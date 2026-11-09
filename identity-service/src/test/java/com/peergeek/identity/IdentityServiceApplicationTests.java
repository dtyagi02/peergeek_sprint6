package com.peergeek.identity;

import java.time.Instant;
import java.util.Optional;
import com.peergeek.identity.config.AuthSettings;
import com.peergeek.identity.dto.*;
import com.peergeek.identity.entity.*;
import com.peergeek.identity.repository.*;
import com.peergeek.identity.security.JwtService;
import com.peergeek.identity.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {IdentityServiceApplication.class, IdentityServiceApplicationTests.TestConfig.class})
@ActiveProfiles("test")
@Transactional
class IdentityServiceApplicationTests {

	@TestConfiguration
	static class TestConfig {
		@Bean
		@Primary
		public MailSender mockMailSender() {
			return new MailSender() {
				@Override
				public void sendRegistrationVerification(String recipient, String otp, long expiryMinutes) {}
				@Override
				public void sendPasswordReset(String recipient, String otp, long expiryMinutes) {}
				@Override
				public void sendPasswordResetConfirmation(String recipient) {}
			};
		}
	}

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private AuthOtpRepository authOtpRepository;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private AuthService authService;

	@Autowired
	private UserService userService;

	@Test
	void contextLoads() {
		assertNotNull(authService);
		assertNotNull(userService);
		assertNotNull(jwtService);
		assertNotNull(passwordEncoder);
	}

	@Test
	void testPasswordHashingBCryptCompatibility() {
		String rawPassword = "SecurePassword123!";
		String hash = passwordEncoder.encode(rawPassword);
		assertThat(hash).startsWith("$2a$10$");
		assertTrue(passwordEncoder.matches(rawPassword, hash));
		assertFalse(passwordEncoder.matches("WrongPassword", hash));
	}

	@Test
	void testJwtIssuanceAndParsing() {
		UserEntity user = new UserEntity("jwt-test@peergeek.com", passwordEncoder.encode("pass123"), "JWT Tester", "jwttester", true);
		user.setId(999L);
		user.setRole(UserRole.USER);

		String token = jwtService.issue(user);
		assertNotNull(token);

		JwtService.TokenClaims claims = jwtService.parse(token);
		assertEquals(999L, claims.userId());
		assertEquals("USER", claims.role());
	}

	@Test
	void testUserRegistrationAndOutboxEvent() {
		RegisterRequest request = new RegisterRequest(
				"newstudent@peergeek.com",
				"StudentPass123!",
				"New Student",
				"newstudent",
				null);

		RegisterResponse response = authService.register(request);
		assertNotNull(response);
		assertEquals("newstudent@peergeek.com", response.email());
		assertTrue(response.requiresVerification());

		Optional<UserEntity> userOpt = userRepository.findByEmail("newstudent@peergeek.com");
		assertTrue(userOpt.isPresent());
		UserEntity user = userOpt.get();
		assertFalse(user.isEmailVerified());
		assertEquals("newstudent", user.getUsername());

		// Verify Outbox Event created
		var outboxEvents = outboxEventRepository.findAll();
		boolean found = outboxEvents.stream().anyMatch(e ->
				e.getTopic().equals("peergeek.identity.user-registered.v1") &&
				e.getPayload().contains("newstudent@peergeek.com"));
		assertTrue(found, "Outbox event for user-registered must be persisted in same transaction");
	}

	@Test
	void testEmailVerificationAndLoginFlow() {
		UserEntity user = new UserEntity("login-test@peergeek.com", passwordEncoder.encode("SecretPass123!"), "Login Test", "logintest", false);
		user = userRepository.saveAndFlush(user);

		// Prepare registration OTP
		AuthOtpEntity otpEntity = new AuthOtpEntity(
				user.getEmail(), user, OtpPurpose.REGISTRATION, passwordEncoder.encode("123456"), Instant.now().plusSeconds(600));
		authOtpRepository.saveAndFlush(otpEntity);

		// Verify email
		VerifyEmailRequest verifyRequest = new VerifyEmailRequest("login-test@peergeek.com", "123456");
		GenericMessageResponse verifyRes = authService.verifyEmail(verifyRequest);
		assertNotNull(verifyRes);

		UserEntity verifiedUser = userRepository.findById(user.getId()).orElseThrow();
		assertTrue(verifiedUser.isEmailVerified());

		// Login
		LoginRequest loginRequest = new LoginRequest("login-test@peergeek.com", "SecretPass123!");
		AuthService.IssuedSession session = authService.login(loginRequest, "TestAgent", "127.0.0.1");
		assertNotNull(session);
		assertNotNull(session.accessToken());
		assertNotNull(session.refreshToken());

		// Refresh
		AuthService.IssuedSession refreshed = authService.refresh(session.refreshToken(), "TestAgent", "127.0.0.1");
		assertNotNull(refreshed);
		assertNotNull(refreshed.accessToken());
		assertNotEquals(session.refreshToken(), refreshed.refreshToken());
	}

	@Test
	void testPasswordResetFlow() {
		UserEntity user = new UserEntity("reset-test@peergeek.com", passwordEncoder.encode("OldPass123!"), "Reset Test", "resettest", true);
		user = userRepository.saveAndFlush(user);

		// Request reset
		GenericMessageResponse resetReqRes = authService.requestPasswordReset("reset-test@peergeek.com");
		assertNotNull(resetReqRes);

		// Verify OTP was stored
		var otps = authOtpRepository.findAllByEmailAndPurposeAndConsumedAtIsNull("reset-test@peergeek.com", OtpPurpose.PASSWORD_RESET);
		assertFalse(otps.isEmpty());

		// Simulate user inputting OTP
		AuthOtpEntity otp = otps.getFirst();
		// Set a known hash
		otp = new AuthOtpEntity("reset-test@peergeek.com", user, OtpPurpose.PASSWORD_RESET, passwordEncoder.encode("654321"), Instant.now().plusSeconds(600));
		otp = authOtpRepository.saveAndFlush(otp);

		VerifyResetOtpRequest verifyOtpReq = new VerifyResetOtpRequest("reset-test@peergeek.com", "654321");
		VerifyResetOtpResponse verifyOtpRes = authService.verifyResetOtp(verifyOtpReq);
		assertNotNull(verifyOtpRes);
		assertNotNull(verifyOtpRes.resetToken());

		// Reset password
		ResetPasswordRequest resetPassReq = new ResetPasswordRequest(verifyOtpRes.resetToken(), "NewPass123!");
		GenericMessageResponse finalRes = authService.resetPassword(resetPassReq);
		assertNotNull(finalRes);

		UserEntity updatedUser = userRepository.findById(user.getId()).orElseThrow();
		assertTrue(passwordEncoder.matches("NewPass123!", updatedUser.getPasswordHash()));
	}
}
