//package org.example.db;
//
//import java.sql.*;
//import java.util.HashMap;
//import java.util.Map;
//
//public class DatabaseConnection {
//    private static final String URL = "jdbc:postgresql://localhost:5432/fileManager";
//    private static final String USER = "postgres";
//    private static final String PASSWORD = "MyLitlePony1"; // ЗАМЕНИТЕ НА ВАШ ПАРОЛЬ!
//
//    static {
//        try {
//            Class.forName("org.postgresql.Driver");
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private static Connection getConnection() throws SQLException {
//        return DriverManager.getConnection(URL, USER, PASSWORD);
//    }
//
//    // Загрузить всех пользователей из БД в Map
//    public static Map<String, UserProfileForDB> loadAllUsers() {
//        Map<String, UserProfileForDB> users = new HashMap<>();
//        String sql = "SELECT login, email, password FROM users";
//
//        try (Connection conn = getConnection();
//             Statement stmt = conn.createStatement();
//             ResultSet rs = stmt.executeQuery(sql)) {
//
//            while (rs.next()) {
//                UserProfileForDB user = new UserProfileForDB(
//                        rs.getString("login"),
//                        rs.getString("email"),
//                        rs.getString("password")
//                );
//                users.put(user.getLogin(), user);
//            }
//            System.out.println("Загружено пользователей из БД: " + users.size());
//        } catch (SQLException e) {
//            System.err.println("Ошибка загрузки пользователей: " + e.getMessage());
//        }
//        return users;
//    }
//
//    // Сохранить пользователя в БД
//    public static void saveUser(String login, String email, String password) {
//        String sql = "INSERT INTO users (login, email, password) VALUES (?, ?, ?)";
//
//        try (Connection conn = getConnection();
//             PreparedStatement pstmt = conn.prepareStatement(sql)) {
//
//            pstmt.setString(1, login);
//            pstmt.setString(2, email);
//            pstmt.setString(3, password);
//            pstmt.executeUpdate();
//            System.out.println("Пользователь сохранен в БД: " + login);
//
//        } catch (SQLException e) {
//            System.err.println("Ошибка сохранения пользователя: " + e.getMessage());
//        }
//    }
//
//    // Проверить существование логина в БД
//    public static boolean loginExistsInDb(String login) {
//        String sql = "SELECT COUNT(*) FROM users WHERE login = ?";
//
//        try (Connection conn = getConnection();
//             PreparedStatement pstmt = conn.prepareStatement(sql)) {
//
//            pstmt.setString(1, login);
//            ResultSet rs = pstmt.executeQuery();
//
//            if (rs.next()) {
//                return rs.getInt(1) > 0;
//            }
//
//        } catch (SQLException e) {
//            System.err.println("Ошибка проверки логина: " + e.getMessage());
//        }
//
//        return false;
//    }
//
//    // Получить пользователя из БД
//    public static UserProfileForDB getUserFromDb(String login) {
//        String sql = "SELECT login, email, password FROM users WHERE login = ?";
//
//        try (Connection conn = getConnection();
//             PreparedStatement pstmt = conn.prepareStatement(sql)) {
//
//            pstmt.setString(1, login);
//            ResultSet rs = pstmt.executeQuery();
//
//            if (rs.next()) {
//                return new UserProfileForDB(
//                        rs.getString("login"),
//                        rs.getString("email"),
//                        rs.getString("password")
//                );
//            }
//
//        } catch (SQLException e) {
//            System.err.println("Ошибка получения пользователя: " + e.getMessage());
//        }
//
//        return null;
//    }
//}

package org.example.db;

import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.HashMap;
import java.util.Map;

public class DatabaseConnection {

    public static void saveUser(String login, String email, String password) {
        UserEntity user = new UserEntity(login, email, password);
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();
            session.save(user);
            session.getTransaction().commit();
        }
    }

    public static UserEntity getUserFromDb(String login) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<UserEntity> query = session.createQuery(
                    "FROM UserEntity WHERE login = :login", UserEntity.class);
            query.setParameter("login", login);
            return query.uniqueResult();
        }
    }

    public static Map<String, UserEntity> loadAllUsers() {
        Map<String, UserEntity> userMap = new HashMap<>();
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<UserEntity> query = session.createQuery("FROM UserEntity", UserEntity.class);
            for (UserEntity user : query.list()) {
                userMap.put(user.getLogin(), user);
            }
        }
        return userMap;
    }

    public static boolean loginExistsInDb(String login) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Long> query = session.createQuery(
                    "SELECT COUNT(*) FROM UserEntity WHERE login = :login", Long.class);
            query.setParameter("login", login);
            return query.uniqueResult() > 0;
        }
    }
}