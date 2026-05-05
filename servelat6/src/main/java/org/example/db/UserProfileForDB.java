package org.example.db;

// Вспомогательный класс для хранения данных пользователя
public class UserProfileForDB {
    private final String login;
    private final String email;
    private final String password;

    public UserProfileForDB(String login, String email, String password) {
        this.login = login;
        this.email = email;
        this.password = password;
    }

    public String getLogin() { return login; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
}