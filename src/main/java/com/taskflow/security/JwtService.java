package com.taskflow.security;

import com.taskflow.user.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${taskflow.jwt.secret:}") String base64Secret,
            @Value("${taskflow.jwt.expiration-ms:86400000}") long expirationMs
    ) {
        // Fallback development-only secret if not provided via env/config
        String secretToUse = base64Secret;
        if (secretToUse == null || secretToUse.isBlank()) {
            // 64-byte random-like development fallback (BASE64). DEVELOPMENT ONLY.
            secretToUse = "w7yZ3Vn8qKp9rT6sX2b4C1d5F7gH9jK3L0mN2pQ5rS8tV1wX4zY6aB8cD0eF2gH";
            // ensure base64
            secretToUse = Base64.getEncoder().encodeToString(secretToUse.getBytes(StandardCharsets.UTF_8));
        }

        byte[] keyBytes = Base64.getDecoder().decode(secretToUse);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiry = Date.from(now.plusMillis(expirationMs));

        return Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(issuedAt)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
