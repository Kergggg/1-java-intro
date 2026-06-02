package org.example.db;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

//CREATE TABLE IF NOT EXISTS users (
//    id SERIAL PRIMARY KEY,
//    login VARCHAR(50) UNIQUE NOT NULL,
//    email VARCHAR(100) NOT NULL,
//    password VARCHAR(255) NOT NULL,
//    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
//);

public class DatabaseConnection {
    private static final String URL = "jdbc:postgresql://localhost:5432/fileManager";
    private static final String USER = "postgres";
    private static final String PASSWORD = "MyLitlePony1";

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static Map<String, UserProfileForDB> loadAllUsers() {
        Map<String, UserProfileForDB> users = new HashMap<>();
        String sql = "SELECT login, email, password FROM users";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                UserProfileForDB user = new UserProfileForDB(
                        rs.getString("login"),
                        rs.getString("email"),
                        rs.getString("password")
                );
                users.put(user.getLogin(), user);
            }
            System.out.println("Загружено пользователей из БД: " + users.size());
        } catch (SQLException e) {
            System.err.println("Ошибка загрузки пользователей: " + e.getMessage());
        }
        return users;
    }

    public static void saveUser(String login, String email, String password) {
        String sql = "INSERT INTO users (login, email, password) VALUES (?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, login);
            pstmt.setString(2, email);
            pstmt.setString(3, password);
            pstmt.executeUpdate();
            System.out.println("Пользователь сохранен в БД: " + login);

        } catch (SQLException e) {
            System.err.println("Ошибка сохранения пользователя: " + e.getMessage());
        }
    }

    public static boolean loginExistsInDb(String login) {
        String sql = "SELECT COUNT(*) FROM users WHERE login = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, login);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            System.err.println("Ошибка проверки логина: " + e.getMessage());
        }

        return false;
    }

    public static UserProfileForDB getUserFromDb(String login) {
        String sql = "SELECT login, email, password FROM users WHERE login = ?";

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