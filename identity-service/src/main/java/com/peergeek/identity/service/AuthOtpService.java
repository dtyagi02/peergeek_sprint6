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
import java.util.Optional;
import com.peergeek.identity.config.AuthSettings;
import com.peergeek.identity.entity.AuthOtpEntity;
import com.peergeek.identity.entity.OtpPurpose;
import com.peergeek.identity.entity.PasswordResetOtpEntity;
import com.peergeek.identity.entity.UserEntity;
import com.peergeek.identity.exception.FieldErrorResponse;
import com.peergeek.identity.exception.InputValidationException;
import com.peergeek.identity.repository.AuthOtpRepository;
import com.peergeek.identity.repository.PasswordResetOtpRepository;
import com.peergeek.identity.repository.RefreshTokenRepository;
import com.peergeek.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthOtpService {
	private static final Logger log = LoggerFactory.getLogger(AuthOtpService.class);
	public static final int MAX_ATTEMPTS = 5;

	private final UserRepository users;
	private final AuthOtpRepository otps;
	private final PasswordResetOtpRepository legacyResetOtps;
	private final RefreshTokenRepository refreshTokens;
	private final PasswordEncoder passwordEncoder;
	private final AuthSettings settings;
	private final SecureRandom secureRandom = new SecureRandom();
	private final String dummyOtpHash;

	public AuthOtpService(
			UserRepository users,
			AuthOtpRepository otps,
			PasswordResetOtpRepository legacyResetOtps,
			RefreshTokenRepository refreshTokens,
			PasswordEncoder passwordEncoder,
			AuthSettings settings) {
		this.users = users;
		this.otps = otps;
		this.legacyResetOtps = legacyResetOtps;
		this.refreshTokens = refreshTokens;
		this.passwordEncoder = passwordEncoder;
		this.settings = settings;
		this.dummyOtpHash = passwordEncoder.encode("000000");
	}

	@Transactional
	public OtpDispatch prepareRegistrationOtp(UserEntity user) {
		String email = user.getEmail();
		Instant now = Instant.now();

		for (AuthOtpEntity previous : otps.findAllByEmailAndPurposeAndConsumedAtIsNull(email, OtpPurpose.REGISTRATION)) {
			previous.consume(now);
		}

		String otp = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
		otps.save(new AuthOtpEntity(
				email,
				user,
				OtpPurpose.REGISTRATION,
				passwordEncoder.encode(otp),
				now.plusSeconds(settings.getResetOtpTtlMinutes() * 60)));

		log.info("Registration OTP prepared for user id={}", user.getId());
		return new OtpDispatch(email, otp);
	}

	@Transactional
	public OtpDispatch prepareResendRegistrationOtp(UserEntity user) {
		String email = user.getEmail();
		Instant now = Instant.now();

		Optional<AuthOtpEntity> latest = otps.findFirstByEmailAndPurposeOrderByCreatedAtDesc(
				email, OtpPurpose.REGISTRATION);
		if (latest.isPresent()
				&& latest.get().getCreatedAt().plusSeconds(settings.getResetOtpCooldownSeconds()).isAfter(now)) {
			throw new InputValidationException("Please wait a moment before requesting another code.",
					List.of(new FieldErrorResponse("email", "Please wait a moment before requesting another code.")));
		}

		return prepareRegistrationOtp(user);
	}

	@Transactional
	public boolean verifyEmailOtp(String normalizedEmail, String otpValue) {
		Optional<UserEntity> userOpt = users.findByEmail(normalizedEmail);
		if (userOpt.isEmpty()) {
			passwordEncoder.matches(otpValue, dummyOtpHash);
			return false;
		}

		UserEntity user = userOpt.get();
		List<AuthOtpEntity> active = otps.findAllUnconsumedForUpdate(normalizedEmail, OtpPurpose.REGISTRATION);
		if (active.isEmpty()) {
			passwordEncoder.matches(otpValue, dummyOtpHash);
			return false;
		}

		AuthOtpEntity otp = active.getFirst();
		Instant now = Instant.now();
		if (otp.getAttempts() >= MAX_ATTEMPTS || !now.isBefore(otp.getExpiresAt())) {
			passwordEncoder.matches(otpValue, dummyOtpHash);
			otp.consume(now);
			return false;
		}

		if (!passwordEncoder.matches(otpValue, otp.getOtpHash())) {
			otp.incrementAttempts();
			if (otp.getAttempts() >= MAX_ATTEMPTS) {
				otp.consume(now);
			}
			return false;
		}

		otp.consume(now);
		otp.markVerified(now);
		user.setEmailVerified(true);
		users.save(user);

		log.info("Email verified for user id={}", user.getId());
		return true;
	}

	@Transactional
	public OtpDispatch preparePasswordResetOtp(String normalizedEmail) {
		Optional<UserEntity> userResult = users.findByEmail(normalizedEmail);
		if (userResult.isEmpty() || !userResult.get().isEnabled() || !userResult.get().isEmailVerified()) {
			passwordEncoder.encode("000000");
			return null;
		}

		UserEntity user = userResult.get();
		Instant now = Instant.now();

		Optional<AuthOtpEntity> latest = otps.findFirstByEmailAndPurposeOrderByCreatedAtDesc(
				normalizedEmail, OtpPurpose.PASSWORD_RESET);
		if (latest.isPresent()
				&& latest.get().getCreatedAt().plusSeconds(settings.getResetOtpCooldownSeconds()).isAfter(now)) {
			passwordEncoder.encode("000000");
			return null;
		}

		for (AuthOtpEntity previous : otps.findAllByEmailAndPurposeAndConsumedAtIsNull(normalizedEmail, OtpPurpose.PASSWORD_RESET)) {
			previous.consume(now);
		}
		for (PasswordResetOtpEntity legacy : legacyResetOtps.findAllByUser_IdAndConsumedAtIsNull(user.getId())) {
			legacy.consume(now);
		}

		String otp = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
		String encodedOtp = passwordEncoder.encode(otp);
		Instant expiresAt = now.plusSeconds(settings.getResetOtpTtlMinutes() * 60);

		otps.save(new AuthOtpEntity(normalizedEmail, user, OtpPurpose.PASSWORD_RESET, encodedOtp, expiresAt));
		legacyResetOtps.save(new PasswordResetOtpEntity(user, encodedOtp, expiresAt));

		log.info("Password reset OTP prepared for user id={}", user.getId());
		return new OtpDispatch(user.getEmail(), otp);
	}

	@Transactional
	public String verifyResetOtp(String normalizedEmail, String otpValue) {
		Optional<UserEntity> userResult = users.findByEmail(normalizedEmail);
		if (userResult.isEmpty() || !userResult.get().isEnabled() || !userResult.get().isEmailVerified()) {
			passwordEncoder.matches(otpValue, dummyOtpHash);
			return null;
		}

		UserEntity user = userResult.get();
		List<AuthOtpEntity> active = otps.findAllUnconsumedForUpdate(normalizedEmail, OtpPurpose.PASSWORD_RESET);
		if (active.isEmpty()) {
			passwordEncoder.matches(otpValue, dummyOtpHash);
			return null;
		}

		AuthOtpEntity otp = active.getFirst();
		Instant now = Instant.now();
		if (otp.getAttempts() >= MAX_ATTEMPTS || !now.isBefore(otp.getExpiresAt())) {
			passwordEncoder.matches(otpValue, dummyOtpHash);
			otp.consume(now);
			return null;
		}

		if (!passwordEncoder.matches(otpValue, otp.getOtpHash())) {
			otp.incrementAttempts();
			if (otp.getAttempts() >= MAX_ATTEMPTS) {
				otp.consume(now);
			}
			return false ? "" : null;
		}

		String resetToken = newResetToken();
		String tokenHash = hashToken(resetToken);
		Instant tokenExpiresAt = now.plusSeconds(settings.getResetOtpTtlMinutes() * 60);

		otp.authorizeReset(tokenHash, tokenExpiresAt, now);

		log.info("Reset OTP verified for user id={}", user.getId());
		return resetToken;
	}

	@Transactional
	public boolean resetPasswordWithToken(String normalizedEmail, String resetToken, String newPassword) {
		String tokenHash = hashToken(resetToken);
		Optional<AuthOtpEntity> otpOpt = otps.findByResetTokenHashAndConsumedAtIsNull(tokenHash);
		if (otpOpt.isEmpty()) {
			return false;
		}

		AuthOtpEntity otp = otpOpt.get();
		if (!otp.getEmail().equalsIgnoreCase(normalizedEmail) || otp.getPurpose() != OtpPurpose.PASSWORD_RESET) {
			return false;
		}

		Instant now = Instant.now();
		if (otp.getResetTokenExpiresAt() == null || !now.isBefore(otp.getResetTokenExpiresAt())) {
			otp.consume(now);
			return false;
		}

		Optional<UserEntity> userOpt = users.findByEmail(normalizedEmail);
		if (userOpt.isEmpty() || !userOpt.get().isEnabled()) {
			otp.consume(now);
			return false;
		}

		UserEntity user = userOpt.get();
		user.updatePasswordHash(passwordEncoder.encode(newPassword));
		users.save(user);

		otp.consume(now);
		for (PasswordResetOtpEntity legacy : legacyResetOtps.findAllByUser_IdAndConsumedAtIsNull(user.getId())) {
			legacy.consume(now);
		}

		refreshTokens.revokeAllByUserId(user.getId());

		log.info("Password reset completed for user id={}", user.getId());
		return true;
	}

	public Optional<AuthOtpEntity> findByResetTokenHash(String tokenHash) {
		return otps.findByResetTokenHashAndConsumedAtIsNull(tokenHash);
	}

	private String newResetToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hashToken(String rawToken) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	public record OtpDispatch(String recipient, String otp) {
	}
}
