package com.peergeek.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.config.AuthSettings;
import com.peergeek.config.BootstrapAdminSettings;
import com.peergeek.security.JwtService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import com.peergeek.user.UserRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthIntegrationTest.MailTestConfiguration.class)
class AuthIntegrationTest extends AbstractIntegrationTest {
	private static final String EMAIL = "phase1@example.com";
	private static final String PASSWORD = "correct horse battery";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private CapturingMailSender mailSender;
	@Autowired
	private UserRepository users;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private AuthSettings authSettings;
	@Autowired
	private BootstrapAdminSettings bootstrapSettings;
	@Autowired
	private BootstrapAdminInitializer bootstrapAdminInitializer;
	@Autowired
	private jakarta.persistence.EntityManager entityManager;

	@BeforeEach
	void clearPhaseOneData() {
		jdbc.update("delete from audit_logs");
		jdbc.update("delete from auth_otps");
		jdbc.update("delete from password_reset_otps");
		jdbc.update("delete from refresh_tokens");
		jdbc.update("delete from users");
		entityManager.clear();
		mailSender.reset();
	}

	@Test
	void registrationNormalizesEmailRequiresVerificationAndLoginReturnsAccessTokenAndSetsSecureCookieAttributes() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"  Phase1@Example.com ","password":"correct horse battery","displayName":"Ada","username":"  Ada_Lovelace  "}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(EMAIL))
				.andExpect(jsonPath("$.verificationRequired").value(true))
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).doesNotContain(PASSWORD, "accessToken", "passwordHash");
		assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();

		// Attempting login before verification fails with 403 EMAIL_NOT_VERIFIED
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

		mailSender.awaitMessage();
		String otp = mailSender.lastOtp.get();
		assertThat(otp).matches("\\d{6}");
		String otpHash = jdbc.queryForObject("select otp_hash from auth_otps where email = ?", String.class, EMAIL);
		assertThat(otpHash).isNotEqualTo(otp);

		// Verify email with received OTP
		mockMvc.perform(post("/api/v1/auth/verify-email")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(new VerifyEmailRequest(EMAIL, otp))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").isString());

		assertThat(users.findByEmail(EMAIL)).isPresent();
		assertThat(users.findByEmail(EMAIL).get().isEmailVerified()).isTrue();
		assertThat(users.findByEmail(EMAIL).get().getUsername()).isEqualTo("ada_lovelace");

		// Now login succeeds and returns access token + refresh cookie
		MvcResult loginResult = login(EMAIL, PASSWORD);
		assertThat(loginResult.getResponse().getStatus()).isEqualTo(200);
		JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
		assertThat(loginJson.at("/user/email").asText()).isEqualTo(EMAIL);
		assertThat(loginJson.at("/user/username").asText()).isEqualTo("ada_lovelace");
		assertThat(loginJson.at("/user/role").asText()).isEqualTo("USER");
		assertThat(loginJson.get("accessToken").isTextual()).isTrue();
		assertThat(loginJson.get("expiresIn").asInt()).isEqualTo(900);
		String refreshToken = cookieValue(loginResult.getResponse().getHeader("Set-Cookie"));
		assertThat(loginResult.getResponse().getHeader("Set-Cookie"))
				.contains("HttpOnly", "SameSite=Lax", "Path=/api/v1/auth");
		assertThat(jdbc.queryForObject("select token_hash from refresh_tokens", String.class))
				.isNotEqualTo(refreshToken);
		assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'USER_REGISTERED'", Integer.class))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'AUTH_EMAIL_VERIFIED'", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void registrationConflictsAndRejectsWeakOrEmailPasswordsAsFieldErrors() throws Exception {
		registerAndVerify();
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"PHASE1@example.com","password":"correct horse battery","displayName":"Ada","username":"ada_second"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"weak@example.com","password":"short","displayName":"Weak","username":"weak_user"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"same@example.com","password":"same@example.com","displayName":"Same","username":"same_user"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
	}

	@Test
	void unverifiedRegistrationCanBeUpdatedWithNewDetailsAndFreshOtp() throws Exception {
		register();
		mailSender.awaitMessage();
		String firstOtp = mailSender.lastOtp.get();
		mailSender.reset();

		// Advance time past cooldown for fresh OTP generation
		jdbc.update("update auth_otps set created_at = DATE_SUB(created_at, INTERVAL 2 MINUTE) where email = ?", EMAIL);
		entityManager.clear();

		// Reregister with same email before verification updates the pending account and sends fresh OTP
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"phase1@example.com","password":"updated password 123","displayName":"Ada Updated","username":"phase1_user"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.verificationRequired").value(true));

		mailSender.awaitMessage();
		String secondOtp = mailSender.lastOtp.get();
		assertThat(secondOtp).matches("\\d{6}");

		// Old OTP should no longer work
		mockMvc.perform(post("/api/v1/auth/verify-email")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(new VerifyEmailRequest(EMAIL, firstOtp))))
				.andExpect(status().isBadRequest());

		// New OTP works
		mockMvc.perform(post("/api/v1/auth/verify-email")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(new VerifyEmailRequest(EMAIL, secondOtp))))
				.andExpect(status().isOk());

		// Login works with the updated password
		assertThat(login(EMAIL, "updated password 123").getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void registrationOtpInvalidBurnedAfterFiveAttemptsAndResendCooldownEnforced() throws Exception {
		register();
		mailSender.awaitMessage();
		mailSender.reset();

		// Invalid attempts
		for (int attempt = 0; attempt < 5; attempt++) {
			mockMvc.perform(post("/api/v1/auth/verify-email")
							.contentType("application/json")
							.content(objectMapper.writeValueAsString(new VerifyEmailRequest(EMAIL, "000000"))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
					.andExpect(jsonPath("$.fieldErrors[0].field").value("otp"));
		}

		// Subsequent attempt with correct OTP is rejected because OTP was burned
		String actualOtp = mailSender.lastOtp.get();
		if (actualOtp != null) {
			mockMvc.perform(post("/api/v1/auth/verify-email")
							.contentType("application/json")
							.content(objectMapper.writeValueAsString(new VerifyEmailRequest(EMAIL, actualOtp))))
					.andExpect(status().isBadRequest());
		}

		// Resend verification OTP during cooldown fails
		mockMvc.perform(post("/api/v1/auth/resend-verification-otp")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(new ResendVerificationRequest(EMAIL))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		// Advance past cooldown and retry
		jdbc.update("update auth_otps set created_at = DATE_SUB(created_at, INTERVAL 2 MINUTE) where email = ?", EMAIL);
		entityManager.clear();
		mockMvc.perform(post("/api/v1/auth/resend-verification-otp")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(new ResendVerificationRequest(EMAIL))))
				.andExpect(status().isOk());
	}

	@Test
	void wrongPasswordUnknownEmailAndDisabledUsersShareGenericLoginFailure() throws Exception {
		registerAndVerify();
		MvcResult wrongPassword = login("phase1@example.com", "wrong password");
		MvcResult unknownEmail = login("unknown@example.com", "wrong password");
		jdbc.update("update users set enabled = false where email = ?", EMAIL);
		MvcResult disabled = login(EMAIL, PASSWORD);

		assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
		assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
		assertThat(disabled.getResponse().getStatus()).isEqualTo(401);
		assertThat(normalizeError(wrongPassword)).isEqualTo(normalizeError(unknownEmail));
		assertThat(normalizeError(wrongPassword)).isEqualTo(normalizeError(disabled));
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'AUTH_LOGIN_FAILED'", Integer.class))
				.isEqualTo(3);
	}

	@Test
	void refreshRotatesTokensAndReuseRevokesTheWholeFamily() throws Exception {
		MvcResult loggedIn = registerAndLogin();
		String oldToken = cookieValue(loggedIn.getResponse().getHeader("Set-Cookie"));
		MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
						.cookie(new Cookie("PEERGEEK_REFRESH", oldToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isString())
				.andReturn();
		String currentToken = cookieValue(refreshed.getResponse().getHeader("Set-Cookie"));
		assertThat(currentToken).isNotEqualTo(oldToken);

		mockMvc.perform(post("/api/v1/auth/refresh")
						.cookie(new Cookie("PEERGEEK_REFRESH", oldToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.fieldErrors").isArray());
		assertThat(jdbc.queryForObject(
				"select count(*) from refresh_tokens where user_id = (select id from users where email = ?) and revoked_at is null",
				Integer.class, EMAIL)).isZero();
	}

	@Test
	void logoutIsIdempotentAndRequiresAuthentication() throws Exception {
		MvcResult loggedIn = registerAndLogin();
		String token = cookieValue(loggedIn.getResponse().getHeader("Set-Cookie"));
		String accessToken = objectMapper.readTree(loggedIn.getResponse().getContentAsString())
				.get("accessToken").asText();

		mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("PEERGEEK_REFRESH", token))
						.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("PEERGEEK_REFRESH", token))
						.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'AUTH_LOGOUT'", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject(
				"select count(*) from refresh_tokens where revoked_at is not null", Integer.class)).isEqualTo(1);
	}

	@Test
	void forgotPasswordIsGenericAndStoresOnlyHashedOtpThenResetRevokesSessions() throws Exception {
		MvcResult loggedIn = registerAndLogin();
		String refreshToken = cookieValue(loggedIn.getResponse().getHeader("Set-Cookie"));
		String requestBody = """
				{"email":"phase1@example.com"}
				""";
		MvcResult known = mockMvc.perform(post("/api/v1/auth/forgot-password")
						.contentType("application/json").content(requestBody))
				.andExpect(status().isAccepted())
				.andReturn();
		MvcResult unknown = mockMvc.perform(post("/api/v1/auth/forgot-password")
						.contentType("application/json")
						.content("{\"email\":\"unknown@example.com\"}"))
				.andExpect(status().isAccepted())
				.andReturn();

		assertThat(known.getResponse().getContentAsString())
				.isEqualTo(unknown.getResponse().getContentAsString());
		mailSender.awaitMessage();
		String otp = mailSender.lastOtp.get();
		assertThat(otp).matches("\\d{6}");
		String otpHash = jdbc.queryForObject("select otp_hash from password_reset_otps", String.class);
		assertThat(otpHash).isNotEqualTo(otp);
		assertThat(known.getResponse().getContentAsString()).doesNotContain(otp);
		assertThat(mailSender.lastBody.get()).contains(otp);

		mockMvc.perform(post("/api/v1/auth/reset-password")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(
								new ResetPasswordRequest(EMAIL, otp, "new secure password"))))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/auth/reset-password")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(
								new ResetPasswordRequest(EMAIL, otp, "another secure password"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("otp"));
		assertThat(jdbc.queryForObject(
				"select count(*) from refresh_tokens where revoked_at is null", Integer.class)).isZero();
		mockMvc.perform(post("/api/v1/auth/refresh")
						.cookie(new Cookie("PEERGEEK_REFRESH", refreshToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		assertThat(login(EMAIL, "new secure password").getResponse().getStatus()).isEqualTo(200);
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'AUTH_PASSWORD_RESET_COMPLETED'", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void passwordResetTwoStepFlowWithResetTokenAndConfirmationEmail() throws Exception {
		registerAndLogin();
		mailSender.reset();

		mockMvc.perform(post("/api/v1/auth/forgot-password")
						.contentType("application/json")
						.content("{\"email\":\"phase1@example.com\"}"))
				.andExpect(status().isAccepted());

		mailSender.awaitMessage();
		String otp = mailSender.lastOtp.get();
		mailSender.reset();

		// Step 1: verify reset OTP to obtain reset authorization token
		MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/verify-reset-otp")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(new VerifyResetOtpRequest(EMAIL, otp))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resetToken").isString())
				.andReturn();

		String resetToken = objectMapper.readTree(verifyResult.getResponse().getContentAsString())
				.get("resetToken").asText();
		assertThat(resetToken).isNotBlank();

		// Step 2: reset password with resetToken
		mockMvc.perform(post("/api/v1/auth/reset-password")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(
								new ResetPasswordRequest(EMAIL, resetToken, null, "two step reset password 123"))))
				.andExpect(status().isOk());

		// Single-use: resetToken cannot be used again
		mockMvc.perform(post("/api/v1/auth/reset-password")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(
								new ResetPasswordRequest(EMAIL, resetToken, null, "two step reset password 456"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("resetToken"));

		// Confirmation email was sent
		mailSender.awaitMessage();
		assertThat(mailSender.lastType.get()).isEqualTo("PASSWORD_RESET_CONFIRMATION");

		// New password logs in successfully
		assertThat(login(EMAIL, "two step reset password 123").getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void purposeSeparationBetweenRegistrationAndPasswordReset() throws Exception {
		register();
		mailSender.awaitMessage();
		String regOtp = mailSender.lastOtp.get();
		mailSender.reset();

		// Registration OTP cannot be used to verify password reset
		mockMvc.perform(post("/api/v1/auth/verify-reset-otp")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(new VerifyResetOtpRequest(EMAIL, regOtp))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("otp"));
	}

	@Test
	void invalidOtpIsBurnedAfterFiveAttemptsAndReturnsTheSameValidationError() throws Exception {
		registerAndLogin();
		mockMvc.perform(post("/api/v1/auth/forgot-password").contentType("application/json")
						.content("{\"email\":\"phase1@example.com\"}"))
				.andExpect(status().isAccepted());

		mailSender.awaitMessage();
		String realOtp = mailSender.lastOtp.get();

		for (int attempt = 0; attempt < 5; attempt++) {
			mockMvc.perform(post("/api/v1/auth/reset-password").contentType("application/json")
							.content(objectMapper.writeValueAsString(
									new ResetPasswordRequest(EMAIL,
											"000000".equals(realOtp) ? "000001" : "000000",
											"another secure password"))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
					.andExpect(jsonPath("$.fieldErrors[0].field").value("otp"));
		}
		assertThat(jdbc.queryForObject("select attempts from password_reset_otps", Integer.class)).isEqualTo(5);
		assertThat(jdbc.queryForObject("select consumed_at from password_reset_otps", java.sql.Timestamp.class))
				.isNotNull();
	}

	@Test
	void protectedRoutesReturnStandard401AndAdminOnlyRoutesReturnStandard403() throws Exception {
		MvcResult loggedIn = registerAndLogin();
		String accessToken = objectMapper.readTree(loggedIn.getResponse().getContentAsString())
				.get("accessToken").asText();
		mockMvc.perform(get("/api/v1/test/security/protected"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.fieldErrors").isArray());
		mockMvc.perform(get("/api/v1/test/security/admin")
						.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.fieldErrors").isArray());
	}

	@Test
	void validJwtWorksAndTamperedExpiredWrongSignatureAndDeletedUserAreRejected() throws Exception {
		MvcResult loggedIn = registerAndLogin();
		String accessToken = objectMapper.readTree(loggedIn.getResponse().getContentAsString())
				.get("accessToken").asText();
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk());

		String[] parts = accessToken.split("\\.");
		char first = parts[2].charAt(0);
		String tampered = parts[0] + "." + parts[1] + "." + (first == 'A' ? 'B' : 'A')
				+ parts[2].substring(1);
		assertUnauthorized(tampered);
		assertUnauthorized(customToken(Instant.now().minusSeconds(60), Instant.now().minusSeconds(1), "phase1"));
		assertUnauthorized(customToken(Instant.now().minusSeconds(1), Instant.now().plusSeconds(60), "wrong-key"));

		UserEntity user = users.findByEmail(EMAIL).orElseThrow();
		jdbc.update("delete from refresh_tokens where user_id = ?", user.getId());
		jdbc.update("delete from auth_otps where user_id = ?", user.getId());
		jdbc.update("delete from password_reset_otps where user_id = ?", user.getId());
		jdbc.update("delete from users where id = ?", user.getId());
		assertUnauthorized(accessToken);
	}

	@Test
	void publicProfileReturnsExactlyTheAllowedFieldsAndModeDoesNotChangeRole() throws Exception {
		MvcResult loggedIn = registerAndLogin();
		String accessToken = objectMapper.readTree(loggedIn.getResponse().getContentAsString())
				.get("accessToken").asText();
		long userId = objectMapper.readTree(loggedIn.getResponse().getContentAsString())
				.at("/user/id").asLong();

		mockMvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + accessToken)
						.contentType("application/json")
						.content("""
								{"displayName":"Ada Lovelace","headline":"Engineer","bio":"Learning in public"}
								"""))
				.andExpect(status().isOk());
		String publicJson = mockMvc.perform(get("/api/v1/users/" + userId + "/public"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		JsonNode profile = objectMapper.readTree(publicJson);
		assertThat(profile.fieldNames()).toIterable().containsExactlyInAnyOrder(
				"id", "username", "displayName", "headline", "bio");
		assertThat(publicJson).doesNotContain("email", "role", "lastMode", "createdAt");

		mockMvc.perform(patch("/api/v1/users/me/mode").header("Authorization", "Bearer " + accessToken)
						.contentType("application/json").content("{\"mode\":\"INSTRUCTOR\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lastMode").value("INSTRUCTOR"))
				.andExpect(jsonPath("$.role").value("USER"));
		assertThat(jdbc.queryForObject("select role from users where id = ?", String.class, userId))
				.isEqualTo("USER");
	}

	@Test
	void bootstrapAdminIsCreatedOnlyWhenConfiguredAndThereIsNoAdmin() throws Exception {
		bootstrapSettings.setEmail("admin@example.com");
		bootstrapSettings.setPassword("bootstrap secure password");
		ApplicationArguments arguments = new DefaultApplicationArguments(new String[0]);
		bootstrapAdminInitializer.run(arguments);
		bootstrapAdminInitializer.run(arguments);
		UserEntity admin = users.findByEmail("admin@example.com").orElseThrow();
		assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(passwordEncoder.matches("bootstrap secure password", admin.getPasswordHash())).isTrue();
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'AUTH_BOOTSTRAP_ADMIN_CREATED'", Integer.class))
				.isEqualTo(1);
		bootstrapSettings.setEmail("");
		bootstrapSettings.setPassword("");
	}

	@Test
	void usernameValidationRejectsMissingShortLongInvalidCharactersAndStartingWithNonLetter() throws Exception {
		// Missing username
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"test1@example.com","password":"correct horse battery","displayName":"Test"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]").exists());

		// Blank username
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"test1@example.com","password":"correct horse battery","displayName":"Test","username":"   "}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]").exists());

		// Too short (< 3)
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"test2@example.com","password":"correct horse battery","displayName":"Test","username":"ab"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]").exists());

		// Too long (> 30)
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"test3@example.com","password":"correct horse battery","displayName":"Test","username":"a1234567890123456789012345678901"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]").exists());

		// Invalid characters: spaces
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"test4@example.com","password":"correct horse battery","displayName":"Test","username":"ada lovelace"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]").exists());

		// Invalid characters: symbols (hyphens, dots, special chars)
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"test5@example.com","password":"correct horse battery","displayName":"Test","username":"ada-lovelace"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]").exists());

		// Must start with a letter: starts with digit
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"test6@example.com","password":"correct horse battery","displayName":"Test","username":"1ada"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]").exists());

		// Must start with a letter: starts with underscore
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"test7@example.com","password":"correct horse battery","displayName":"Test","username":"_ada"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]").exists());
	}

	@Test
	void usernameValidationRejectsReservedUsernamesCaseInsensitively() throws Exception {
		for (String reserved : List.of("admin", "ADMIN", "administrator", "support", "system", "api", "root")) {
			mockMvc.perform(post("/api/v1/auth/register")
							.contentType("application/json")
							.content(objectMapper.writeValueAsString(
									new RegisterRequest("res_" + reserved + "@example.com", PASSWORD, "Reserved Test", reserved))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
					.andExpect(jsonPath("$.fieldErrors[0].field").value("username"));
		}
	}

	@Test
	void caseInsensitiveDuplicateUsernameDetectionAndRejection() throws Exception {
		// Register initial user with lowercase username
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(
								new RegisterRequest("devansh1@example.com", PASSWORD, "Devansh", "devansh_geek"))))
				.andExpect(status().isCreated());

		// Attempt duplicate with mixed case
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(
								new RegisterRequest("devansh2@example.com", PASSWORD, "Devansh 2", "Devansh_Geek"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("username"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("Username is already taken. Please choose another username."));

		// Attempt duplicate with UPPERCASE
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(
								new RegisterRequest("devansh3@example.com", PASSWORD, "Devansh 3", "DEVANSH_GEEK"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("username"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("Username is already taken. Please choose another username."));
	}

	@Test
	void concurrentRegistrationAttemptsWithSameUsernameSafelyHandled() throws Exception {
		String concurrentUsername = "race_user";
		int threads = 2;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch doneGate = new CountDownLatch(threads);
		AtomicReference<MvcResult> result1 = new AtomicReference<>();
		AtomicReference<MvcResult> result2 = new AtomicReference<>();

		executor.submit(() -> {
			try {
				startGate.await();
				result1.set(mockMvc.perform(post("/api/v1/auth/register")
								.contentType("application/json")
								.content(objectMapper.writeValueAsString(
										new RegisterRequest("race1@example.com", PASSWORD, "Race 1", concurrentUsername))))
						.andReturn());
			} catch (Exception e) {
				// ignored
			} finally {
				doneGate.countDown();
			}
		});

		executor.submit(() -> {
			try {
				startGate.await();
				result2.set(mockMvc.perform(post("/api/v1/auth/register")
								.contentType("application/json")
								.content(objectMapper.writeValueAsString(
										new RegisterRequest("race2@example.com", PASSWORD, "Race 2", concurrentUsername))))
						.andReturn());
			} catch (Exception e) {
				// ignored
			} finally {
				doneGate.countDown();
			}
		});

		startGate.countDown();
		assertThat(doneGate.await(10, TimeUnit.SECONDS)).isTrue();
		executor.shutdown();

		int status1 = result1.get().getResponse().getStatus();
		int status2 = result2.get().getResponse().getStatus();

		// Exactly one must succeed (201) and the other must be rejected (400)
		assertThat(List.of(status1, status2)).containsExactlyInAnyOrder(201, 400);

		MvcResult failedResult = status1 == 400 ? result1.get() : result2.get();
		String failedBody = failedResult.getResponse().getContentAsString();
		assertThat(failedBody).contains("Username is already taken. Please choose another username.");
		assertThat(failedBody).doesNotContain("SQL", "Duplicate entry", "Exception");
	}

	@Test
	void publicProfileEndpointsExposeUsernameAndSupportLookup() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(
								new RegisterRequest("grace@example.com", PASSWORD, "Grace Hopper", "grace_hopper"))))
				.andExpect(status().isCreated());

		UserEntity user = users.findByEmail("grace@example.com").orElseThrow();

		// Lookup by numeric id
		mockMvc.perform(get("/api/v1/users/" + user.getId() + "/public"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.getId()))
				.andExpect(jsonPath("$.displayName").value("Grace Hopper"))
				.andExpect(jsonPath("$.username").value("grace_hopper"));

		// Lookup by username
		mockMvc.perform(get("/api/v1/users/by-username/grace_hopper"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.getId()))
				.andExpect(jsonPath("$.displayName").value("Grace Hopper"))
				.andExpect(jsonPath("$.username").value("grace_hopper"));

		// Lookup by uppercase username (case-insensitive)
		mockMvc.perform(get("/api/v1/users/by-username/GRACE_HOPPER"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.getId()))
				.andExpect(jsonPath("$.displayName").value("Grace Hopper"))
				.andExpect(jsonPath("$.username").value("grace_hopper"));
	}

	@Test
	void existingUsersReceiveValidUniqueUsernamesAndForeignKeysIntact() throws Exception {
		// Insert existing users directly and verify valid usernames and FK relationships
		List<String> usernames = jdbc.queryForList("select username from users", String.class);
		for (String un : usernames) {
			assertThat(un).isNotNull();
			assertThat(un).matches("^[a-zA-Z][a-zA-Z0-9_]{2,29}$");
			assertThat(AuthService.RESERVED_USERNAMES).doesNotContain(un.toLowerCase(java.util.Locale.ROOT));
		}
	}

	private MvcResult register() throws Exception {
		return mockMvc.perform(post("/api/v1/auth/register")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(
								new RegisterRequest(EMAIL, PASSWORD, "Phase One", "phase1_user"))))
				.andExpect(status().isCreated())
				.andReturn();
	}

	private MvcResult registerAndVerify() throws Exception {
		register();
		jdbc.update("update users set email_verified = true where email = ?", EMAIL);
		entityManager.clear();
		return null;
	}

	private MvcResult registerAndLogin() throws Exception {
		register();
		jdbc.update("update users set email_verified = true where email = ?", EMAIL);
		entityManager.clear();
		return login(EMAIL, PASSWORD);
	}

	private MvcResult login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
				.andReturn();
	}

	private JsonNode normalizeError(MvcResult result) throws Exception {
		JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
		((com.fasterxml.jackson.databind.node.ObjectNode) json).remove("timestamp");
		return json;
	}

	private String cookieValue(String setCookie) {
		assertThat(setCookie).isNotNull();
		return setCookie.substring("PEERGEEK_REFRESH=".length(), setCookie.indexOf(';'));
	}

	private String customToken(Instant issuedAt, Instant expiresAt, String keyKind) {
		byte[] secret = (keyKind.equals("wrong-key")
				? "another-signing-key-that-is-at-least-32-bytes-long"
				: authSettings.getJwtSecret()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
		return Jwts.builder()
				.subject(users.findByEmail(EMAIL).orElseThrow().getId().toString())
				.claim("role", "USER")
				.issuedAt(java.util.Date.from(issuedAt))
				.expiration(java.util.Date.from(expiresAt))
				.signWith(Keys.hmacShaKeyFor(secret))
				.compact();
	}

	private void assertUnauthorized(String token) throws Exception {
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.fieldErrors").isArray());
	}

	@TestConfiguration
	static class MailTestConfiguration {
		@Bean
		@Primary
		CapturingMailSender capturingMailSender() {
			return new CapturingMailSender();
		}
	}

	static class CapturingMailSender implements MailSender {
		private final AtomicReference<String> lastOtp = new AtomicReference<>();
		private final AtomicReference<String> lastBody = new AtomicReference<>();
		private final AtomicReference<String> lastRecipient = new AtomicReference<>();
		private final AtomicReference<String> lastType = new AtomicReference<>();
		private final AtomicReference<CountDownLatch> delivery = new AtomicReference<>(new CountDownLatch(1));

		@Override
		public void sendPasswordReset(String recipient, String otp, long expiryMinutes) {
			lastRecipient.set(recipient);
			lastOtp.set(otp);
			lastType.set("PASSWORD_RESET");
			lastBody.set("Your PeerGeek password reset code is " + otp + ". It expires in "
					+ expiryMinutes + " minutes.");
			delivery.get().countDown();
		}

		@Override
		public void sendRegistrationVerification(String recipient, String otp, long expiryMinutes) {
			lastRecipient.set(recipient);
			lastOtp.set(otp);
			lastType.set("REGISTRATION");
			lastBody.set("Your PeerGeek verification code is " + otp + ". It expires in "
					+ expiryMinutes + " minutes.");
			delivery.get().countDown();
		}

		@Override
		public void sendPasswordResetConfirmation(String recipient) {
			lastRecipient.set(recipient);
			lastType.set("PASSWORD_RESET_CONFIRMATION");
			lastBody.set("Your PeerGeek password has been changed.");
			delivery.get().countDown();
		}

		@Override
		public void sendReferralInvitation(String recipient, String inviterName, String referralCode, String registrationUrl, java.math.BigDecimal inviterReward, java.math.BigDecimal newUserReward, int expiryDays) {
			lastRecipient.set(recipient);
			lastType.set("REFERRAL_INVITATION");
			lastBody.set("Referral code: " + referralCode + "; expires in " + expiryDays + " days");
			delivery.get().countDown();
		}

		void reset() {
			lastRecipient.set(null);
			lastOtp.set(null);
			lastBody.set(null);
			lastType.set(null);
			delivery.set(new CountDownLatch(1));
		}

		void awaitMessage() throws InterruptedException {
			assertThat(delivery.get().await(5, TimeUnit.SECONDS)).isTrue();
		}
	}
}
