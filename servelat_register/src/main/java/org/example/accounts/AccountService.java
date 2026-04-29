package org.example.accounts;

import java.util.HashMap;
import java.util.Map;

public class AccountService {
    private final Map<String, UserProfile> emailToProfile;
    private final Map<String, UserProfile> sessionIdToProfile;

    public AccountService() {
        emailToProfile = new HashMap<>(); //для постоянного хранения всех пользователей
        sessionIdToProfile = new HashMap<>(); //для временного хранения активных сессий
    }

    public void addNewUser(UserProfile userProfile) {
        emailToProfile.put(userProfile.getEmail(), userProfile);
    }

    public UserProfile getUserByEmail(String email) {
        return emailToProfile.get(email);
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

    public boolean userExists(String email) {
        return emailToProfile.containsKey(email);
    }
}