package com.peergeek.security;

import org.springframework.http.HttpMethod;
import java.util.List;

/** Add later public API routes here; all routes not listed here require authentication. */
public final class PublicEndpoints {
	private PublicEndpoints() {
	}

	public static List<Endpoint> application() {
		return List.of(
				new Endpoint(HttpMethod.POST, "/api/v1/auth/register"),
				new Endpoint(HttpMethod.POST, "/api/v1/auth/verify-email"),
				new Endpoint(HttpMethod.POST, "/api/v1/auth/resend-verification-otp"),
				new Endpoint(HttpMethod.POST, "/api/v1/auth/login"),
				new Endpoint(HttpMethod.POST, "/api/v1/auth/refresh"),
				new Endpoint(HttpMethod.POST, "/api/v1/auth/forgot-password"),
				new Endpoint(HttpMethod.POST, "/api/v1/auth/verify-reset-otp"),
				new Endpoint(HttpMethod.POST, "/api/v1/auth/reset-password"),
				new Endpoint(HttpMethod.GET, "/api/v1/categories"),
				new Endpoint(HttpMethod.GET, "/api/v1/users/*/public"),
				new Endpoint(HttpMethod.GET, "/api/v1/users/by-username/*"),
				new Endpoint(HttpMethod.GET, "/api/v1/users/*/profile"),
				new Endpoint(HttpMethod.GET, "/api/v1/users/by-username/*/profile"),
				new Endpoint(HttpMethod.GET, "/api/v1/users/*/avatar"),
				new Endpoint(HttpMethod.GET, "/api/v1/public/courses"),
				new Endpoint(HttpMethod.GET, "/api/v1/public/courses/popular"),
				new Endpoint(HttpMethod.GET, "/api/v1/public/courses/*"),
				new Endpoint(HttpMethod.GET, "/api/v1/public/courses/*/thumbnail"),
				new Endpoint(HttpMethod.GET, "/api/v1/public/courses/*/reviews"),
				new Endpoint(HttpMethod.GET, "/api/v1/public/courses/*/reviews/summary"),
				new Endpoint(HttpMethod.GET, "/api/v1/public/courses/*/doubt-session-availability"),
				new Endpoint(HttpMethod.POST, "/api/v1/doubt-sessions/webhook"),
				new Endpoint(HttpMethod.GET, "/api/v1/public/categories"),
				new Endpoint(HttpMethod.GET, "/api/v1/community/posts"),
				new Endpoint(HttpMethod.GET, "/api/v1/community/posts/*"),
				new Endpoint(HttpMethod.GET, "/api/v1/community/posts/*/comments"),
				new Endpoint(HttpMethod.GET, "/api/v1/domains/communities"),
				new Endpoint(HttpMethod.GET, "/api/v1/domains/*/community"),
				new Endpoint(HttpMethod.GET, "/api/v1/domains/*/community/messages"),
				new Endpoint(HttpMethod.GET, "/api/v1/domains/*/community/members"),
				new Endpoint(HttpMethod.GET, "/api/v1/referrals/validate"),
				new Endpoint(HttpMethod.GET, "/api/v1/referrals/config"),
				new Endpoint(HttpMethod.GET, "/api/v1/certificates/verify/*"),
				new Endpoint(HttpMethod.POST, "/api/v1/payments/razorpay/webhook"),
				new Endpoint(HttpMethod.GET, "/ws"),
				new Endpoint(HttpMethod.GET, "/ws/**"));
	}

	public static List<Endpoint> testOnly() {
		return List.of(
				new Endpoint(HttpMethod.GET, "/api/v1/test/errors/**"),
				new Endpoint(HttpMethod.POST, "/api/v1/test/errors/**"),
				new Endpoint(HttpMethod.GET, "/no-such-route"));
	}

	public record Endpoint(HttpMethod method, String path) {
	}
}
