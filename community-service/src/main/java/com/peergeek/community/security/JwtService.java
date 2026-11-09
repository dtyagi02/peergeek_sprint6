package com.peergeek.community.security;

import com.peergeek.community.common.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private final SecretKey signingKey;

    public JwtService(@Value("${peergeek.auth.jwt-secret}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            log.warn("JWT secret length is less than 32 bytes, padding for safety");
            byte[] padded = new byte[32];
            System.arraycopy(secretBytes, 0, padded, 0, Math.min(secretBytes.length, 32));
            this.signingKey = Keys.hmacShaKeyFor(padded);
        } else {
            this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        }
    }

    public CurrentUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = Long.parseLong(claims.getSubject());
        String roleStr = claims.get("role", String.class);
        UserRole role;
        try {
            role = roleStr != null ? UserRole.valueOf(roleStr.toUpperCase()) : UserRole.LEARNER;
        } catch (IllegalArgumentException e) {
            role = UserRole.LEARNER;
        }
        return new CurrentUser(userId, role);
    }

    public String generateToken(Long userId, UserRole role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(86400)))
                .signWith(signingKey)
                .compact();
    }
}

