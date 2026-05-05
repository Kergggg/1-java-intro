package org.example.accounts;

import org.example.db.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class AccountService {
    ///Параметры подключения
    private final Map<String, UserProfile> loginToProfile;
    private final Map<String, UserProfile> sessionIdToProfile;
    private static final String USERS_ROOT = "C:/filemanager/";

    public AccountService() {
        loginToProfile = new HashMap<>();
        sessionIdToProfile = new HashMap<>();
        new File(USERS_ROOT).mkdirs();

        // Загружаем пользователей из базы данных при запуске
        loadUsersFromDatabase();
    }

    // Загрузка пользователей из БД в память
    private void loadUsersFromDatabase() {
        Map<String, UserProfileForDB> dbUsers = DatabaseConnection.loadAllUsers();
        for (UserProfileForDB dbUser : dbUsers.values()) {
            UserProfile user = new UserProfile(
                    dbUser.getLogin(),
                    dbUser.getEmail(),
                    dbUser.getPassword()
            );
            loginToProfile.put(user.getLogin(), user);
        }
        System.out.println("AccountService: загружено " + loginToProfile.size() + " пользователей");
    }

    public void addNewUser(UserProfile userProfile) {
        // Сохраняем в БД
        DatabaseConnection.saveUser(
                userProfile.getLogin(),
                userProfile.getEmail(),
                userProfile.getPass()
        );

        // Сохраняем в памяти
        loginToProfile.put(userProfile.getLogin(), userProfile);

        // Создаем домашнюю папку пользователя
        new File(USERS_ROOT + userProfile.getLogin()).mkdirs();

        System.out.println("Добавлен пользователь: " + userProfile.getLogin());
    }

    public UserProfile getUserByLogin(String login) {
        // Сначала проверяем в памяти
        UserProfile user = loginToProfile.get(login);

        // Если не нашли в памяти, пробуем загрузить из БД
        if (user == null) {
            UserProfileForDB dbUser = DatabaseConnection.getUserFromDb(login);
            if (dbUser != null) {
                user = new UserProfile(
                        dbUser.getLogin(),
                        dbUser.getEmail(),
                        dbUser.getPassword()
                );
                loginToProfile.put(login, user);
                System.out.println("Загружен из БД: " + login);
            }
        }

        return user;
    }

    public UserProfile getUserBySessionId(String sessionId) {
        return sessionIdToProfile.get(sessionId);
    }

    public void addSession(String sessionId, UserProfile userProfile) {
        sessionIdToProfile.put(sessionId, userProfile);
    }

    public void deleteSession(String sessionId) {
        sessionIdToProfile.remove(sessionId);
    }

    public boolean loginExists(String login) {
        // Проверяем в памяти
        if (loginToProfile.containsKey(login)) {
            return true;
        }

        // Проверяем в БД
        return DatabaseConnection.loginExistsInDb(login);
    }

    public String getUserRootFolder(String login) {
        return USERS_ROOT + login;
    }
}