package com.peergeek.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import com.peergeek.audit.AuditAction;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.config.AuthSettings;
import com.peergeek.exception.ConflictException;
import com.peergeek.exception.FieldErrorResponse;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserProfileDto;
import com.peergeek.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
	private static final Logger log = LoggerFactory.getLogger(AuthService.class);
	private static final String RESET_ACK = "If an account exists for that email, password reset instructions will be sent.";
	private static final String RESEND_ACK = "If an unverified account exists for that email, a new verification code has been sent.";
	public static final java.util.Set<String> RESERVED_USERNAMES = java.util.Set.of(
			"admin", "administrator", "support", "system", "api", "root");
	public static final java.util.regex.Pattern USERNAME_PATTERN =
			java.util.regex.Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{2,29}$");

	private final UserRepository users;
	private final RefreshTokenRepository refreshTokens;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthSettings settings;
	private final AuditService auditService;
	private final PasswordResetService passwordResetService;
	private final PasswordResetEmailDispatcher emailDispatcher;
	private final AuthOtpService authOtpService;
	private final RegistrationEmailDispatcher registrationEmailDispatcher;
	private final com.peergeek.wallet.WalletService walletService;
	private final com.peergeek.referral.ReferralService referralService;
	private final SecureRandom secureRandom = new SecureRandom();
	private final String dummyPasswordHash;

	@Autowired
	public AuthService(
			UserRepository users,
			RefreshTokenRepository refreshTokens,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			AuthSettings settings,
			AuditService auditService,
			PasswordResetService passwordResetService,
			PasswordResetEmailDispatcher emailDispatcher,
			AuthOtpService authOtpService,
			RegistrationEmailDispatcher registrationEmailDispatcher,
			com.peergeek.wallet.WalletService walletService,
			com.peergeek.referral.ReferralService referralService) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.settings = settings;
		this.auditService = auditService;
		this.passwordResetService = passwordResetService;
		this.emailDispatcher = emailDispatcher;
		this.authOtpService = authOtpService;
		this.registrationEmailDispatcher = registrationEmailDispatcher;
		this.walletService = walletService;
		this.referralService = referralService;
		this.dummyPasswordHash = passwordEncoder.encode("not-a-real-peer-geek-password");
	}

	public AuthService(
			UserRepository users,
			RefreshTokenRepository refreshTokens,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			AuthSettings settings,
			AuditService auditService,
			PasswordResetService passwordResetService,
			PasswordResetEmailDispatcher emailDispatcher,
			AuthOtpService authOtpService,
			RegistrationEmailDispatcher registrationEmailDispatcher,
			com.peergeek.wallet.WalletService walletService) {
		this(users, refreshTokens, passwordEncoder, jwtService, settings, auditService,
				passwordResetService, emailDispatcher, authOtpService, registrationEmailDispatcher, walletService, null);
	}

	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		validatePassword(email, request.password(), "password");
		String displayName = trim(request.displayName());
		if (containsMarkup(displayName)) {
			throw validation("displayName", "HTML markup is not allowed");
		}
		String username = normalizeUsername(request.username());
		validateUsername(username);

		Optional<UserEntity> existing = users.findByEmail(email);
		UserEntity user;

		if (existing.isPresent()) {
			if (referralService != null && request.referralCode() != null && !request.referralCode().isBlank()) {
				throw validation("referralCode", "Referral codes can only be used when creating a new account");
			}
			UserEntity existingUser = existing.get();
			if (existingUser.isEmailVerified()) {
				throw new ConflictException("An account with this email already exists");
			}
			// Check if username is taken by another user
			Optional<UserEntity> userWithUsername = users.findByUsername(username);
			if (userWithUsername.isPresent() && !userWithUsername.get().getId().equals(existingUser.getId())) {
				throw usernameUnavailable();
			}
			// Update pending unverified account
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
			if (referralService != null && request.referralCode() != null && !request.referralCode().isBlank()) {
				referralService.validateAndPrepareRegistration(request.referralCode(), email);
			}
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
			auditService.record(new AuditEvent(user.getId(), AuditAction.USER_REGISTERED,
					"USER", Long.toString(user.getId()), Map.of()));
		}

		walletService.getOrCreateWallet(user);

		if (referralService != null && request.referralCode() != null && !request.referralCode().isBlank()) {
			referralService.associateReferralOnRegistration(user, request.referralCode());
		}

		AuthOtpService.OtpDispatch dispatch = authOtpService.prepareRegistrationOtp(user);
		try {
			registrationEmailDispatcher.dispatch(dispatch.recipient(), dispatch.otp(),
					settings.getResetOtpTtlMinutes(), MDC.get("requestId"));
		} catch (RuntimeException exception) {
			log.error("Registration email could not be queued requestId={}", MDC.get("requestId"));
		}

		return new RegisterResponse("Verification code sent to your email", email, true);
	}

	@Transactional
	public GenericMessageResponse verifyEmail(VerifyEmailRequest request) {
		String email = normalizeEmail(request.email());
		if (!authOtpService.verifyEmailOtp(email, request.otp())) {
			throw validation("otp", "The verification code is invalid or has expired");
		}
		users.findByEmail(email).ifPresent(user -> {
			walletService.getOrCreateWallet(user);
			if (referralService != null) {
				referralService.completeReferralReward(user);
			}
		});
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
					settings.getResetOtpTtlMinutes(), MDC.get("requestId"));
		} catch (RuntimeException exception) {
			log.error("Registration email could not be queued requestId={}", MDC.get("requestId"));
		}
		return new GenericMessageResponse("A new verification code has been sent to your email.");
	}

	public IssuedSession login(LoginRequest request, String userAgent, String ip) {
		String email = normalizeEmail(request.email());
		Optional<UserEntity> result = users.findByEmail(email);
		boolean withinBcryptLimit = request.password().getBytes(StandardCharsets.UTF_8).length <= 72;
		String passwordToCheck = withinBcryptLimit ? request.password() : "invalid-password";
		boolean passwordMatches = passwordEncoder.matches(passwordToCheck,
				result.map(UserEntity::getPasswordHash).orElse(dummyPasswordHash)) && withinBcryptLimit;

		if (result.isEmpty() || !result.get().isEnabled() || !passwordMatches) {
			auditService.record(new AuditEvent(null, AuditAction.AUTH_LOGIN_FAILED,
					"USER", null, Map.of()));
			throw new UnauthorizedAuthException();
		}

		UserEntity user = result.get();
		if (!user.isEmailVerified()) {
			auditService.record(new AuditEvent(user.getId(), AuditAction.AUTH_LOGIN_FAILED,
					"USER", Long.toString(user.getId()), Map.of("reason", "EMAIL_NOT_VERIFIED")));
			throw new EmailNotVerifiedException();
		}

		auditService.record(new AuditEvent(user.getId(), AuditAction.AUTH_LOGIN_SUCCESS,
				"USER", Long.toString(user.getId()), Map.of()));
		return issueSession(user, null, userAgent, ip);
	}

	@Transactional
	public IssuedSession refresh(String rawToken, String userAgent, String ip) {
		if (rawToken == null || rawToken.isBlank()) return null;
		RefreshTokenEntity previous = refreshTokens.findByHashForUpdate(hashToken(rawToken)).orElse(null);
		if (previous == null) return null;
		Instant now = Instant.now();
		if (previous.getRevokedAt() != null) {
			refreshTokens.revokeFamily(previous.getUser().getId(), previous.getFamilyId());
			return null;
		}
		if (!now.isBefore(previous.getExpiresAt()) || !previous.getUser().isEnabled() || !previous.getUser().isEmailVerified()) {
			previous.revoke(now);
			return null;
		}
		String nextRaw = newRefreshToken();
		RefreshTokenEntity next = refreshTokens.save(new RefreshTokenEntity(
				previous.getUser(), hashToken(nextRaw), previous.getFamilyId(),
				now.plusSeconds(settings.getRefreshTokenTtlDays() * 86400), userAgent, ip));
		previous.replaceWith(next.getId(), now);
		return new IssuedSession(previous.getUser(), nextRaw);
	}

	@Transactional
	public void logout(Long currentUserId, String rawToken) {
		if (rawToken != null && !rawToken.isBlank()) {
			refreshTokens.findByHashForUpdate(hashToken(rawToken))
					.filter(token -> token.getUser().getId().equals(currentUserId))
					.ifPresent(token -> token.revoke(Instant.now()));
		}
		auditService.record(new AuditEvent(currentUserId, AuditAction.AUTH_LOGOUT,
				"USER", Long.toString(currentUserId), Map.of()));
	}

	public GenericMessageResponse requestPasswordReset(String email) {
		String normalizedEmail = normalizeEmail(email);
		AuthOtpService.OtpDispatch dispatch = authOtpService.preparePasswordResetOtp(normalizedEmail);
		if (dispatch != null) {
			try {
				emailDispatcher.dispatch(dispatch.recipient(), dispatch.otp(),
						settings.getResetOtpTtlMinutes(), MDC.get("requestId"));
			} catch (RuntimeException exception) {
				log.error("Password reset email could not be queued requestId={}", MDC.get("requestId"));
			}
		}
		auditService.record(new AuditEvent(null, AuditAction.AUTH_PASSWORD_RESET_REQUESTED,
				"PASSWORD_RESET", null, Map.of()));
		return new GenericMessageResponse(RESET_ACK);
	}

	@Transactional
	public VerifyResetOtpResponse verifyResetOtp(VerifyResetOtpRequest request) {
		String email = normalizeEmail(request.email());
		String resetToken = authOtpService.verifyResetOtp(email, request.otp());
		if (resetToken == null) {
			throw validation("otp", "The code is invalid or expired");
		}
		return new VerifyResetOtpResponse(resetToken, "Reset code verified successfully");
	}

	public void resetPassword(ResetPasswordRequest request) {
		String email = normalizeEmail(request.email());
		validatePassword(email, request.newPassword(), "newPassword");

		boolean success = false;
		if (request.resetToken() != null && !request.resetToken().isBlank()) {
			success = authOtpService.resetPasswordWithToken(email, request.resetToken(), request.newPassword());
			if (!success) {
				throw validation("resetToken", "The reset authorization is invalid or expired");
			}
		} else if (request.otp() != null && !request.otp().isBlank()) {
			success = passwordResetService.resetPassword(email, request.otp(), request.newPassword());
			if (!success) {
				throw validation("otp", "The code is invalid or expired");
			}
		} else {
			throw validation("otp", "Reset code or authorization token is required");
		}

		try {
			emailDispatcher.dispatchConfirmation(email, MDC.get("requestId"));
		} catch (RuntimeException exception) {
			log.error("Password reset confirmation email could not be queued requestId={}", MDC.get("requestId"));
		}
	}

	public IssuedSession issueSession(UserEntity user, String familyId, String userAgent, String ip) {
		String rawToken = newRefreshToken();
		String tokenFamily = familyId == null ? RefreshTokenEntity.newFamilyId() : familyId;
		refreshTokens.save(new RefreshTokenEntity(
				user, hashToken(rawToken), tokenFamily,
				Instant.now().plusSeconds(settings.getRefreshTokenTtlDays() * 86400), userAgent, ip));
		return new IssuedSession(user, rawToken);
	}

	public SessionResponse sessionResponse(IssuedSession session) {
		UserEntity user = session.user();
		return new SessionResponse(jwtService.issue(user), jwtService.expiresInSeconds(), UserProfileDto.from(user));
	}

	public long refreshCookieMaxAgeSeconds() {
		return settings.getRefreshTokenTtlDays() * 86400;
	}

	private void validatePassword(String email, String password, String field) {
		if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw validation(field, "must contain no more than 72 UTF-8 bytes");
		}
		if (password.equalsIgnoreCase(email)) {
			throw validation(field, "must not be the same as the email address");
		}
	}

	private InputValidationException validation(String field, String message) {
		return new InputValidationException("Request validation failed",
				java.util.List.of(new FieldErrorResponse(field, message)));
	}

	private boolean isUsernameConstraintViolation(DataIntegrityViolationException exception) {
		Throwable current = exception;
		while (current != null) {
			String message = current.getMessage();
			if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("username")) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private InputValidationException usernameUnavailable() {
		return new InputValidationException(
				"Username is already taken. Please choose another username.",
				java.util.List.of(new FieldErrorResponse("username", "Username is already taken. Please choose another username.")));
	}

	private void validateUsername(String username) {
		if (username == null || username.isBlank()) {
			throw validation("username", "Username is required");
		}
		if (username.length() < 3 || username.length() > 30) {
			throw validation("username", "Username must be between 3 and 30 characters");
		}
		if (!Character.isLetter(username.charAt(0))) {
			throw validation("username", "Username must start with a letter");
		}
		if (!USERNAME_PATTERN.matcher(username).matches()) {
			throw validation("username", "Username can only contain letters, numbers, and underscores");
		}
		if (RESERVED_USERNAMES.contains(username.toLowerCase(Locale.ROOT))) {
			throw validation("username", "This username is reserved. Please choose another username.");
		}
	}

	private String normalizeUsername(String username) {
		return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private boolean containsMarkup(String value) {
		return value.contains("<") || value.contains(">");
	}

	private String newRefreshToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hashToken(String rawToken) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	public record IssuedSession(UserEntity user, String refreshToken) {
	}
}
