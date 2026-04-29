package org.example.accounts;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class AccountService {
    private final Map<String, UserProfile> loginToProfile;    // новый
    private final Map<String, UserProfile> sessionIdToProfile;
    private static final String USERS_ROOT = "C:/filemanager/"; // родительская папка

    public AccountService() {
        loginToProfile = new HashMap<>();
        sessionIdToProfile = new HashMap<>();
        new File(USERS_ROOT).mkdirs(); // создать папку если нет
    }

    public void addNewUser(UserProfile userProfile) {
        loginToProfile.put(userProfile.getLogin(), userProfile);
        // создать домашнюю папку пользователя
        new File(USERS_ROOT + userProfile.getLogin()).mkdirs();
    }

    public UserProfile getUserByLogin(String login) {
        return loginToProfile.get(login);
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
        return loginToProfile.containsKey(login);
    }

    public String getUserRootFolder(String login) {
        return USERS_ROOT + login;
    }
}