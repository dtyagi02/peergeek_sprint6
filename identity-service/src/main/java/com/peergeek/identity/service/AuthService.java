package com.peergeek.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import com.peergeek.identity.config.AuthSettings;
import com.peergeek.identity.dto.*;
import com.peergeek.identity.entity.RefreshTokenEntity;
import com.peergeek.identity.entity.UserEntity;
import com.peergeek.identity.exception.ConflictException;
import com.peergeek.identity.exception.EmailNotVerifiedException;
import com.peergeek.identity.exception.FieldErrorResponse;
import com.peergeek.identity.exception.InputValidationException;
import com.peergeek.identity.exception.UnauthorizedAuthException;
import com.peergeek.identity.repository.RefreshTokenRepository;
import com.peergeek.identity.repository.UserRepository;
import com.peergeek.identity.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
	private static final Logger log = LoggerFactory.getLogger(AuthService.class);
	private static final String RESET_ACK = "If an account exists for that email, password reset instructions will be sent.";
	private static final String RESEND_ACK = "If an unverified account exists for that email, a new verification code has been sent.";
	public static final Set<String> RESERVED_USERNAMES = Set.of(
			"admin", "administrator", "support", "system", "api", "root");
	public static final Pattern USERNAME_PATTERN =
			Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{2,29}$");

	private final UserRepository users;
	private final RefreshTokenRepository refreshTokens;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthSettings settings;
	private final AuthOtpService authOtpService;
	private final RegistrationEmailDispatcher registrationEmailDispatcher;
	private final PasswordResetEmailDispatcher passwordResetEmailDispatcher;
	private final OutboxEventWriter outboxEventWriter;
	private final SecureRandom secureRandom = new SecureRandom();
	private final String dummyPasswordHash;

	public AuthService(
			UserRepository users,
			RefreshTokenRepository refreshTokens,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			AuthSettings settings,
			AuthOtpService authOtpService,
			RegistrationEmailDispatcher registrationEmailDispatcher,
			PasswordResetEmailDispatcher passwordResetEmailDispatcher,
			OutboxEventWriter outboxEventWriter) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.settings = settings;
		this.authOtpService = authOtpService;
		this.registrationEmailDispatcher = registrationEmailDispatcher;
		this.passwordResetEmailDispatcher = passwordResetEmailDispatcher;
		this.outboxEventWriter = outboxEventWriter;
		this.dummyPasswordHash = passwordEncoder.encode("not-a-real-peer-geek-password");
	}

	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		validatePassword(email, request.password(), "password");
		String displayName = trim(request.displayName());
		if (containsMarkup(displayName)) {
			throw validation("displayName", "HTML markup is not allowed");
		}
		String username = normalizeUsername(request.username() != null && !request.username().isBlank() ? request.username() : UserEntity.defaultUsername(email));
		validateUsername(username);

		Optional<UserEntity> existing = users.findByEmail(email);
		UserEntity user;

		if (existing.isPresent()) {
			UserEntity existingUser = existing.get();
			if (existingUser.isEmailVerified()) {
				throw new ConflictException("An account with this email already exists");
			}
			Optional<UserEntity> userWithUsername = users.findByUsername(username);
			if (userWithUsername.isPresent() && !userWithUsername.get().getId().equals(existingUser.getId())) {
				throw usernameUnavailable();
			}
			existingUser.updatePasswordHash(passwordEncoder.encode(request.password()));
			existingUser.updateProfile(displayName, existingUser.getHeadline(), existingUser.getBio());
			existingUser.setUsername(username);
			try {
				user = users.saveAndFlush(existingUser);
			} catch (DataIntegrityViolationException exception) {
				if (isUsernameConstraintViolation(exception)) {
					throw usernameUnavailable();
				}
				throw new ConflictException("An account with this email already exists");
			}
		} else {
			if (users.existsByUsername(username)) {
				throw usernameUnavailable();
			}
			UserEntity newUser = new UserEntity(email, passwordEncoder.encode(request.password()), displayName, username, false);
			try {
				user = users.saveAndFlush(newUser);
			} catch (DataIntegrityViolationException exception) {
				if (isUsernameConstraintViolation(exception)) {
					throw usernameUnavailable();
				}
				throw new ConflictException("An account with this email already exists");
			}
		}

		// Persist Transactional Outbox Event for Notification and other services
		outboxEventWriter.writeEvent(
				"peergeek.identity.user-registered.v1",
				"UserRegistered",
				Map.of(
						"userId", user.getId(),
						"displayName", user.getDisplayName(),
						"email", user.getEmail(),
						"role", user.getRole().name()));

		AuthOtpService.OtpDispatch dispatch = authOtpService.prepareRegistrationOtp(user);
		try {
			registrationEmailDispatcher.dispatch(dispatch.recipient(), dispatch.otp(),
					settings.getResetOtpTtlMinutes(), "reg-" + user.getId());
		} catch (RuntimeException exception) {
			log.error("Registration email could not be queued for userId={}", user.getId());
		}

		return new RegisterResponse("Verification code sent to your email", email, true);
	}

	@Transactional
	public GenericMessageResponse verifyEmail(VerifyEmailRequest request) {
		String email = normalizeEmail(request.email());
		if (!authOtpService.verifyEmailOtp(email, request.otp())) {
			throw validation("otp", "The verification code is invalid or has expired");
		}
		return new GenericMessageResponse("Email verified successfully. You can now log in.");
	}

	@Transactional
	public GenericMessageResponse resendVerificationOtp(ResendVerificationRequest request) {
		String email = normalizeEmail(request.email());
		Optional<UserEntity> userOpt = users.findByEmail(email);
		if (userOpt.isEmpty() || userOpt.get().isEmailVerified()) {
			passwordEncoder.encode("000000");
			return new GenericMessageResponse(RESEND_ACK);
		}
		UserEntity user = userOpt.get();
		AuthOtpService.OtpDispatch dispatch = authOtpService.prepareResendRegistrationOtp(user);
		try {
			registrationEmailDispatcher.dispatch(dispatch.recipient(), dispatch.otp(),
					settings.getResetOtpTtlMinutes(), "resend-" + user.getId());
		} catch (RuntimeException exception) {
			log.error("Resend verification email could not be queued for userId={}", user.getId());
		}
		return new GenericMessageResponse(RESEND_ACK);
	}

	@Transactional
	public IssuedSession login(LoginRequest request, String userAgent, String ip) {
		String email = normalizeEmail(request.email());
		Optional<UserEntity> userOpt = users.findByEmail(email);

		if (userOpt.isEmpty()) {
			passwordEncoder.matches(request.password(), dummyPasswordHash);
			throw new UnauthorizedAuthException("Invalid email or password");
		}

		UserEntity user = userOpt.get();
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new UnauthorizedAuthException("Invalid email or password");
		}

		if (!user.isEmailVerified()) {
			throw new EmailNotVerifiedException(user.getEmail());
		}

		if (!user.isEnabled()) {
			throw new UnauthorizedAuthException("Your account is disabled. Please contact support.");
		}

		String refreshTokenRaw = generateSecureRandomToken();
		String tokenHash = hashRefreshToken(refreshTokenRaw);
		Instant expiresAt = Instant.now().plusSeconds(settings.getRefreshTokenTtlDays() * 86400);

		RefreshTokenEntity tokenEntity = new RefreshTokenEntity(
				user, tokenHash, RefreshTokenEntity.newFamilyId(), expiresAt, userAgent, ip);
		refreshTokens.save(tokenEntity);

		String accessToken = jwtService.issue(user);
		return new IssuedSession(accessToken, refreshTokenRaw, user, jwtService.expiresInSeconds());
	}

	@Transactional
	public IssuedSession refresh(String rawToken, String userAgent, String ip) {
		if (rawToken == null || rawToken.isBlank()) {
			return null;
		}

		String tokenHash = hashRefreshToken(rawToken);
		Optional<RefreshTokenEntity> tokenOpt = refreshTokens.findByHashForUpdate(tokenHash);

		if (tokenOpt.isEmpty()) {
			return null;
		}

		RefreshTokenEntity currentToken = tokenOpt.get();
		Instant now = Instant.now();

		if (!currentToken.isUsable(now)) {
			// Token reuse detected - revoke entire family
			refreshTokens.revokeFamily(currentToken.getUser().getId(), currentToken.getFamilyId());
			log.warn("Refresh token reuse detected for user id={}, revoking family id={}",
					currentToken.getUser().getId(), currentToken.getFamilyId());
			return null;
		}

		UserEntity user = currentToken.getUser();
		if (!user.isEnabled()) {
			currentToken.revoke(now, null);
			refreshTokens.save(currentToken);
			return null;
		}

		String newRefreshTokenRaw = generateSecureRandomToken();
		String newTokenHash = hashRefreshToken(newRefreshTokenRaw);
		Instant newExpiresAt = now.plusSeconds(settings.getRefreshTokenTtlDays() * 86400);

		RefreshTokenEntity newToken = new RefreshTokenEntity(
				user, newTokenHash, currentToken.getFamilyId(), newExpiresAt, userAgent, ip);
		RefreshTokenEntity savedNewToken = refreshTokens.save(newToken);

		currentToken.revoke(now, savedNewToken.getId());
		refreshTokens.save(currentToken);

		String accessToken = jwtService.issue(user);
		return new IssuedSession(accessToken, newRefreshTokenRaw, user, jwtService.expiresInSeconds());
	}

	@Transactional
	public void logout(Long userId, String rawToken) {
		if (rawToken != null && !rawToken.isBlank()) {
			String tokenHash = hashRefreshToken(rawToken);
			refreshTokens.findByHashForUpdate(tokenHash).ifPresent(token -> {
				token.revoke(Instant.now(), null);
				refreshTokens.save(token);
			});
		} else if (userId != null) {
			refreshTokens.revokeAllByUserId(userId);
		}
	}

	@Transactional
	public GenericMessageResponse requestPasswordReset(String email) {
		String normalized = normalizeEmail(email);
		AuthOtpService.OtpDispatch dispatch = authOtpService.preparePasswordResetOtp(normalized);
		if (dispatch != null) {
			try {
				passwordResetEmailDispatcher.dispatch(dispatch.recipient(), dispatch.otp(),
						settings.getResetOtpTtlMinutes(), "reset-" + System.currentTimeMillis());
			} catch (RuntimeException exception) {
				log.error("Password reset email could not be queued for email={}", normalized);
			}
		}
		return new GenericMessageResponse(RESET_ACK);
	}

	@Transactional
	public VerifyResetOtpResponse verifyResetOtp(VerifyResetOtpRequest request) {
		String normalized = normalizeEmail(request.email());
		String resetToken = authOtpService.verifyResetOtp(normalized, request.otp());
		if (resetToken == null) {
			throw validation("otp", "The verification code is invalid or has expired");
		}
		return new VerifyResetOtpResponse(resetToken, "Verification code confirmed");
	}

	@Transactional
	public GenericMessageResponse resetPassword(ResetPasswordRequest request) {
		validatePassword("", request.newPassword(), "newPassword");
		String tokenHash = hashRefreshToken(request.resetToken());

		var otpOpt = authOtpService.findByResetTokenHash(tokenHash);
		if (otpOpt.isEmpty()) {
			throw validation("resetToken", "The password reset token is invalid or has expired");
		}

		var otp = otpOpt.get();
		if (!authOtpService.resetPasswordWithToken(otp.getEmail(), request.resetToken(), request.newPassword())) {
			throw validation("resetToken", "The password reset token is invalid or has expired");
		}

		try {
			passwordResetEmailDispatcher.dispatchConfirmation(otp.getEmail(), "reset-conf-" + System.currentTimeMillis());
		} catch (RuntimeException exception) {
			log.error("Password reset confirmation email could not be queued for email={}", otp.getEmail());
		}

		return new GenericMessageResponse("Password reset successful. You can now sign in with your new password.");
	}

	public SessionResponse sessionResponse(IssuedSession session) {
		return new SessionResponse(
				session.accessToken(),
				session.expiresIn(),
				UserProfileDto.from(session.user()));
	}

	private String generateSecureRandomToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hashRefreshToken(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available", e);
		}
	}

	private String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeUsername(String username) {
		return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
	}

	private String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private boolean containsMarkup(String value) {
		return value != null && (value.contains("<") || value.contains(">"));
	}

	private void validatePassword(String email, String password, String field) {
		if (password == null || password.length() < 8) {
			throw validation(field, "Password must be at least 8 characters long");
		}
		if (password.length() > 72) {
			throw validation(field, "Password must not exceed 72 characters");
		}
		if (!email.isEmpty() && password.equalsIgnoreCase(email)) {
			throw validation(field, "Password cannot be the same as your email address");
		}
	}

	private void validateUsername(String username) {
		if (username == null || username.isBlank()) {
			throw validation("username", "Username is required");
		}
		if (!USERNAME_PATTERN.matcher(username).matches()) {
			throw validation("username", "Username must be 3-30 alphanumeric characters and start with a letter");
		}
		if (RESERVED_USERNAMES.contains(username.toLowerCase(Locale.ROOT))) {
			throw validation("username", "This username is reserved");
		}
	}

	private InputValidationException validation(String field, String message) {
		return new InputValidationException(message, List.of(new FieldErrorResponse(field, message)));
	}

	private InputValidationException usernameUnavailable() {
		return validation("username", "Username is already taken. Please choose another username.");
	}

	private boolean isUsernameConstraintViolation(DataIntegrityViolationException exception) {
		String message = exception.getMessage();
		return message != null && (message.contains("uk_users_username") || message.contains("username"));
	}

	public record IssuedSession(String accessToken, String refreshToken, UserEntity user, long expiresIn) {
	}
}
