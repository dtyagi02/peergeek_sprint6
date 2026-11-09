package com.peergeek.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import com.peergeek.config.AuthSettings;
import com.peergeek.user.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
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
		if (environment.matchesProfiles("prod")
				&& AuthSettings.DEVELOPMENT_SECRET.equals(settings.getJwtSecret())) {
			throw new IllegalStateException("A production JWT secret must be configured");
		}
		if (environment.matchesProfiles("prod") && !settings.isRefreshCookieSecure()) {
			throw new IllegalStateException("Secure refresh cookies must be enabled in production");
		}
		this.signingKey = Keys.hmacShaKeyFor(secret);
	}

	public String issue(UserEntity user) {
		Instant issuedAt = Instant.now();
		return Jwts.builder()
				.subject(Long.toString(user.getId()))
				.claim("role", user.getRole().name())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(issuedAt.plusSeconds(settings.getAccessTokenTtlSeconds())))
				.signWith(signingKey)
				.compact();
	}

	public TokenClaims parse(String compactToken) {
		Claims claims = Jwts.parser().verifyWith(signingKey).build()
				.parseSignedClaims(compactToken).getPayload();
		long userId = Long.parseLong(claims.getSubject());
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
