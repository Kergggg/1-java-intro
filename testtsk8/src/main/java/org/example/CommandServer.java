package org.example;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;

public class CommandServer {
    private static final int PORT = 1111;
    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT_MS = 10000; // 10 секунд
    private static final Charset charset = Charset.forName("UTF-8");
    private static final CharsetDecoder decoder = charset.newDecoder();
    private static final CharsetEncoder encoder = charset.newEncoder();


    // Класс для хранения информации о клиенте
    private static class ClientInfo {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        StringBuilder input = new StringBuilder();
        Process process = null;
        long processStartTime = 0;
        StringBuilder outputBuffer = new StringBuilder();
        boolean waitingForPrompt = false;

        // Проверяем, не истекло ли время выполнения процесса
        boolean isProcessTimeout() {
            return process != null && process.isAlive() &&
                    (System.currentTimeMillis() - processStartTime) > TIMEOUT_MS;
        }
    }

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Map<SocketChannel, ClientInfo> clients = Collections.synchronizedMap(new HashMap<>());
    private boolean running = true;

    public void start() {
        try {
            initServer();
            startClientCounter();
            runServerLoop();
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    // Инициализация сервера
    private void initServer() throws IOException {
        selector = Selector.open();

        // Создаем и настраиваем серверный канал
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(PORT));

        // Регистрируем канал для приема подключений
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Command Server started on port " + PORT);
        System.out.println("Waiting for connections...");
        System.out.println("Note: Use 'exit' command to disconnect");
    }

    // Основной цикл сервера
    private void runServerLoop() throws IOException {
        while (running) {
            // Ждем события (максимум 100мс для проверки процессов)
            selector.select(100);

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();

            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();

                try {
                    if (key.isValid()) {
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        }
                    }
                } catch (CancelledKeyException e) {
                    // Ключ был отменен, игнорируем
                } catch (IOException e) {
                    // Ошибка ввода/вывода - отключаем клиента
                    SocketChannel channel = (SocketChannel) key.channel();
                    disconnectClient(channel);
                }
            }

            // Проверяем все активные процессы
            checkAllProcesses();
        }
    }

    // Обработка нового подключения
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();

        if (client != null) {
            client.configureBlocking(false);
            SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ);

            ClientInfo info = new ClientInfo();
            clientKey.attach(info);
            clients.put(client, info);

            System.out.println("New client connected from: " +
                    client.socket().getInetAddress());

            // Отправляем приглашение для ввода
            sendPrompt(client);
        }
    }

    // Чтение данных от клиента
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientInfo info = (ClientInfo) key.attachment();

        if (info == null) {
            disconnectClient(channel);
            return;
        }

        ByteBuffer buffer = info.buffer;
        buffer.clear();

        int bytesRead;
        try {
            bytesRead = channel.read(buffer);
        } catch (IOException e) {
            // Клиент отключился
            disconnectClient(channel);
            return;
        }

        if (bytesRead == -1) {
            // Клиент закрыл соединение
            disconnectClient(channel);
            return;
        }

        // Преобразуем полученные данные в строку
        buffer.flip();
        String received;
        try {
            received = decoder.decode(buffer).toString();
        } catch (CharacterCodingException e) {
            received = "";
        }

        info.input.append(received);

        // Проверяем, есть ли завершающий символ (новая строка)
        String inputStr = info.input.toString();
        if (inputStr.contains("\n") || inputStr.contains("\r")) {
            // Извлекаем команду (до первого перевода строки)
            int endIndex = Math.max(
                    inputStr.indexOf('\n'),
                    inputStr.indexOf('\r')
            );

            String command = inputStr.substring(0, endIndex).trim();
            info.input = new StringBuilder(inputStr.substring(endIndex + 1));

            // Обрабатываем команду
            processCommand(channel, info, command);
        }
    }

    // Обработка команды от клиента
    private void processCommand(SocketChannel channel, ClientInfo info, String command) throws IOException {
        if (command.isEmpty()) {
            // Пустая команда - просто отправляем новое приглашение
            sendPrompt(channel);
            return;
        }

        if (command.equalsIgnoreCase("exit")) {
            disconnectClient(channel);
            return;
        }

        // Запускаем процесс
        executeCommand(channel, info, command);
    }

    // Запуск системной команды
    private void executeCommand(SocketChannel channel, ClientInfo info, String command) {
        try {
            // Готовим команду для выполнения
            String[] cmdArray;

            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // Windows: используем cmd.exe
                cmdArray = new String[] {"cmd.exe", "/c", command};
            } else {
                // Linux/Mac: разбиваем на части
                // Простой сплит, но в реальности нужно учитывать кавычки и пробелы
                cmdArray = command.split("\\s+");
            }

            ProcessBuilder pb = new ProcessBuilder(cmdArray);

            // Перенаправляем ошибки в стандартный вывод
            pb.redirectErrorStream(true);

            // Запускаем процесс
            Process process = pb.start();

            // Сохраняем информацию о процессе
            info.process = process;
            info.processStartTime = System.currentTimeMillis();
            info.outputBuffer = new StringBuilder();
            info.waitingForPrompt = true;

            System.out.println("Executing command: " + command +
                    " for client: " + channel.socket().getInetAddress());

        } catch (IOException e) {
            // Ошибка запуска процесса
            try {
                sendToClient(channel, "Error: Cannot execute command: " + e.getMessage() + "\r\n");
                sendPrompt(channel);
            } catch (IOException ioException) {
                disconnectClient(channel);
            }
        }
    }

    // Проверка всех активных процессов
    private void checkAllProcesses() {
        List<SocketChannel> toDisconnect = new ArrayList<>();

        for (Map.Entry<SocketChannel, ClientInfo> entry : clients.entrySet()) {
            SocketChannel channel = entry.getKey();
            ClientInfo info = entry.getValue();

            try {
                if (info.process != null) {
                    // Проверяем таймаут
                    if (info.isProcessTimeout()) {
                        info.process.destroyForcibly();
                        sendToClient(channel, "\r\nTimeout: Process exceeded 10 seconds limit\r\n");
                        sendPrompt(channel);
                        info.process = null;
                        info.waitingForPrompt = false;
                        continue;
                    }

                    // Пытаемся прочитать вывод процесса
                    checkProcessOutput(channel, info);

                    // Проверяем, завершился ли процесс
                    if (!info.process.isAlive()) {
                        // Процесс завершился, читаем оставшиеся данные
                        readRemainingProcessOutput(channel, info);

                        // Отправляем промпт
                        sendPrompt(channel);
                        info.process = null;
                        info.waitingForPrompt = false;
                    }
                }
            } catch (IOException e) {
                // Ошибка при отправке данных клиенту
                toDisconnect.add(channel);
            }
        }

        // Отключаем клиентов, с которыми возникли проблемы
        for (SocketChannel channel : toDisconnect) {
            disconnectClient(channel);
        }
    }

    // Проверка вывода процесса (неблокирующее чтение)
    private void checkProcessOutput(SocketChannel channel, ClientInfo info) throws IOException {
        if (info.process == null) return;

        InputStream processOutput = info.process.getInputStream();

        // Проверяем, есть ли доступные данные
        int available = processOutput.available();
        if (available > 0) {
            // Читаем доступные данные
            byte[] buffer = new byte[Math.min(available, 4096)];
            int bytesRead = processOutput.read(buffer);

            if (bytesRead > 0) {
                String output = new String(buffer, 0, bytesRead, charset);
                info.outputBuffer.append(output);

                // Отправляем накопленный вывод клиенту
                if (info.outputBuffer.length() > 0) {
                    sendToClient(channel, info.outputBuffer.toString());
                    info.outputBuffer.setLength(0); // Очищаем буфер
                }
            }
        }
    }

    // Чтение оставшегося вывода после завершения процесса
    private void readRemainingProcessOutput(SocketChannel channel, ClientInfo info) throws IOException {
        if (info.process == null) return;

        InputStream processOutput = info.process.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];

        int bytesRead;
        while ((bytesRead = processOutput.read(data)) != -1) {
            buffer.write(data, 0, bytesRead);
        }

        String output = buffer.toString(charset.name());
        if (!output.isEmpty()) {
            sendToClient(channel, output);
        }

        // Также получаем код завершения
        int exitCode = info.process.exitValue();
        if (exitCode != 0) {
            sendToClient(channel, "\r\nProcess exited with code: " + exitCode + "\r\n");
        }
    }

    // Отправка данных клиенту
    private void sendToClient(SocketChannel channel, String message) throws IOException {
        if (channel.isOpen() && channel.isConnected()) {
            ByteBuffer buffer = encoder.encode(CharBuffer.wrap(message));
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }

    // Отправка приглашения для ввода
    private void sendPrompt(SocketChannel channel) throws IOException {
        sendToClient(channel, "\r\ninput> ");
    }

    // Отключение клиента
    private void disconnectClient(SocketChannel channel) {
        try {
            ClientInfo info = clients.get(channel);
            if (info != null && info.process != null && info.process.isAlive()) {
                info.process.destroyForcibly();
            }

            if (channel.isOpen()) {
                SelectionKey key = channel.keyFor(selector);
                if (key != null) {
                    key.cancel();
                }
                channel.close();
            }

            clients.remove(channel);
            System.out.println("Client disconnected. Total clients: " + clients.size());
        } catch (IOException e) {
            // Игнорируем ошибки при закрытии
        }
    }

    // Таймер для вывода количества подключенных клиентов
    private void startClientCounter() {
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("[" + new Date() + "] Connected clients: " + clients.size());
            }
        }, 5000, 5000); // Первый раз через 5 секунд, потом каждые 5 секунд
    }

    // Очистка ресурсов
    private void cleanup() {
        running = false;

        // Закрываем все клиентские соединения
        for (SocketChannel channel : clients.keySet()) {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException e) {
                // Игнорируем
            }
        }
        clients.clear();

        try {
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
        } catch (IOException e) {
            // Игнорируем
        }

        try {
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }
        } catch (IOException e) {
            // Игнорируем
        }

        System.out.println("Server stopped.");
    }
}