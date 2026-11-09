package com.peergeek.auth;

import java.time.Duration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import com.peergeek.config.AuthSettings;
import com.peergeek.security.CurrentUserProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
	private static final String REFRESH_COOKIE = "PEERGEEK_REFRESH";
	private static final String COOKIE_PATH = "/api/v1/auth";
	private final AuthService authService;
	private final AuthSettings settings;
	private final CurrentUserProvider currentUserProvider;

	public AuthController(
			AuthService authService, AuthSettings settings, CurrentUserProvider currentUserProvider) {
		this.authService = authService;
		this.settings = settings;
		this.currentUserProvider = currentUserProvider;
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(
			@Valid @RequestBody RegisterRequest request) {
		RegisterResponse response = authService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/verify-email")
	public ResponseEntity<GenericMessageResponse> verifyEmail(
			@Valid @RequestBody VerifyEmailRequest request) {
		return ResponseEntity.ok(authService.verifyEmail(request));
	}

	@PostMapping("/resend-verification-otp")
	public ResponseEntity<GenericMessageResponse> resendVerificationOtp(
			@Valid @RequestBody ResendVerificationRequest request) {
		return ResponseEntity.ok(authService.resendVerificationOtp(request));
	}

	@PostMapping("/login")
	public ResponseEntity<SessionResponse> login(
			@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		AuthService.IssuedSession issued = authService.login(
				request, servletRequest.getHeader("User-Agent"), servletRequest.getRemoteAddr());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshCookie(issued.refreshToken()).toString())
				.body(authService.sessionResponse(issued));
	}

	@PostMapping("/refresh")
	public ResponseEntity<SessionResponse> refresh(
			@CookieValue(name = REFRESH_COOKIE, required = false) String token,
			HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
		AuthService.IssuedSession issued = authService.refresh(
				token, servletRequest.getHeader("User-Agent"), servletRequest.getRemoteAddr());
		if (issued == null) {
			servletResponse.addHeader(HttpHeaders.SET_COOKIE, clearedCookie().toString());
			throw new UnauthorizedAuthException();
		}
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshCookie(issued.refreshToken()).toString())
				.body(authService.sessionResponse(issued));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
		authService.logout(currentUserProvider.requireCurrentUser().id(), token);
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, clearedCookie().toString()).build();
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<GenericMessageResponse> forgotPassword(
			@Valid @RequestBody ForgotPasswordRequest request) {
		return ResponseEntity.accepted().body(authService.requestPasswordReset(request.email()));
	}

	@PostMapping("/verify-reset-otp")
	public ResponseEntity<VerifyResetOtpResponse> verifyResetOtp(
			@Valid @RequestBody VerifyResetOtpRequest request) {
		return ResponseEntity.ok(authService.verifyResetOtp(request));
	}

	@PostMapping("/reset-password")
	public GenericMessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		authService.resetPassword(request);
		return new GenericMessageResponse("Password updated");
	}

	private ResponseCookie refreshCookie(String token) {
		return ResponseCookie.from(REFRESH_COOKIE, token)
				.httpOnly(true)
				.secure(settings.isRefreshCookieSecure())
				.sameSite("Lax")
				.path(COOKIE_PATH)
				.maxAge(Duration.ofSeconds(authService.refreshCookieMaxAgeSeconds()))
				.build();
	}

	private ResponseCookie clearedCookie() {
		return ResponseCookie.from(REFRESH_COOKIE, "")
				.httpOnly(true)
				.secure(settings.isRefreshCookieSecure())
				.sameSite("Lax")
				.path(COOKIE_PATH)
				.maxAge(Duration.ZERO)
				.build();
	}
}
