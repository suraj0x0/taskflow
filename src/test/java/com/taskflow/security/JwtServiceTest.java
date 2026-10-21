package com.taskflow.security;

import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final byte[] SIGNING_SECRET =
            "taskflow-test-signing-secret-key-32-bytes-minimum".getBytes(StandardCharsets.UTF_8);
    private static final String ENCODED_SECRET = Base64.getEncoder().encodeToString(SIGNING_SECRET);

    private final JwtService jwtService = new JwtService(ENCODED_SECRET, 60_000);

    @Test
    void generatedTokenIsAcceptedAndContainsTheUserEmail() {
        User user = new User("TaskFlow User", "user@example.com", Role.WORKER);

        String token = jwtService.generateToken(user);

        assertTrue(jwtService.isTokenValid(token));
        assertEquals("user@example.com", jwtService.extractEmail(token));
    }

    @Test
    void rejectsExpiredToken() {
        String token = token(Keys.hmacShaKeyFor(SIGNING_SECRET), "user@example.com",
                new Date(System.currentTimeMillis() - 60_000));

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void rejectsMalformedToken() {
        assertFalse(jwtService.isTokenValid("not.a.valid.jwt"));
        assertFalse(jwtService.isTokenValid(""));
        assertFalse(jwtService.isTokenValid(null));
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-test-signing-secret-key-32-bytes-minimum".getBytes(StandardCharsets.UTF_8));
        String token = token(otherKey, "user@example.com",
                new Date(System.currentTimeMillis() + 60_000));

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void rejectsSignedTokenWithoutSubject() {
        String token = Jwts.builder()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SIGNING_SECRET), Jwts.SIG.HS256)
                .compact();

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void rejectsWeakSecretsAndNonpositiveExpiration() {
        String weakSecret = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        assertThrows(RuntimeException.class, () -> new JwtService(weakSecret, 60_000));
        assertThrows(IllegalArgumentException.class, () -> new JwtService(ENCODED_SECRET, 0));
    }

    private String token(SecretKey key, String subject, Date expiration) {
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
