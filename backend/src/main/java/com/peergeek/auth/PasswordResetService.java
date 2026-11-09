package com.peergeek.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import com.peergeek.audit.AuditAction;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.config.AuthSettings;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
	private static final int MAX_ATTEMPTS = 5;
	private final UserRepository users;
	private final PasswordResetOtpRepository otps;
	private final RefreshTokenRepository refreshTokens;
	private final PasswordEncoder passwordEncoder;
	private final AuthSettings settings;
	private final AuditService auditService;
	private final SecureRandom secureRandom = new SecureRandom();
	private final String dummyOtpHash;

	public PasswordResetService(
			UserRepository users, PasswordResetOtpRepository otps, RefreshTokenRepository refreshTokens,
			PasswordEncoder passwordEncoder, AuthSettings settings, AuditService auditService) {
		this.users = users;
		this.otps = otps;
		this.refreshTokens = refreshTokens;
		this.passwordEncoder = passwordEncoder;
		this.settings = settings;
		this.auditService = auditService;
		this.dummyOtpHash = passwordEncoder.encode("000000");
	}

	@Transactional
	public OtpDispatch prepareOtp(String normalizedEmail) {
		Optional<UserEntity> userResult = users.findByEmail(normalizedEmail);
		if (userResult.isEmpty() || !userResult.get().isEnabled()) {
			passwordEncoder.encode("000000");
			return null;
		}
		UserEntity user = userResult.get();
		Instant now = Instant.now();
		Optional<PasswordResetOtpEntity> latest = otps.findFirstByUser_IdOrderByCreatedAtDesc(user.getId());
		if (latest.isPresent()
				&& latest.get().getCreatedAt().plusSeconds(settings.getResetOtpCooldownSeconds()).isAfter(now)) {
			passwordEncoder.encode("000000");
			return null;
		}
		for (PasswordResetOtpEntity previous : otps.findAllByUser_IdAndConsumedAtIsNull(user.getId())) {
			previous.consume(now);
		}
		String otp = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
		otps.save(new PasswordResetOtpEntity(
				user, passwordEncoder.encode(otp), now.plusSeconds(settings.getResetOtpTtlMinutes() * 60)));
		return new OtpDispatch(user.getEmail(), otp);
	}

	@Transactional
	public boolean resetPassword(String normalizedEmail, String otpValue, String newPassword) {
		Optional<UserEntity> userResult = users.findByEmail(normalizedEmail);
		if (userResult.isEmpty() || !userResult.get().isEnabled()) {
			passwordEncoder.matches(otpValue, dummyOtpHash);
			return false;
		}
		UserEntity user = userResult.get();
		List<PasswordResetOtpEntity> active = otps.findAllUnconsumedForUpdate(user.getId());
		if (active.isEmpty()) {
			passwordEncoder.matches(otpValue, dummyOtpHash);
			return false;
		}
		PasswordResetOtpEntity otp = active.getFirst();
		Instant now = Instant.now();
		if (otp.getAttempts() >= MAX_ATTEMPTS || !now.isBefore(otp.getExpiresAt())) {
			passwordEncoder.matches(otpValue, dummyOtpHash);
			otp.consume(now);
			return false;
		}
		if (!passwordEncoder.matches(otpValue, otp.getOtpHash())) {
			otp.incrementAttempts();
			if (otp.getAttempts() >= MAX_ATTEMPTS) otp.consume(now);
			return false;
		}
		user.updatePasswordHash(passwordEncoder.encode(newPassword));
		otp.consume(now);
		refreshTokens.revokeAllByUserId(user.getId());
		auditService.record(new AuditEvent(user.getId(), AuditAction.AUTH_PASSWORD_RESET_COMPLETED,
				"USER", Long.toString(user.getId()), Map.of()));
		return true;
	}

	public record OtpDispatch(String recipient, String otp) {
	}
}
