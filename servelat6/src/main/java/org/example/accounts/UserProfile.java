package org.example.accounts;

public class UserProfile {
    private final String pass;
    private final String email;
    private final String login;

    public UserProfile(String login, String email, String pass) {
        this.login = login;
        this.email = email;
        this.pass = pass;
    }

    public String getPass() {
        return pass;
    }

    public String getEmail() {
        return email;
    }

    public String getLogin() {
        return login;
    }
}
