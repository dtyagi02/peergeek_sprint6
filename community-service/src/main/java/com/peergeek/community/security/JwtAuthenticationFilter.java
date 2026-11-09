package com.peergeek.community.security;

import com.peergeek.community.common.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
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

        String authHeader = request.getHeader("Authorization");

        // 1. Bearer Token Parsing (Primary cryptographic verification)
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                CurrentUser user = jwtService.parseToken(token);
                setSecurityContext(user, request);
                filterChain.doFilter(request, response);
                return;
            } catch (Exception e) {
                log.warn("Failed to parse JWT token: {}", e.getMessage());
            }
        }

        // 2. Gateway Enriched Header Fallback - strictly requires trusted X-Gateway-Secret
        String gatewaySecretHeader = request.getHeader("X-Gateway-Secret");
        String userIdHeader = request.getHeader("X-User-Id");
        if (StringUtils.hasText(userIdHeader)) {
            if (StringUtils.hasText(gatewaySecretHeader) && gatewaySharedSecret.equals(gatewaySecretHeader)) {
                try {
                    Long userId = Long.parseLong(userIdHeader);
                    String roleHeader = request.getHeader("X-User-Role");
                    UserRole role = UserRole.LEARNER;
                    if (StringUtils.hasText(roleHeader)) {
                        try {
                            role = UserRole.valueOf(roleHeader.toUpperCase());
                        } catch (IllegalArgumentException ignored) {}
                    }
                    CurrentUser user = new CurrentUser(userId, role);
                    setSecurityContext(user, request);
                } catch (NumberFormatException e) {
                    log.warn("Invalid X-User-Id header: {}", userIdHeader);
                }
            } else {
                log.warn("Rejected unverified X-User-Id header from request without valid X-Gateway-Secret");
            }
        }

        filterChain.doFilter(request, response);
    }

    private void setSecurityContext(CurrentUser user, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.role().name()));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}

