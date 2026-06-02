package org.example.accounts;

public class UserProfile {
    private final String pass;
    private final String email;

    public UserProfile(String email, String pass) {
        this.email = email;
        this.pass = pass;
    }

    public String getPass() {
        return pass;
    }

    public String getEmail() {
        return email;
    }
}