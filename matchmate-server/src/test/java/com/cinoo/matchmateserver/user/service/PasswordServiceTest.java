package com.cinoo.matchmateserver.user.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordServiceTest {

    private static final String PASSWORD = "12345678";
    private final PasswordService passwordService = new PasswordService();

    @Test
    void encodeCreatesBcryptPassword() {
        String encodedPassword = passwordService.encode(PASSWORD);

        assertNotEquals(PASSWORD, encodedPassword);
        assertTrue(encodedPassword.startsWith("$2"));
        assertTrue(passwordService.matches(PASSWORD, encodedPassword));
    }

    @Test
    void rejectsWrongPassword() {
        String encodedPassword = passwordService.encode(PASSWORD);

        assertFalse(passwordService.matches("wrongPassword", encodedPassword));
    }
}
