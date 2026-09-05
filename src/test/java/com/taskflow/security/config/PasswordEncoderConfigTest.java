package com.taskflow.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordEncoderConfigTest {

    private final PasswordEncoderConfig config = new PasswordEncoderConfig();

    @Test
    @DisplayName("PasswordEncoder bean should be a BCryptPasswordEncoder and properly hash/verify passwords")
    void shouldInitializeBCryptPasswordEncoder() {
        PasswordEncoder encoder = config.passwordEncoder();

        assertNotNull(encoder);
        assertTrue(encoder instanceof BCryptPasswordEncoder);

        String rawPassword = "StrongPassword123!";
        String encoded1 = encoder.encode(rawPassword);
        String encoded2 = encoder.encode(rawPassword);

        assertNotNull(encoded1);
        assertNotEquals(rawPassword, encoded1);
        assertTrue(encoded1.startsWith("$2a$") || encoded1.startsWith("$2b$"));

        // BCrypt salt generates different hashes for same password
        assertNotEquals(encoded1, encoded2);

        assertTrue(encoder.matches(rawPassword, encoded1));
        assertTrue(encoder.matches(rawPassword, encoded2));
        assertFalse(encoder.matches("WrongPassword", encoded1));
    }
}

