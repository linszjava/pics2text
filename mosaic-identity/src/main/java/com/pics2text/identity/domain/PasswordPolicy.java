package com.pics2text.identity.domain;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static boolean valid(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            return false;
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (char character : password.toCharArray()) {
            upper |= Character.isUpperCase(character);
            lower |= Character.isLowerCase(character);
            digit |= Character.isDigit(character);
            special |= !Character.isLetterOrDigit(character);
        }
        return upper && lower && digit && special;
    }
}
