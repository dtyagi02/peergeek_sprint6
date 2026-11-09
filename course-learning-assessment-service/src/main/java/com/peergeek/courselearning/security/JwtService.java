package com.peergeek.courselearning.security;

import com.peergeek.courselearning.common.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(@Value("${peergeek.auth.jwt-secret:dev_jwt_secret_must_be_at_least_32_characters_long_for_hmac}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public CurrentUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String sub = claims.getSubject();
        Long userId = claims.get("userId", Long.class);
        if (userId == null && sub != null) {
            try {
                userId = Long.parseLong(sub);
            } catch (NumberFormatException ignored) {}
        }

        String roleStr = claims.get("role", String.class);
        UserRole role = UserRole.LEARNER;
        if (roleStr != null) {
            try {
                role = UserRole.valueOf(roleStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        return new CurrentUser(userId, role);
    }

    public String generateToken(Long userId, UserRole role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userId", userId)
                .claim("role", role.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(key)
                .compact();
    }
}

