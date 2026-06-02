import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;

/// СВЯЗЬ СЕЛЕКТОРА, ЧЕНЭЛА, БАЙТБУФЕРА И НЕБЛОКИРУЮЩЕГО РЕЖИМА
/// 1. Selector просыпается и говорит: "Эй, в serverChannel кто-то стучится".
/// 2. Программа вызывает SocketChannel (дверь для нового клиента).
/// 3. Этот новый канал тут же переводится в неблокирующий режим и регистрируется в Selector'e с пометкой "следи, когда он будет читать".
/// 4. Через некоторое время Selector говорит: "Клиент в канале 3 прислал данные".
/// 5. Программа берёт этот SocketChannel, создаёт ByteBuffer и читает данные кусочком.
/// 6. Так как канал неблокирующий, метод read() забирает только то, что есть, и сразу возвращает управление, даже если клиент прислал только половину команды.
/// 7. Остатки команды дожидаются в session.inputBuffer (обычном StringBuilder) до следующего раза.
///
/// Весь сервер живёт в одном цикле, не создавая потоков.
/// Selector говорит, кого обслуживать, Channels предоставляют двери, а Buffer - это временное хранилище для кусочков данных.
/// Благодаря неблокирующему режиму программа никогда не зависает в ожидании одного клиента.

