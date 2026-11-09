package com.peergeek.security;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitingFilter extends OncePerRequestFilter {
	private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

	private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
	private final SecurityErrorWriter errorWriter;
	private final int authLimit;
	private final int authWindowSeconds;
	private final int reportLimit;
	private final int reportWindowSeconds;

	// In-memory fallback if Redis is unavailable or in local test without Redis
	private final ConcurrentHashMap<String, WindowCounter> localCounters = new ConcurrentHashMap<>();

	public RateLimitingFilter(
			ObjectProvider<StringRedisTemplate> redisTemplateProvider,
			SecurityErrorWriter errorWriter,
			int authLimit,
			int authWindowSeconds,
			int reportLimit,
			int reportWindowSeconds) {
		this.redisTemplateProvider = redisTemplateProvider;
		this.errorWriter = errorWriter;
		this.authLimit = authLimit;
		this.authWindowSeconds = authWindowSeconds;
		this.reportLimit = reportLimit;
		this.reportWindowSeconds = reportWindowSeconds;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String path = request.getRequestURI();
		String method = request.getMethod();

		boolean isAuth = "POST".equalsIgnoreCase(method) && (
				path.endsWith("/auth/login") ||
				path.endsWith("/auth/register") ||
				path.endsWith("/auth/verify-email") ||
				path.endsWith("/auth/resend-verification-otp") ||
				path.endsWith("/auth/forgot-password") ||
				path.endsWith("/auth/verify-reset-otp") ||
				path.endsWith("/auth/reset-password")
		);

		boolean isReport = "POST".equalsIgnoreCase(method) && path.matches(".*/courses/\\d+/reports/?$");

		if (isAuth) {
			String clientKey = "ratelimit:auth:" + resolveClientIp(request) + ":" + path;
			if (!tryAcquire(clientKey, authLimit, authWindowSeconds)) {
				rejectRateLimit(request, response, authWindowSeconds);
				return;
			}
		} else if (isReport) {
			String clientKey = "ratelimit:report:" + resolveClientIp(request) + ":" + path;
			if (!tryAcquire(clientKey, reportLimit, reportWindowSeconds)) {
				rejectRateLimit(request, response, reportWindowSeconds);
				return;
			}
		}

		filterChain.doFilter(request, response);
	}

	private boolean tryAcquire(String key, int limit, int windowSeconds) {
		StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
		if (redis != null) {
			try {
				Long count = redis.opsForValue().increment(key);
				if (count != null && count == 1L) {
					redis.expire(key, java.time.Duration.ofSeconds(windowSeconds));
				}
				return count != null && count <= limit;
			} catch (Exception ex) {
				log.warn("Redis rate-limiting failed for key {}, falling back to in-memory counter: {}", key, ex.getMessage());
			}
		}

		// Fallback to in-memory window counter
		long now = Instant.now().getEpochSecond();
		WindowCounter counter = localCounters.compute(key, (k, existing) -> {
			if (existing == null || now - existing.startTime >= windowSeconds) {
				return new WindowCounter(now, new AtomicInteger(1));
			}
			existing.counter.incrementAndGet();
			return existing;
		});

		// Periodically prune stale counters
		if (localCounters.size() > 1000) {
			localCounters.entrySet().removeIf(entry -> now - entry.getValue().startTime >= windowSeconds * 2L);
		}

		return counter.counter.get() <= limit;
	}

	private void rejectRateLimit(HttpServletRequest request, HttpServletResponse response, int retryAfterSeconds)
			throws IOException {
		response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
		errorWriter.write(
				request,
				response,
				HttpStatus.TOO_MANY_REQUESTS,
				"RATE_LIMIT_EXCEEDED",
				"Too many requests. Please try again later.");
	}

	private String resolveClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
	}

	private static class WindowCounter {
		final long startTime;
		final AtomicInteger counter;

		WindowCounter(long startTime, AtomicInteger counter) {
			this.startTime = startTime;
			this.counter = counter;
		}
	}
}
