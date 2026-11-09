package com.peergeek.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
	@Bean
	JwtAuthenticationFilter jwtAuthenticationFilter(
			JwtService jwtService, com.peergeek.user.UserRepository users,
			JsonAuthenticationEntryPoint entryPoint) {
		return new JwtAuthenticationFilter(jwtService, users, entryPoint);
	}

	@Bean
	FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
		FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	RateLimitingFilter rateLimitingFilter(
			org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redisTemplateProvider,
			SecurityErrorWriter errorWriter,
			@org.springframework.beans.factory.annotation.Value("${peergeek.security.rate-limiting.auth-limit:10}") int authLimit,
			@org.springframework.beans.factory.annotation.Value("${peergeek.security.rate-limiting.auth-window-seconds:60}") int authWindowSeconds,
			@org.springframework.beans.factory.annotation.Value("${peergeek.security.rate-limiting.report-limit:10}") int reportLimit,
			@org.springframework.beans.factory.annotation.Value("${peergeek.security.rate-limiting.report-window-seconds:60}") int reportWindowSeconds) {
		return new RateLimitingFilter(
				redisTemplateProvider, errorWriter, authLimit, authWindowSeconds, reportLimit, reportWindowSeconds);
	}

	@Bean
	FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(RateLimitingFilter filter) {
		FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtAuthenticationFilter jwtFilter,
			RateLimitingFilter rateLimitingFilter,
			JsonAuthenticationEntryPoint authenticationEntryPoint,
			JsonAccessDeniedHandler accessDeniedHandler,
			Environment environment) throws Exception {
		http
				.cors(cors -> { })
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.requestCache(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.headers(headers -> headers
						.contentSecurityPolicy(csp -> csp
								.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https: blob:; media-src 'self' https: blob:; connect-src 'self' https:; frame-ancestors 'none'; object-src 'none'; base-uri 'self';"))
						.frameOptions(org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig::deny)
						.contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
						.httpStrictTransportSecurity(hsts -> hsts
								.includeSubDomains(true)
								.maxAgeInSeconds(31536000))
						.referrerPolicy(referrer -> referrer
								.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.authorizeHttpRequests(authorize -> {
					PublicEndpoints.application().forEach(endpoint ->
							authorize.requestMatchers(endpoint.method(), endpoint.path()).permitAll());
					if (environment.matchesProfiles("test")) {
						PublicEndpoints.testOnly().forEach(endpoint ->
								authorize.requestMatchers(endpoint.method(), endpoint.path()).permitAll());
					}
					authorize.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**",
							"/actuator/info").permitAll();
					if (environment.matchesProfiles("dev")) {
						authorize.requestMatchers("/swagger-ui", "/swagger-ui/**",
								"/v3/api-docs", "/v3/api-docs/**").permitAll();
					}
					authorize.anyRequest().authenticated();
				})
				.addFilterBefore(rateLimitingFilter, org.springframework.security.web.authentication.logout.LogoutFilter.class)
				.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