/// Отличие блокирующего от неблокирующего метода было бы в том, что если бы потоки блокировались, то для каждого клиента нужен был бы отдельный поток while(),
/// а в неблокирующем режиме создаётся один главный поток while() на всех клиентов сразу. Это способствует быстрой работе сервера без зависаний.

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

        /// КАНАЛЫ (CHANNEL)

        /// Без метода .configureBlocking(false): ты подходишь к двери и смотришь в глазок.
        ///Если сотрудник занят, ты стоишь и ждёшь, пока он освободится, и не идёшь к другим. Это блокирующий режим.

        /// С методом .configureBlocking(false): ты дёргаешь ручку, видишь "занято" и сразу идёшь к следующему сотруднику. Это неблокирующий режим.

        /// Главный серверный канал настроен как неблокирующий, чтобы accept() (принятие нового клиента) не заставлял программу висеть.
        /// Каналы клиентов тоже неблокирующие, чтобы метод read() (чтение данных) не ждал, пока клиент соизволит что-то напечатать, а сразу возвращал управление.

        selector = Selector.open();
        /// OP_ACCEPT - константа в классе SelectionKey, обозначающая операцию принятия входящего соединения.
        /// Регистрируем двери, за которыми нужно следить
        serverChannel.register(selector, SelectionKey.OP_ACCEPT); //для сообщений о подключениях
        /// Следи, когда кто-то постучится в главную дверь (то-бишь, будет новое подключение).

        /// SELECTOR

        /// Селектор - это твой планшет с уведами.
        /// Ты же не можешь бесконечно бегать по кругу и дёргать все двери подряд? Это неэффективно.
        /// Selector - это как умный планшет, который сам тебе пишет: "Сотрудник в кабинете 5 открыл дверь" или "Клиент в приёмной ждёт".
        /// В селектор нельзя зарегистрировать любой канал, поэтому для прослушивания сетевых соединений можно использовать метод select, а для чтения данных процесса - нет.

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

            selector.select(50); ///"Планшет, есть ли новости за последние 50 мс?"

            /// Получаем список уведомлений
            Set<SelectionKey> keys = selector.selectedKeys(); //один ключ = одно произошедшее событие
            Iterator<SelectionKey> iter = keys.iterator();

            /// ГЛАВНЫЙ ЦИКЛ ПРОГРАММЫ
            while (iter.hasNext()) { //итерация по событиям
                SelectionKey key = iter.next();
                iter.remove();

                /// Смотрим каждое уведомление по-очереди
                if (key.isAcceptable()) { /// В главную дверь стучатся
                    acceptConnection(); //новое подключение
                }

                if (key.isReadable()) { /// Этот сотрудник что-то пишет
                    readData(key); //чтение новых данных
                }
            }
            /// Так программа узнает, что делать дальше, не простаивая без дела.
        }
    }

    /// Метод вызывается при каждой итерации в главном цикле в методе start()
    //проверка активных процессов
    private void checkActiveProcesses(long currentTime) throws IOException{
        /// получаем итератор для перебора всех активных сессий (подключённых клиентов)
        /// используется итератор, а не цикл for, тк в процессе перебора мы можем удалять элементы
        /// entrySet() возвращает набор всех пар. С помощью iterator() мы перебираем последовательно
        Iterator<Map.Entry<SocketChannel, ClientSession>> iter = sessions.entrySet().iterator(); //для обхода сессий
        /// В цикле мы можем удалять клиентов из sessions (при таймауте или завершении процесса).
        /// Если использовать for/foreach и удалять внутри, то будет ConcurrentModificationException.
        /// Итератор позволяет безопасно удалить текущий элемент.
        /// Iterator - интерфейс, с помощью которого можно перебрать элементы любой коллекции.

        /// пока есть непроверенные сессии
        while (iter.hasNext()) { /// hasNext() - проверяет есть ли следующий элемент.
        /// Map - это интерфейс, представляющий структуру данных для хранения пар "ключ-значение".
        /// Это не коллекция, тк хранит не отдельные элементы, а связи между ключами и значениями.
        /// Серверу нужно для каждого подключённого клиента хранить:
        /// 1) накопленный ввод (части команд)
        /// 2) запущенный процесс (если есть)
        /// 3) время запуска последней команды
        /// Когда происходит событие от Selector, мы получаем SocketChannel, но не знаем, какой процесс у этого клиента.
        /// Map решает эту проблему.
        /// Map.Entry - это внутренний интерфейс Map, представляющий одну конкретную пару ключ-значение.
        /// Каждый элемент Map внутри себя является объектом Entry.
            Map.Entry<SocketChannel, ClientSession> entry = iter.next(); //пара <клиент,сессия>
            /// entry - пара ключ-значение из мап
            /// clientChannel - канал для связи с клиентом (нужен для отправки ответов)
            /// session - объект с состоянием клиента (буфер ввода, текущий процесс, время старта)
            SocketChannel clientChannel = entry.getKey();
            ClientSession session = entry.getValue();

            /// Для каждой сессии с активным процессом
            if (session.currentProcess != null) { //проверка на активный процесс
                Process process = session.currentProcess;
                /// session.currentProcess хранит ссылку на объект Process, если для данного клиента сейчас выполняется какая-то команда.
                /// Если процесс завершён или комвнды не было - здесь null.

                /// Проверка таймаута
                /// session.commandStartTime хранит время в миллисекундах, когда была запущена текущая команда (нужно для отслеживания таймаута).
                if (currentTime - session.commandStartTime > TIMEOUT_SECONDS * 1000) { //проверка на таймаут
                    System.out.println("Произошёл Timeout");
                    process.destroy(); //принудительное прерывание процесса
                    try {
                        sendToClient(clientChannel, "\nTimeout\n");
                        sendToClient(clientChannel, "input> ");
                    } catch (IOException e) {
                        //игнор
                    }
                    session.currentProcess = null; /// обнуляем ссылку на процесс в сессии
                    continue; ///переходим к следующему клиенту
                }

                //завершился ли процесс (проверка неблок)
                try {
                    int exitCode = process.exitValue(); //исключение, если процесс ещё жив
                    /// exitValue() - неблок проверка статуса процесса
                    System.out.println("Процесс прерван: " + exitCode);

                    /// читаем всё, что процесс записал в stdout и stderr, и отправляет клиенту
                    readAndSendProcessOutput(clientChannel, process); //вывод из stdout и stderr

                    try {
                        sendToClient(clientChannel, "input> "); ///отправка нового приглашения
                    } catch (IOException e) {
                        //игнор
                    }
                    session.currentProcess = null; ///обнуляем ссылку на процесс в сессии

                } catch (IllegalThreadStateException e) {
                    try {
                        //если процесс ещё выполняется, то данные не блокируются
                        readAvailableProcessOutput(clientChannel, process); ///проверка, есть ли доступные данные в процессе (через available())
                    } catch (IOException ex) {
                        //игнор
                    }
                }
                /// Ошибки в catch игнорируются, тк сессия клиента всё равно будет скоро удалена, а ошибка не критична.
            }
        }
    }

    /// Метод вызывается после того, как процесс завершился (нормально или принудительно)
    /// Его задача прочитать весь вывод, который накопился в потоках процесса, и отправить его клиенту.
    //отправка клиенту всех данных из stderr и strout после завершения процесса
    private void readAndSendProcessOutput(SocketChannel clientChannel, Process process) throws IOException {
        //вывод stdout
        String stdout = readAllAvailableData(process.getInputStream()); ///читаем всё, что есть в stdout
        /// process.getInputStream() - получаем данные потока stdout
        /// readAllAvailableData() - читаем из потока все данные (если данных нет, то возвращается пустая строка).
        if (!stdout.isEmpty()) {
            sendToClient(clientChannel, stdout); ///отправляем клиенту вывод данных
        }

        //вывод ошибок stderr
        String stderr = readAllAvailableData(process.getErrorStream()); //////читаем всё, что есть в stderr
        /// process.getErrorStream() - получаем данные потока stderr
        /// readAllAvailableData - читаем доступные данные
        if (!stderr.isEmpty()) {
            sendToClient(clientChannel, stderr); /// отправляем клиенту вывод ошибок
        }

        /// если не проверять потоки на пустоту, то клиент получал бы пустые строки
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

        /// читаем, пока есть данные
        while (inputStream.available() > 0) { //пока есть данные
            bytesRead = inputStream.read(buffer); ///сколько байт было прочитано
            /// read() не блокируется в ожидании данных, тк они точно есть (мы проверили это с помощью available()).
            if (bytesRead > 0) {
                result.append(new String(buffer, 0, bytesRead, ASCII)); ///добавляем в SringBuilder
                /// нельзя написать просто new String(buffer), тк создалась бы строка на 4096 символов, невзависимости от того, сколько реально было прочитано.
                /// это чревато всяким "мусором" на оставшихся символах
            }
        }

        return result.toString(); /// возвращем всё, что накопили, как одну строку.
    }

    //чтение данных после завершения процесса
    private String readAvailableData(InputStream inputStream) throws IOException {
        if (inputStream == null) return "";

        /// Сколько байт можно прочитать без блокировки
        int available = inputStream.available(); //сколько есть байт
        if (available <= 0) return "";

        /// AVAILABLE() - возвращает кол-во байт, которые можно прочитать без блокировки.
        /// 1. Это оценка, а не точное суждение.
        /// Возвращаемое число может не учитывать данные, которые уже сгенерированы процессом, но ещё не попали в буфер ОС.
        /// 2. Может вернуть 0, хотя данные появятся через микросекунду.
        /// Поэтому в цикле проверки мы вызываем available() многократно.

        byte[] buffer = new byte[Math.min(available, 4096)]; ///массив байтов для чтения данных
        /// используем Math.min, чтобы не создавать огромный буфер
        /// Этот read() не заблокируется, тк данные точно есть
        int bytesRead = inputStream.read(buffer);
        /// Обычный read() на InputStream блокируется до тех пор, пока не появятся данные.
        /// Если бы мы вызвали process.getInputStream().read() без проверки, поток завис бы в ожидании вывода процесса
        /// и не смог бы обслуживать других клиентов.
        /// available() позволяет узнать, есть ли данные прямо сейчас, и читать только если они есть.
        if (bytesRead > 0) {
            return new String(buffer, 0, bytesRead, ASCII);
        }

        return "";
    }

    /// вызывается при новом подключении клиента
    //новое подключение
    private void acceptConnection() throws IOException {
        /// 1. Принимаем соединение(неблокирующее)(????)
        SocketChannel clientChannel = serverChannel.accept();
        /// 2. Устанавливаем неблокирующий режим
        clientChannel.configureBlocking(false); //неблокирующий режим

        /// 3. Регистрируем канал в селекторе для чтения
        /// Когда клиент уже зашёл, регистрируем его канал для чтения
        clientChannel.register(selector, SelectionKey.OP_READ); //регистр для чтения
        /// Следи, когда этот конкретный сотрудник начнёт говорить
        /// если не зарегистрировать канал в селекторе, то данные, которые он будет отправлять никогда не будут прочитаны.


        /// 4. Создаём сессию для хранения состояния
        sessions.put(clientChannel, new ClientSession());

        SocketAddress addr = clientChannel.getRemoteAddress(); /// возвращаем адрес и порт клиента
        System.out.println("Подключение нового клиента: " + getClientIP(addr)); /// форматируемый адрес в читаемый вид

        /// 5. Отправляем приглашение
        sendToClient(clientChannel, "input> ");
    }

    /// метод преобразует SocketAdress в человека-читаемую строчку кода: "IP:порт" (IPv6)
    //айпи клиента
    private String getClientIP(SocketAddress address) {
        /// InstanceOf — бинарный оператор в Java, который проверяет, принадлежит ли объект конкретному классу, его подклассу или интерфейсу.
        if (address instanceof InetSocketAddress) {
            InetSocketAddress inetAddr = (InetSocketAddress) address;
            String ip = inetAddr.getAddress().getHostAddress();
            /// inetAddr.getAddress() - возвращает объект InetAddress (содержит айпи адрес)
            /// getHostAddress() - возвращает строковое представление айпи-адреса
            int port = inetAddr.getPort();///получение порта клиента
            if (ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) {
                return "127.0.0.1:" + port;
                /// ::1 - сокращённая запись IPv6 localhost
                /// 0:0:0:0:0:0:0:1 - полная запись того же адреса
                /// 127.0.0.1 - оба обозначают "локальная машина" (аналог 127.0.0.1 в IPv4)
                /// последний вариант лучше для читаемости логов, тк джава может автоматически использовать IPv6
            }
            return ip + ":" + port; ///для остальных адрес
        }
        return address.toString(); ///если адрес не является сетевым, то возврщае строковое представление
    }

    /// вызывается, когда селектор сообщает, что у клиента есть данные для чтения.Задачи:
    /// 1) прочитать данные от клиента
    /// 2) обработать откл клиента
    /// 3) накопить данные в буфере сессии
    /// 4) когда накопится команда - извлечь и выполнить
    //чтение данных от клиента
    private void readData(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientSession session = sessions.get(clientChannel);

        if (session == null) return; /// если сессии нет, то ничего не делаем

        /// BYTEBUFFER
        /// Когда ты заходишь в кабинет к сотруднику (канал почти готов для чтения), тебе нужно записать, что он говорит.
        /// Но говорить он может медленно и с перерывами.
        /// Ты не будешь ждать, пока он закончит сразу, а запишешь то, что успел сказать сейчас, в блокнот, и пойдёшь по другим делам.
        /// ByteBuffer - это и есть такой блокнот.

        /// Выделяем память для чтения (берём 1024 символа)
        ByteBuffer buffer = ByteBuffer.allocate(1024); //для чтения
        /// allocate() - создаёт буфер
        /// Читаем те данные, которые уже есть
        int bytesRead = clientChannel.read(buffer); /// Записываем в блокнот всё, что сотрудник сказал на данный момент.

        if (bytesRead == -1) { //если клиент отключился
            safeDisconnectClient(clientChannel);
            return;
        }

        if (bytesRead > 0) { //если есть данные
            /// Представь, что ты писал ручкой с одной стороны листа, а теперь хочешь прочитать написанное.
            /// flip() переворачивает блокнот в режим чтения.
            buffer.flip(); //из режима записи в режим чтения
            /// Читаем, что же там написано.
            String input = ASCII.decode(buffer).toString(); /// Декодируем байты в строку
            session.inputBuffer.append(input); /// Добавляем в буфер сессии (мб неполная строка)

            //проверка на разрыв строки после команды
            String fullInput = session.inputBuffer.toString();
            if (fullInput.contains("\n")) {
                int idx = fullInput.indexOf("\n");
                String command = fullInput.substring(0, idx).trim();

                session.inputBuffer = new StringBuilder( //проверка есть ли что-то после разрыва
                        idx + 1 < fullInput.length() ? fullInput.substring(idx + 1) : ""
                        /// если после перевода строки есть символы, то создаём новый буфер, тк это мб новая команда. Иначе ничего не создаём
                );

                processCommand(clientChannel, session, command); ///передаём информацию для запуска процесс
            }
        }
    }

    /// вызывается из readData, когда от клиента получена новая команда
    //обработка команды клиента
    private void processCommand(SocketChannel clientChannel, ClientSession session, String command) {
        if (command.equalsIgnoreCase("exit")) { /// обрабатываем команду выхода с игнором регистра
            safeDisconnectClient(clientChannel);
            return;
        }

        if (command.trim().isEmpty()) { /// если пустая команда
            try {
                sendToClient(clientChannel, "input> "); ///отправляем приглашение
            } catch (IOException e) {
                safeDisconnectClient(clientChannel); ///если отправить не вышло, то дисконектим клиента
            }
            return;
        }

        System.out.println("Команда в процессе: " + command);

        try {
            List<String> commandParts = parseCommandWin(command);
            /// Команда передаётся списком, а не строкой, потому что это важно для безопасности и правильной обработки аргументов.
            /// Конструктор ProcessBuilder(List<String> command)) принимает команду и аргументы по отдельности:
            /// 1) если передать строку "ls -la", PB попытается найти программу с командой "ls -la" (с пробелом), что неверно
            /// 2) правильно: первый элемент - исполняемый файл (ls), остальные - аргументы (-la).

            ProcessBuilder pb = new ProcessBuilder(commandParts); ///PB с командой будет использоваться для запуска процесса
            /// С помощью PB можно создавать и запускать процессы

            Process process = pb.start(); ///тут вернётся объект Process, через который можно управлять процессом

            session.currentProcess = process; ///сохраняем объект процесса в сессии
            session.commandStartTime = System.currentTimeMillis(); ///запоминаем время запуска для таймаута

            readAvailableProcessOutput(clientChannel, process);//чтение нач вывода
            /// сразу проверяем нет ли уже доступных данных и отправляем их клиенту

        } catch (IOException e) { /// перехват ошибок ввода-вывода
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

    /// вызвается при след случаях:
    /// 1) клиент использовал команду exit
    /// 2) клиент закрыл соединение
    /// 3) произошла ошибка при отправке данных клиенту
    //отключение клиента, очистка ресурсов и завершение процесса
    private void safeDisconnectClient(SocketChannel clientChannel) {
        ClientSession session = sessions.get(clientChannel); ///получаем объект состояния клиента из HashMap
        String clientInfo = "неизвестный"; ///значение по умолчанию, если не удастся получить айпи

        try {
            clientInfo = getClientIP(clientChannel.getRemoteAddress()); ///получение красивого айпи адреса
        } catch (Exception e) {
            //clientInfo = "неизвестный"
        }

        if (session != null && session.currentProcess != null) {
            session.currentProcess.destroy(); //прерывание активного процесса
        }

        sessions.remove(clientChannel); ///удаляем сессию клиента

        //закрытие канала
        try {
            if (clientChannel.isOpen()) { /// если канал открыт, то закрываем
                clientChannel.close();
            }
        } catch (IOException e) {
            //игнор
        }

        System.out.println("Клиент отключился: " + clientInfo);
    }

    /// метод подготавливает команду для запуска через ProcessBuilder с учётом особенностей ОС
    /// на виндовс команды выполняются через командный интерпретатор cmd.exe
    //парсит строку команды на части для ProcessBuilder
    private List<String> parseCommandWin(String command) { /// возвращает список строк (команда и аргументы)
        List<String> parts = new ArrayList<>(); ///для складывания команд

        if (System.getProperty("os.name").toLowerCase().contains("win")) { /// получаем имя ос, приводим к нижнему регистру, проверяем содержит ли "win"
            parts.add("cmd.exe"); ///командный интерпретатор
            parts.add("/c");/// параметр - "выполнить и завершить"
            parts.add(command); // Вся команда передается как один аргумент
        }
        return parts;

        /// На виндовс команды вроде dir не являются исполняемыми файлами - это встроенные команды командного интерпретатора.
        /// Поэтому мы запускаем cmd.exe /c dir, где /c означает "выполнить команду и завершиться".

    }

    /// отправлка сообщения клиенту через его сокет канал
    //отправляет сообщение клиенту
    private void sendToClient(SocketChannel clientChannel, String message) throws IOException {
        if (clientChannel.isConnected() && clientChannel.isOpen()) { //подключён ли клиент и открыт ли канал
            /// отправляем данные только если условие выполняется
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