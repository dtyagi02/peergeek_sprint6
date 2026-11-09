package com.peergeek.referral.security;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final String gatewaySharedSecret;

	public JwtAuthenticationFilter(
			JwtService jwtService,
			@Value("${peergeek.gateway.shared-secret:pg-internal-gateway-secret-2026}") String gatewaySharedSecret) {
		this.jwtService = jwtService;
		this.gatewaySharedSecret = gatewaySharedSecret;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		try {
			String authHeader = request.getHeader("Authorization");
			if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
				String token = authHeader.substring(BEARER_PREFIX.length()).trim();
				if (!token.isEmpty()) {
					try {
						JwtService.TokenClaims claims = jwtService.parse(token);
						CurrentUserPrincipal principal = new CurrentUserPrincipal(claims.userId(), claims.role());
						UsernamePasswordAuthenticationToken authentication =
								new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
						authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
						SecurityContextHolder.getContext().setAuthentication(authentication);
					} catch (Exception ex) {
						log.debug("Invalid JWT token: {}", ex.getMessage());
					}
				}
			} else {
				String gatewaySecretHeader = request.getHeader("X-Gateway-Secret");
				String headerUserId = request.getHeader("X-User-Id");
				if (headerUserId != null && !headerUserId.isBlank()) {
					if (gatewaySecretHeader != null && gatewaySharedSecret.equals(gatewaySecretHeader)) {
						try {
							Long userId = Long.valueOf(headerUserId.trim());
							String headerRole = request.getHeader("X-User-Role");
							CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, headerRole != null ? headerRole : "USER");
							UsernamePasswordAuthenticationToken authentication =
									new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
							authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
							SecurityContextHolder.getContext().setAuthentication(authentication);
						} catch (NumberFormatException ignored) {}
					} else {
						log.warn("Rejected unverified X-User-Id header without valid X-Gateway-Secret");
					}
				}
			}
		} catch (Exception ex) {
			log.error("Could not set user authentication in security context", ex);
		}

		filterChain.doFilter(request, response);
	}
}

