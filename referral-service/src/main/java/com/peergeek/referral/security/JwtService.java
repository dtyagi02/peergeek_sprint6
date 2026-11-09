package com.peergeek.referral.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import javax.crypto.spec.SecretKeySpec;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	private final Key key;

	public JwtService(@Value("${peergeek.auth.jwt-secret}") String secret) {
		byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
		byte[] keyBytes = new byte[32];
		System.arraycopy(secretBytes, 0, keyBytes, 0, Math.min(secretBytes.length, 32));
		this.key = new SecretKeySpec(keyBytes, "HmacSHA256");
	}

	public TokenClaims parse(String token) {
		Claims claims = Jwts.parser()
				.setSigningKey(key)
				.build()
				.parseClaimsJws(token)
				.getBody();

		Long userId = null;
		if (claims.get("userId") != null) {
			userId = Long.valueOf(claims.get("userId").toString());
		} else if (claims.getSubject() != null) {
			try {
				userId = Long.valueOf(claims.getSubject());
			} catch (NumberFormatException ignored) {}
		}

		String role = claims.get("role", String.class);
		return new TokenClaims(userId, role);
	}

	public record TokenClaims(Long userId, String role) {}
}

