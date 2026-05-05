package org.example.accounts;

import org.example.db.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class AccountService {
    private final Map<String, UserProfile> loginToProfile;
    private final Map<String, UserProfile> sessionIdToProfile;
    private static final String USERS_ROOT = "C:/filemanager/";

    public AccountService() {
        loginToProfile = new HashMap<>();
        sessionIdToProfile = new HashMap<>();
        new File(USERS_ROOT).mkdirs();

        loadUsersFromDatabase();
    }

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
        DatabaseConnection.saveUser(
                userProfile.getLogin(),
                userProfile.getEmail(),
                userProfile.getPass()
        );

        loginToProfile.put(userProfile.getLogin(), userProfile);

        new File(USERS_ROOT + userProfile.getLogin()).mkdirs();

        System.out.println("Добавлен пользователь: " + userProfile.getLogin());
    }

    public UserProfile getUserByLogin(String login) {
        UserProfile user = loginToProfile.get(login);

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
        if (loginToProfile.containsKey(login)) {
            return true;
        }

        return DatabaseConnection.loginExistsInDb(login);
    }

    public String getUserRootFolder(String login) {
        return USERS_ROOT + login;
    }
}