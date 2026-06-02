package org.example.accounts;

public class UserProfile {
    private final String login;
    private final String email;
    private final String pass;

    public UserProfile(String login, String email, String pass) {
        this.login = login;
        this.email = email;
        this.pass = pass;
    }

    public String getLogin() {
        return login;
    }

    public String getEmail() {
        return email;
    }

    public String getPass() {
        return pass;
    }
}