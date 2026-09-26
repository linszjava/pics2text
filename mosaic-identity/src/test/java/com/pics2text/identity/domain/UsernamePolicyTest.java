package com.pics2text.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UsernamePolicyTest {

    @Test
    void normalizesUsername() {
        assertEquals("admin.user", UsernamePolicy.normalize("  Admin.User "));
    }

    @Test
    void acceptsSupportedCharacters() {
        assertTrue(UsernamePolicy.valid("design_user-01"));
    }

    @Test
    void rejectsWhitespaceAndChineseCharacters() {
        assertFalse(UsernamePolicy.valid("设计 用户"));
    }
}
