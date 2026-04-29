package org.example;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class ProcessServer {
    private static final int PORT = 1111;
    private static final int TIMEOUT_SECONDS = 10;
    private static final int STATUS_INTERVAL_MS = 5000;
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private long lastStatusTime = 0;

    // Для хранения состояния клиентов
    static class ClientState {
        SocketChannel channel;
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        StringBuilder input = new StringBuilder();
        Process process;
        InputStream processOutput;
        boolean closing = false;
        long processStartTime = 0;

        ClientState(SocketChannel channel) {
            this.channel = channel;
        }
    }

    private Map<SocketChannel, ClientState> clients = new HashMap<>();
    private Map<Process, ClientState> processClients = new HashMap<>();

    public static void main(String[] args) throws IOException {
        new ProcessServer().start();
    }

    public void start() throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(PORT));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server started on port " + PORT);

        while (true) {
            selector.select(100); // Проверяем каждые 100мс для активного ожидания

            // Обработка сетевых событий
            for (SelectionKey key : selector.selectedKeys()) {
                if (!key.isValid()) continue;

                if (key.isAcceptable()) {
                    acceptConnection();
                }
                if (key.isReadable()) {
                    readFromClient(key);
                }
            }
            selector.selectedKeys().clear();

            // Проверка завершения процессов
            checkProcesses();

            // Проверка таймаутов процессов
            checkTimeouts();

            // Вывод статистики каждые 5 секунд
            printStatus();
        }
    }

    private void acceptConnection() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);

            ClientState state = new ClientState(clientChannel);
            clients.put(clientChannel, state);

            sendPrompt(clientChannel);
            System.out.println("New client connected: " + clientChannel.getRemoteAddress());
        }
    }

    private void readFromClient(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientState state = clients.get(channel);

        if (state == null) return;

        state.buffer.clear();
        int bytesRead = channel.read(state.buffer);

        if (bytesRead == -1) {
            // Клиент отключился
            disconnectClient(channel);
            return;
        }

        if (bytesRead > 0) {
            state.buffer.flip();
            String received = CHARSET.decode(state.buffer).toString();
            state.input.append(received);

            // Проверяем, есть ли полная команда (заканчивается \n)
            processInput(state);
        }
    }

    private void processInput(ClientState state) throws IOException {
        String inputStr = state.input.toString();
        int newlineIndex = inputStr.indexOf('\n');

        if (newlineIndex != -1) {
            String command = inputStr.substring(0, newlineIndex).trim();
            state.input.delete(0, newlineIndex + 1);

            if (command.equalsIgnoreCase("exit")) {
                disconnectClient(state.channel);
                return;
            }

            // Если уже выполняется процесс, игнорируем новую команду
            if (state.process != null) {
                sendToClient(state.channel, "Another process is still running\n");
                sendPrompt(state.channel);
                return;
            }

            // Запуск процесса
            executeCommand(state, command);
        }
    }

    private void executeCommand(ClientState state, String command) throws IOException {
        try {
            if (command.isEmpty()) {
                sendPrompt(state.channel);
                return;
            }

            // Разбиваем команду на аргументы
            List<String> args = parseCommand(command);
            if (args.isEmpty()) {
                sendPrompt(state.channel);
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true); // Объединяем stderr и stdout
            state.process = pb.start();
            state.processStartTime = System.currentTimeMillis();
            state.processOutput = state.process.getInputStream();

            processClients.put(state.process, state);

        } catch (IOException e) {
            sendToClient(state.channel, "Error starting process: " + e.getMessage() + "\n");
            sendPrompt(state.channel);
        }
    }

    private List<String> parseCommand(String command) {
        List<String> args = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentArg = new StringBuilder();

        for (char c : command.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (currentArg.length() > 0) {
                    args.add(currentArg.toString());
                    currentArg = new StringBuilder();
                }
            } else {
                currentArg.append(c);
            }
        }

        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }

        return args;
    }

    private void checkProcesses() throws IOException {
        Iterator<Map.Entry<Process, ClientState>> it = processClients.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Process, ClientState> entry = it.next();
            Process process = entry.getKey();
            ClientState state = entry.getValue();

            if (!process.isAlive()) {
                // Процесс завершился
                try {
                    int exitCode = process.waitFor();
                    String output = readAvailableOutput(state.processOutput);

                    if (!output.isEmpty()) {
                        sendToClient(state.channel, output);
                    }

                    sendToClient(state.channel, "\nProcess exited with code: " + exitCode + "\n");

                } catch (InterruptedException | IOException e) {
                    sendToClient(state.channel, "Error getting process result: " + e.getMessage() + "\n");
                }

                cleanupProcess(state);
                it.remove();
                sendPrompt(state.channel);
            } else {
                // Процесс ещё выполняется, проверяем вывод
                try {
                    String output = readAvailableOutput(state.processOutput);
                    if (!output.isEmpty()) {
                        sendToClient(state.channel, output);
                    }
                } catch (IOException e) {
                    // Игнорируем ошибки чтения
                }
            }
        }
    }

    private void checkTimeouts() throws IOException {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<Process, ClientState>> it = processClients.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Process, ClientState> entry = it.next();
            Process process = entry.getKey();
            ClientState state = entry.getValue();

            if (process.isAlive() &&
                    (currentTime - state.processStartTime) > TIMEOUT_SECONDS * 1000) {

                // Таймаут - убиваем процесс
                process.destroy();
                try {
                    process.waitFor(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // Игнорируем
                }

                if (process.isAlive()) {
                    process.destroyForcibly();
                }

                sendToClient(state.channel, "\nTimeout: process terminated after " + TIMEOUT_SECONDS + " seconds\n");
                cleanupProcess(state);
                it.remove();
                sendPrompt(state.channel);
            }
        }
    }

    private String readAvailableOutput(InputStream stream) throws IOException {
        if (stream.available() <= 0) {
            return "";
        }

        byte[] buffer = new byte[Math.min(stream.available(), 4096)];
        int bytesRead = stream.read(buffer);
        if (bytesRead > 0) {
            return new String(buffer, 0, bytesRead, CHARSET);
        }
        return "";
    }

    private void cleanupProcess(ClientState state) {
        if (state.process != null) {
            try {
                state.processOutput.close();
            } catch (IOException e) {
                // Игнорируем
            }
            state.process = null;
            state.processOutput = null;
        }
    }

    private void sendPrompt(SocketChannel channel) throws IOException {
        sendToClient(channel, "\ninput> ");
    }

    private void sendToClient(SocketChannel channel, String message) throws IOException {
        ByteBuffer buffer = CHARSET.encode(message);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private void disconnectClient(SocketChannel channel) throws IOException {
        ClientState state = clients.get(channel);
        if (state != null) {
            if (state.process != null && state.process.isAlive()) {
                state.process.destroy();
                cleanupProcess(state);
            }

            processClients.values().removeIf(clientState -> clientState == state);
            clients.remove(channel);
        }

        if (channel.isOpen()) {
            channel.close();
        }

        System.out.println("Client disconnected: " + channel.getRemoteAddress());
    }

    private void printStatus() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStatusTime >= STATUS_INTERVAL_MS) {
            System.out.println("[" + LocalDateTime.now() + "] Connected clients: " + clients.size() +
                    ", Active processes: " + processClients.size());
            lastStatusTime = currentTime;
        }
    }
}