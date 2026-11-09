package com.peergeek.identity.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import com.peergeek.identity.config.AuthSettings;
import com.peergeek.identity.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
	private final AuthSettings settings;
	private final SecretKey signingKey;

	public JwtService(AuthSettings settings, Environment environment) {
		this.settings = settings;
		byte[] secret = settings.getJwtSecret().getBytes(StandardCharsets.UTF_8);
		if (secret.length < 32) {
			throw new IllegalStateException("PeerGeek JWT secret must be at least 32 UTF-8 bytes");
		}
		if (settings.getAccessTokenTtlSeconds() <= 0 || settings.getRefreshTokenTtlDays() <= 0
				|| settings.getResetOtpTtlMinutes() <= 0 || settings.getResetOtpCooldownSeconds() < 0) {
			throw new IllegalStateException("PeerGeek authentication time-to-live settings are invalid");
		}
		this.signingKey = Keys.hmacShaKeyFor(secret);
	}

	public String issue(UserEntity user) {
		Instant issuedAt = Instant.now();
		return Jwts.builder()
				.subject(Long.toString(user.getId()))
				.claim("role", user.getRole().name())
				.claim("userId", user.getId())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(issuedAt.plusSeconds(settings.getAccessTokenTtlSeconds())))
				.signWith(signingKey)
				.compact();
	}

	public TokenClaims parse(String compactToken) {
		Claims claims = Jwts.parser().verifyWith(signingKey).build()
				.parseSignedClaims(compactToken).getPayload();
		long userId;
		if (claims.get("userId") != null) {
			userId = ((Number) claims.get("userId")).longValue();
		} else {
			userId = Long.parseLong(claims.getSubject());
		}
		if (userId < 1 || claims.getIssuedAt() == null || claims.getExpiration() == null) {
			throw new IllegalArgumentException("Invalid access token claims");
		}
		return new TokenClaims(userId, claims.get("role", String.class));
	}

	public long expiresInSeconds() {
		return settings.getAccessTokenTtlSeconds();
	}

	public record TokenClaims(long userId, String role) {
	}
}

