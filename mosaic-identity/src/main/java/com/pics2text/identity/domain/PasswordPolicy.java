package com.pics2text.identity.domain;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static boolean valid(String password) {
        return password != null && password.length() >= 5 && password.length() <= 128;
    }
}
