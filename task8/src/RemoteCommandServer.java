import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;

public class RemoteCommandServer {
    private static final int PORT = 1111;
    private static final int TIMEOUT_SECONDS = 10;
    private static final int CLIENT_COUNT_INTERVAL = 5;
    private static final Charset ASCII = StandardCharsets.US_ASCII;

    private ServerSocketChannel serverChannel; //канал для принятия подключений
    private Selector selector; //селектор для обработки соединений в одном потоке
    private Map<SocketChannel, ClientSession> sessions = new HashMap<>(); //сессии подключённых клиентов
    private long lastClientCountOutput = 0; //время последнего вывода количества клиентов

    //создание сервера, настройка селектора и начало работы
    public void start() throws IOException {
        //создание и настройка
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(PORT)); //привязываем к порту

        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT); //для сообщений о подключениях

        System.out.println("Сервер запущен по порту " + PORT);

        //начало работы
        while (true) {
            long currentTime = System.currentTimeMillis();

            checkActiveProcesses(currentTime);

            //вывод кол-ва клиентов раз в пять сек
            if (currentTime - lastClientCountOutput > CLIENT_COUNT_INTERVAL * 1000) {
                LocalTime now = LocalTime.now();
                String time = String.format("[%02d:%02d:%02d]",
                        now.getHour(), now.getMinute(), now.getSecond());
                System.out.println(time + "Количество подключённых клиентов: " + sessions.size());
                lastClientCountOutput = currentTime;
            }

            selector.select(50);

            Set<SelectionKey> keys = selector.selectedKeys(); //один ключ = одно произошедшее событие
            Iterator<SelectionKey> iter = keys.iterator();

            while (iter.hasNext()) { //итерация по событиям
                SelectionKey key = iter.next();
                iter.remove();

                if (key.isAcceptable()) {
                    acceptConnection(); //новое подключение
                }

                if (key.isReadable()) {
                    readData(key); //чтение новых данных
                }
            }
        }
    }

    //проверка активных процессов
    private void checkActiveProcesses(long currentTime) throws IOException{
        Iterator<Map.Entry<SocketChannel, ClientSession>> iter = sessions.entrySet().iterator(); //для обхода сессий

        while (iter.hasNext()) {
            Map.Entry<SocketChannel, ClientSession> entry = iter.next(); //пара <клиент,сессия>
            SocketChannel clientChannel = entry.getKey();
            ClientSession session = entry.getValue();

            if (session.currentProcess != null) { //проверка на активный процесс
                Process process = session.currentProcess;

                if (currentTime - session.commandStartTime > TIMEOUT_SECONDS * 1000) { //проверка на таймаут
                    System.out.println("Произошёл Timeout");
                    process.destroy(); //принудительное прерывание процесса
                    try {
                        sendToClient(clientChannel, "\nTimeout\n");
                        sendToClient(clientChannel, "input> ");
                    } catch (IOException e) {
                        //игнор
                    }
                    session.currentProcess = null;
                    continue;
                }

                //завершился ли процесс (проверка неблок)
                try {
                    int exitCode = process.exitValue(); //исключение, если процесс ещё жив
                    System.out.println("Процесс прерван: " + exitCode);

                    readAndSendProcessOutput(clientChannel, process); //вывод из stdout и stderr

                    try {
                        sendToClient(clientChannel, "input> ");
                    } catch (IOException e) {
                        //игнор
                    }
                    session.currentProcess = null;

                } catch (IllegalThreadStateException e) {
                    try {
                        //если процесс ещё выполняется, то данные не блокируются
                        readAvailableProcessOutput(clientChannel, process);
                    } catch (IOException ex) {
                        //игнор
                    }
                }
            }
        }
    }

    //отправка клиенту всех данных из stderr и strout после завершения процесса
    private void readAndSendProcessOutput(SocketChannel clientChannel, Process process) throws IOException {
        //вывод stdout
        String stdout = readAllAvailableData(process.getInputStream());
        if (!stdout.isEmpty()) {
            sendToClient(clientChannel, stdout);
        }

        //вывод ошибок stderr
        String stderr = readAllAvailableData(process.getErrorStream());
        if (!stderr.isEmpty()) {
            sendToClient(clientChannel, stderr);
        }
    }

    //чтение доступных данных активного процесса
    private void readAvailableProcessOutput(SocketChannel clientChannel, Process process) throws IOException {
        //проверка доступных данных в stdout
        String stdout = readAvailableData(process.getInputStream());
        if (!stdout.isEmpty()) {
            sendToClient(clientChannel, stdout); //данные отправляются клиенту по мере поступления
        }

        //проверка доступных данных в stderr
        String stderr = readAvailableData(process.getErrorStream());
        if (!stderr.isEmpty()) {
            sendToClient(clientChannel, stderr);
        }
    }

    //чтение данных, пока они не закончились
    private String readAllAvailableData(InputStream inputStream) throws IOException {
        if (inputStream == null) return "";

        StringBuilder result = new StringBuilder();
        byte[] buffer = new byte[4096]; //буфер для чтения байтов
        int bytesRead;

        while (inputStream.available() > 0) { //пока есть данные
            bytesRead = inputStream.read(buffer);
            if (bytesRead > 0) {
                result.append(new String(buffer, 0, bytesRead, ASCII));
            }
        }

        return result.toString();
    }

    //чтение данных после завершения процесса
    private String readAvailableData(InputStream inputStream) throws IOException {
        if (inputStream == null) return "";

        int available = inputStream.available(); //сколько есть байт
        if (available <= 0) return "";

        byte[] buffer = new byte[Math.min(available, 4096)];
        int bytesRead = inputStream.read(buffer);
        if (bytesRead > 0) {
            return new String(buffer, 0, bytesRead, ASCII);
        }

        return "";
    }

    //новое подключение
    private void acceptConnection() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false); //неблокирующий режим

        clientChannel.register(selector, SelectionKey.OP_READ); //регистр для чтения

        sessions.put(clientChannel, new ClientSession());

        SocketAddress addr = clientChannel.getRemoteAddress();
        System.out.println("Подключение нового клиента: " + getClientIP(addr));

        sendToClient(clientChannel, "input> ");
    }

    //айпи клиента
    private String getClientIP(SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            InetSocketAddress inetAddr = (InetSocketAddress) address;
            String ip = inetAddr.getAddress().getHostAddress();
            int port = inetAddr.getPort();
            if (ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) {
                return "127.0.0.1:" + port;
            }
            return ip + ":" + port;
        }
        return address.toString();
    }

    //чтение данных от клиента
    private void readData(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientSession session = sessions.get(clientChannel);

        if (session == null) return;

        ByteBuffer buffer = ByteBuffer.allocate(1024); //для чтения
        int bytesRead = clientChannel.read(buffer);

        if (bytesRead == -1) { //если клиент отключился
            safeDisconnectClient(clientChannel);
            return;
        }

        if (bytesRead > 0) { //если есть данные
            buffer.flip(); //из режима записи в режим чтения
            String input = ASCII.decode(buffer).toString();
            session.inputBuffer.append(input);

            //проверка на разрыв строки после команды
            String fullInput = session.inputBuffer.toString();
            if (fullInput.contains("\n")) {
                int idx = fullInput.indexOf("\n");
                String command = fullInput.substring(0, idx).trim();

                session.inputBuffer = new StringBuilder( //проверка есть ли что-то после разрыва
                        idx + 1 < fullInput.length() ? fullInput.substring(idx + 1) : ""
                );

                processCommand(clientChannel, session, command);
            }
        }
    }

    //обработка команды клиента
    private void processCommand(SocketChannel clientChannel, ClientSession session, String command) {
        if (command.equalsIgnoreCase("exit")) {
            safeDisconnectClient(clientChannel);
            return;
        }

        if (command.trim().isEmpty()) {
            try {
                sendToClient(clientChannel, "input> ");
            } catch (IOException e) {
                safeDisconnectClient(clientChannel);
            }
            return;
        }

        System.out.println("Команда в процессе: " + command);

        try {
            List<String> commandParts = parseCommandWin(command);

            ProcessBuilder pb = new ProcessBuilder(commandParts);

            Process process = pb.start();

            session.currentProcess = process;
            session.commandStartTime = System.currentTimeMillis();
            readAvailableProcessOutput(clientChannel, process);//чтение нач вывода

        } catch (IOException e) {
            try {
                sendToClient(clientChannel, "Ошибка: " + e.getMessage() + "\ninput> ");
            } catch (IOException ex) {
                safeDisconnectClient(clientChannel);
            }
        } catch (Exception e) {
            try {
                sendToClient(clientChannel, "Недопустимая команда: " + e.getMessage() + "\ninput> ");
            } catch (IOException ex) {
                safeDisconnectClient(clientChannel);
            }
        }
    }

    //отключение клиента, очистка ресурсов и завершение процесса
    private void safeDisconnectClient(SocketChannel clientChannel) {
        ClientSession session = sessions.get(clientChannel);
        String clientInfo = "неизвестный";

        try {
            clientInfo = getClientIP(clientChannel.getRemoteAddress());
        } catch (Exception e) {
            //clientInfo = "неизвестный"
        }

        if (session != null && session.currentProcess != null) {
            session.currentProcess.destroy(); //прерывание активного процесса
        }

        sessions.remove(clientChannel);

        //закрытие канала
        try {
            if (clientChannel.isOpen()) {
                clientChannel.close();
            }
        } catch (IOException e) {
            //игнор
        }

        System.out.println("Клиент отключился: " + clientInfo);
    }

    //парсит строку команды на части для ProcessBuilder
    private List<String> parseCommandWin(String command) {
        List<String> parts = new ArrayList<>();

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            parts.add("cmd.exe");
            parts.add("/c");
            parts.add(command); // Вся команда передается как один аргумент
        }
        return parts;
    }

    //отправляет сообщение клиенту
    private void sendToClient(SocketChannel clientChannel, String message) throws IOException {
        if (clientChannel.isConnected() && clientChannel.isOpen()) { //подключён ли клиент и открыт ли канал
            ByteBuffer buffer = ASCII.encode(message);
            clientChannel.write(buffer);//отправка из буфера в сокет
        }
    }

    private static class ClientSession {
        StringBuilder inputBuffer = new StringBuilder(); //буфер для накопления ввода
        Process currentProcess = null; //текущий выполняемый процесс
        long commandStartTime = 0; //время запуска последней команды
    }
}