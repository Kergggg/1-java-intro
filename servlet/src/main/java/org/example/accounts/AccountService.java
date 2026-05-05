package org.example.accounts;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class AccountService {
    private final Map<String, UserProfile> loginToProfile;
    private static final String USERS_ROOT = "C:/filemanager/";

    public AccountService() {
        loginToProfile = new HashMap<>();
        new File(USERS_ROOT).mkdirs();
    }

    public void addNewUser(UserProfile userProfile) {
        loginToProfile.put(userProfile.getLogin(), userProfile);
        new File(USERS_ROOT + userProfile.getLogin()).mkdirs();
    }

    public UserProfile getUserByLogin(String login) {
        return loginToProfile.get(login);
    }

    public boolean loginExists(String login) {
        return loginToProfile.containsKey(login);
    }

    public String getUserRootFolder(String login) {
        return USERS_ROOT + login;
    }
}