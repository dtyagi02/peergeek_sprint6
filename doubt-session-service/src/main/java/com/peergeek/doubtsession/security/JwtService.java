package com.peergeek.doubtsession.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
	private final SecretKey signingKey;

	public JwtService(@Value("${peergeek.auth.jwt-secret:development-only-peergeek-jwt-secret-replace-before-production}") String jwtSecret) {
		byte[] secret = jwtSecret.getBytes(StandardCharsets.UTF_8);
		this.signingKey = Keys.hmacShaKeyFor(secret);
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
		return new TokenClaims(userId, claims.get("role", String.class));
	}

	public record TokenClaims(long userId, String role) {
	}
}

