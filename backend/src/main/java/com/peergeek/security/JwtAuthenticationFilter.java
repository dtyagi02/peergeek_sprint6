package com.peergeek.security;

import java.io.IOException;
import java.util.Optional;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import io.jsonwebtoken.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
	private final JwtService jwtService;
	private final UserRepository userRepository;
	private final JsonAuthenticationEntryPoint entryPoint;

	public JwtAuthenticationFilter(
			JwtService jwtService, UserRepository userRepository, JsonAuthenticationEntryPoint entryPoint) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
		this.entryPoint = entryPoint;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			chain.doFilter(request, response);
			return;
		}
		try {
			JwtService.TokenClaims claims = jwtService.parse(authorization.substring(7));
			Optional<UserEntity> user = userRepository.findById(claims.userId())
					.filter(UserEntity::isEnabled)
					.filter(UserEntity::isEmailVerified);
			if (user.isEmpty()) {
				entryPoint.commence(request, response,
						new org.springframework.security.authentication.BadCredentialsException(
								"Token subject is unavailable"));
				return;
			}
			if (!user.get().getRole().name().equals(claims.role())) {
				throw new IllegalArgumentException("Invalid access token role");
			}
			CurrentUserPrincipal principal = new CurrentUserPrincipal(
					user.get().getId(), user.get().getRole());
			var authentication = new UsernamePasswordAuthenticationToken(
					principal, null, java.util.List.of(
							new SimpleGrantedAuthority("ROLE_" + user.get().getRole().name())));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		} catch (JwtException | IllegalArgumentException exception) {
			SecurityContextHolder.clearContext();
			entryPoint.commence(request, response,
					new org.springframework.security.authentication.BadCredentialsException(
							"Invalid or expired access token"));
			return;
		}
		chain.doFilter(request, response);
	}
}
