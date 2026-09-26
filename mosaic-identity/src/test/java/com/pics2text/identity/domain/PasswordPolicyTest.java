package com.pics2text.identity.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsStrongPassword() {
        assertTrue(PasswordPolicy.valid("Strong-Pass-2026"));
    }

    @Test
    void rejectsShortPassword() {
        assertFalse(PasswordPolicy.valid("Aa1!short"));
    }

    @Test
    void rejectsPasswordWithoutSpecialCharacter() {
        assertFalse(PasswordPolicy.valid("LongPassword2026"));
    }

    @Test
    void rejectsNullPassword() {
        assertFalse(PasswordPolicy.valid(null));
    }
}
