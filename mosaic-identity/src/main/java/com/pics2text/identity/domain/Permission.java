package com.pics2text.identity.domain;

import java.util.List;

public enum Permission {
    USER_READ("user:read"),
    USER_WRITE("user:write"),
    USER_KICK("user:kick"),
    PROJECT_CREATE("project:create"),
    PROJECT_READ("project:read"),
    PROJECT_MEMBER_READ("project:member:read"),
    PROJECT_MEMBER_WRITE("project:member:write"),
    AUDIT_READ("audit:read");

    private final String code;

    Permission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static List<String> adminCodes() {
        return List.of(values()).stream().map(Permission::code).toList();
    }
}
