package com.pics2text.identity.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class UsernamePolicy {

    private static final Pattern VALID_USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,63}");

    private UsernamePolicy() {
    }

    public static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean valid(String username) {
        return VALID_USERNAME.matcher(normalize(username)).matches();
    }
}
