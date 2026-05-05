package org.example.db;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/// Сессии хранятся в памяти сервера (HahMap в AccountService) и на диске в файлах Tomcat

public class DatabaseConnection {
    /// УКАЗЫВАЕМ НАСТРОЙКИ БД
    //Порт и название бд
    private static final String URL = "jdbc:postgresql://localhost:5432/fileManager";
    //Имя пользователя (моё)
    private static final String USER = "postgres";
    //Мой пароль
    private static final String PASSWORD = "MyLitlePony1"; // ЗАМЕНИТЕ НА ВАШ ПАРОЛЬ!

    /// Статический блок инициализации
    //Статический блок - это блок кода, который выполняется один раз при первой загрузке класса в память.
    //При первом обращении к классу загружает JDBC драйвер PostgreSQL
    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /// Создаём и возвращаем соединение с бд
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /// Загрузить всех пользователей из БД в Map
    public static Map<String, UserProfileForDB> loadAllUsers() {
        ///Пустая коллекция для результатов
        //Ключ - логин пользователя (String)
        //Значение - объект UserProfileForDB с данными пользователя
        /// Пример:
        //users = {
        //    "john" → UserProfileForDB(login="john", email="john@mail.com", password="12345"),
        //    "jane" → UserProfileForDB(login="jane", email="jane@mail.com", password="67890"),
        //    "bob"  → UserProfileForDB(login="bob", email="bob@mail.com", password="qwerty")
        //}
        Map<String, UserProfileForDB> users = new HashMap<>();
        //Формируем sql запрос
        String sql = "SELECT login, email, password FROM users";
        /// Что вернёт бд?
        //┌─────────┬─────────────────┬────────────┐
        //│ login   │ email           │ password   │
        //├─────────┼─────────────────┼────────────┤
        //│ john    │ john@mail.com   │ 12345      │
        //│ jane    │ jane@mail.com   │ 67890      │
        //│ bob     │ bob@mail.com    │ qwerty     │
        //└─────────┴─────────────────┴────────────┘

        /// try с ресурсами, чтобы они автоматически закрывались
        //1) Connection conn - соединение с бд
        //2) Statement stmt - объект для выполнения запроса
        //3) Result rs - результат запроса
        //Их нужно закрывать, тк они занимают ограниченные системные ресурсы (память, сетевые соединения, файловые дескрипторы)
        //Если их не закрыть, то произойти утечка памяти, исчерпание пула соединений,
        //а также произойдёт блокировка строк для изменений, что помешает изменить данные другим пользователям.
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            //stmt.executeQuery(sql) - отправляет sql запрос в постгрис
            //Постгрис выполняет запрос и возвращает результат в виде таблицы данных
            //ResultSet rs - представляет собой указатель на эти данные
            //ResultSet rs - не загружает все данные в память сразу, а построчно

            /// Обрабатываем каждую строчку
            //Начальное состояние: указатель ПЕРЕД первой строкой
            //
            //    ↓ (указатель)
            //┌─────────┬─────────────────┬────────────┐
            //│ login   │ email           │ password   │
            //├─────────┼─────────────────┼────────────┤
            //│ john    │ john@mail.com   │ 12345      │ ← Строка 1
            //│ jane    │ jane@mail.com   │ 67890      │ ← Строка 2
            //│ bob     │ bob@mail.com    │ qwerty     │ ← Строка 3
            //└─────────┴─────────────────┴────────────┘
            //
            //Шаг 1: rs.next() → true, указатель на строку 1
            //Шаг 2: rs.next() → true, указатель на строку 2
            //Шаг 3: rs.next() → true, указатель на строку 3
            //Шаг 4: rs.next() → false, выход из цикла
            while (rs.next()) {
                UserProfileForDB user = new UserProfileForDB( //создание отдельного объекта под каждого пользователя
                        rs.getString("login"), //берём значение колонки "login" из текущей строки
                        rs.getString("email"), //берём значение колонки "email"
                        rs.getString("password") //берём значение колонки "password"
                );
                users.put(user.getLogin(), user); //добавляем созданный объект в HashMap
            }
            System.out.println("Загружено пользователей из БД: " + users.size());
        } catch (SQLException e) {
            System.err.println("Ошибка загрузки пользователей: " + e.getMessage());
            /// Возможные ошибки:
            //Connection refused - PostgreSQL не запущен
            //Database "fileManager" does not exist - бд не создана
            //Relation "users" does not exist - таблица users не создана
            //Access denied - неверный пароль
        }
        return users;
    }

    /// Сохранить пользователя в БД
    //ВЫЗЫВАЕТСЯ ИЗ AccountService.addNewUser()
    public static void saveUser(String login, String email, String password) {
        /// Команда INSERT добавляет новую строку в таблицу
        //INSERT INTO users - в какую таблицу добавляем
        //(login, email, password) - какие колонки заполняем
        //VALUES (?, ?, ?) - значение для вставки в колонки
        /// Пример:
        //До выполнения:
        //┌────┬─────────┬─────────────────┬──────────┬────────────────────────┐
        //│ id │ login   │ email           │ password │ created_at             │
        //├────┼─────────┼─────────────────┼──────────┼────────────────────────┤
        //│ 1  │ john    │ john@mail.com   │ 12345    │ 2024-01-01 10:00:00   │
        //│ 2  │ jane    │ jane@mail.com   │ 67890    │ 2024-01-02 11:00:00   │
        //└────┴─────────┴─────────────────┴──────────┴────────────────────────┘
        //
        //После выполнения INSERT для ("bob", "bob@mail.com", "qwerty"):
        //
        //┌────┬─────────┬─────────────────┬──────────┬────────────────────────┐
        //│ id │ login   │ email           │ password │ created_at             │
        //├────┼─────────┼─────────────────┼──────────┼────────────────────────┤
        //│ 1  │ john    │ john@mail.com   │ 12345    │ 2024-01-01 10:00:00   │
        //│ 2  │ jane    │ jane@mail.com   │ 67890    │ 2024-01-02 11:00:00   │
        //│ 3  │ bob     │ bob@mail.com    │ qwerty   │ 2024-01-03 12:00:00   │ ← НОВАЯ СТРОКА
        //└────┴─────────┴─────────────────┴──────────┴────────────────────────┘
        String sql = "INSERT INTO users (login, email, password) VALUES (?, ?, ?)";
        // ? - плейсхолдеры - это маркеры, которые будут заменены на реальные значения.
        //Они защищают от SQL-инъекций

        //Открываем соединение с постгрис: getConnection()
        //Создаём подготовленный запрос: conn.prepareStatement(sql))
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            /// Почему именно prepareStatement, а не просто statement?
            /// У statement:
            // 1) запрос создаётся каждый раз;
            // 2) нет защиты от sql-инъекций;
            // 3) медленнее при повторном использовании;
            // 4) нельзя использовать ?
            /// У prepareStatement:
            // 1) запрос подготавливается 1 раз;
            // 2) есть защита;
            // 3) быстрее при повторном использовании;
            // 4) можно использовать ?
            /// Зачем нужны плейсхолдеры и prepareStatement?
            //Злоумышленник может ввести в логин какую-то комануд и войти как админ,
            //но с prepareStatement все специальные символы будут экранироваться, что помешает мошеннику.

            /// Установка значений в плейсхолдеры:
            pstmt.setString(1, login); //в первый ?
            pstmt.setString(2, email); //во второй ?
            pstmt.setString(3, password); //в третий ?
            /// Пример:
            //SQL шаблон: INSERT INTO users (login, email, password) VALUES (?, ?, ?)
            //                                                               │  │  │
            //                                                               │  │  └── position 3
            //                                                               │  └───── position 2
            //                                                               └──────── position 1
            //
            //После pstmt.setString(1, "bob"):
            //INSERT INTO users (login, email, password) VALUES ('bob', ?, ?)
            //
            //После pstmt.setString(2, "bob@mail.com"):
            //INSERT INTO users (login, email, password) VALUES ('bob', 'bob@mail.com', ?)
            //
            //После pstmt.setString(3, "qwerty"):
            //INSERT INTO users (login, email, password) VALUES ('bob', 'bob@mail.com', 'qwerty')

            /// Отправляем sql-запрос в бд и возвращаем количество изменённых строк
            pstmt.executeUpdate();
            //Нужен именно executeUpdate, а не executeQuery, тк он возвращает количество изменённых строк, а не таблицу с данными

            System.out.println("Пользователь сохранен в БД: " + login);

        } catch (SQLException e) {
            System.err.println("Ошибка сохранения пользователя: " + e.getMessage());
        }
    }

    /// Проверить существование логина в БД
    //Если метод возвращает истину, то логин уже занят,
    //а если ложь - свободен
    public static boolean loginExistsInDb(String login) {
        //COUNT(*) - посчитать количество строк с заданным логичном
        String sql = "SELECT COUNT(*) FROM users WHERE login = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, login);
            ResultSet rs = pstmt.executeQuery(); //возвращаем объект ResultSet (в данном случае кол-во совпадений)

            //Передвигаем указатель на строку с результатом
            if (rs.next()) {
                //Получаем значение из первой колонки и возвращаем истину или ложь
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            System.err.println("Ошибка проверки логина: " + e.getMessage());
        }

        return false;
    }

    /// Получить пользователя по логину из БД
    //Если возвращает объект UserProfileForDB, то пользователь найден,
    //а если null, то пользователь не найден.
    public static UserProfileForDB getUserFromDb(String login) {
        //Выбираем необхлдимые колонки и пишем условие
        String sql = "SELECT login, email, password FROM users WHERE login = ?";

        /// Открываем соединение и отпарвляем в постгрис готовый запрос
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, login);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new UserProfileForDB(
                        rs.getString("login"),
                        rs.getString("email"),
                        rs.getString("password")
                );
            }

        } catch (SQLException e) {
            System.err.println("Ошибка получения пользователя: " + e.getMessage());
        }

        return null;
    }
}