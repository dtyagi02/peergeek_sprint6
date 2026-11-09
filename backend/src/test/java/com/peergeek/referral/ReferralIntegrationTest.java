package com.peergeek.referral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.AbstractIntegrationTest;
import com.peergeek.exception.BadRequestException;
import com.peergeek.notification.NotificationEntity;
import com.peergeek.notification.NotificationRepository;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import com.peergeek.wallet.WalletRepository;
import com.peergeek.wallet.WalletService;
import com.peergeek.wallet.WalletTransactionEntity;
import com.peergeek.wallet.WalletTransactionRepository;
import com.peergeek.wallet.WalletTransactionType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReferralIntegrationTest.FailureInjectingWalletConfiguration.class)
class ReferralIntegrationTest extends AbstractIntegrationTest {
	private static final String PASSWORD = "referral integration password";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private WalletRepository walletRepository;
	@Autowired
	private WalletTransactionRepository transactionRepository;
	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private ReferralRepository referralRepository;
	@Autowired
	private ReferralService referralService;
	@Autowired
	private FailureInjectingWalletService failureInjectingWalletService;

	@DynamicPropertySource
	static void mailpitProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.mail.host", () -> "localhost");
		registry.add("spring.mail.port", () -> 1025);
		registry.add("spring.mail.properties.mail.smtp.auth", () -> false);
	}

	@BeforeEach
	void verifyMailpitIsAvailable() throws Exception {
		failureInjectingWalletService.disableFailure();
		HttpResponse<String> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:8025/api/v1/messages"))
						.timeout(Duration.ofSeconds(3)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
	}

	@Test
	void referralRegistrationVerificationRewardsBothWalletsExactlyOnce() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		String inviterEmail = "referrer-" + suffix + "@example.test";
		String referredEmail = "referred-" + suffix + "@example.test";
		String inviterUsername = "referrer_" + suffix;
		String referredUsername = "referred_" + suffix;

		UserEntity inviter = userRepository.saveAndFlush(new UserEntity(
				inviterEmail, passwordEncoder.encode(PASSWORD), "Referral Inviter", inviterUsername, true));
		walletRepository.findByUserId(inviter.getId()).orElseGet(
				() -> walletRepository.saveAndFlush(new com.peergeek.wallet.WalletEntity(
						inviter, BigDecimal.ZERO, "PGC")));

		String inviterToken = login(inviterEmail);
		long mailCountBeforeInvite = mailpitMessageCount();
		String inviteResponse = mockMvc.perform(post("/api/v1/referrals/invite")
						.header("Authorization", "Bearer " + inviterToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + referredEmail + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String referralCode = objectMapper.readTree(inviteResponse).get("referralCode").asText();

		JsonNode invitation = awaitMail(referredEmail, mailCountBeforeInvite + 1, referralCode);
		String invitationText = invitation.get("Text").asText();
		assertThat(invitationText).contains(
				referralCode, "http://localhost:5173/register?ref=",
				"100 GeekCoins", "50 GeekCoins", "7 days");
		assertThat(invitation.get("To").toString()).contains(referredEmail);

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%s","displayName":"Referral New User",
								"username":"%s","referralCode":"%s"}
								""".formatted(referredEmail, PASSWORD, referredUsername, referralCode)))
				.andExpect(status().isCreated());

		JsonNode verification = awaitMail(referredEmail, mailCountBeforeInvite + 2, "verification code is");
		String otp = verification.get("Text").asText().replaceAll("(?s).*verification code is (\\d{6}).*", "$1");
		assertThat(otp).matches("\\d{6}");

		mockMvc.perform(post("/api/v1/auth/verify-email")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + referredEmail + "\",\"otp\":\"" + otp + "\"}"))
				.andExpect(status().isOk());

		UserEntity referred = userRepository.findByEmail(referredEmail).orElseThrow();
		ReferralEntity referral = referralRepository.findByReferralCode(referralCode).orElseThrow();
		assertThat(referral.getStatus()).isEqualTo(ReferralStatus.REWARDED);
		assertThat(walletRepository.findByUserId(inviter.getId()).orElseThrow().getBalance())
				.isEqualByComparingTo("100.00");
		assertThat(walletRepository.findByUserId(referred.getId()).orElseThrow().getBalance())
				.isEqualByComparingTo("50.00");

		List<WalletTransactionEntity> transactions =
				transactionRepository.findByReferenceTypeAndReferenceId("REFERRAL", referral.getId().toString());
		assertThat(transactions).extracting(WalletTransactionEntity::getType)
				.containsExactlyInAnyOrder(
						WalletTransactionType.REFERRAL_INVITER_REWARD,
						WalletTransactionType.REFERRAL_NEW_USER_REWARD);
		List<NotificationEntity> notifications = notificationRepository.findAll().stream()
				.filter(n -> referral.getId().equals(n.getResourceId()))
				.toList();
		assertThat(notifications).hasSize(2);

		mockMvc.perform(post("/api/v1/auth/verify-email")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + referredEmail + "\",\"otp\":\"" + otp + "\"}"))
				.andExpect(status().isBadRequest());
		assertThat(walletRepository.findByUserId(inviter.getId()).orElseThrow().getBalance())
				.isEqualByComparingTo("100.00");
		assertThat(walletRepository.findByUserId(referred.getId()).orElseThrow().getBalance())
				.isEqualByComparingTo("50.00");
		assertThat(transactionRepository.findByReferenceTypeAndReferenceId("REFERRAL", referral.getId().toString()))
				.hasSize(2);
	}

	@Test
	void failedSecondWalletCreditRollsBackTheFirstCreditAndNotifications() {
		TestReferralData data = createRegisteredReferral();
		failureInjectingWalletService.enableFailure();

		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> referralService.completeReferralReward(userRepository.findById(data.referredId).orElseThrow()))
				.isInstanceOf(RuntimeException.class);

		assertThat(walletRepository.findByUserId(data.inviterId).orElseThrow().getBalance())
				.isEqualByComparingTo("10.00");
		assertThat(walletRepository.findByUserId(data.referredId).orElseThrow().getBalance())
				.isEqualByComparingTo("20.00");
		assertThat(transactionRepository.findByReferenceTypeAndReferenceId("REFERRAL", data.referralId.toString()))
				.isEmpty();
		assertThat(referralRepository.findById(data.referralId).orElseThrow().getStatus())
				.isEqualTo(ReferralStatus.REGISTERED);
		assertThat(notificationRepository.findAll().stream()
				.filter(n -> data.referralId.equals(n.getResourceId())).toList()).isEmpty();
	}

	@Test
	void concurrentReferralRedemptionRewardsExactlyOnce() throws Exception {
		TestReferralData data = createRegisteredReferral();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<?> first = executor.submit(() -> redeemAfter(start, data.referredId));
			Future<?> second = executor.submit(() -> redeemAfter(start, data.referredId));
			start.countDown();
			first.get();
			second.get();
		} finally {
			executor.shutdownNow();
		}

		assertThat(walletRepository.findByUserId(data.inviterId).orElseThrow().getBalance())
				.isEqualByComparingTo("110.00");
		assertThat(walletRepository.findByUserId(data.referredId).orElseThrow().getBalance())
				.isEqualByComparingTo("70.00");
		assertThat(transactionRepository.findByReferenceTypeAndReferenceId("REFERRAL", data.referralId.toString()))
				.hasSize(2);
		assertThat(notificationRepository.findAll().stream()
				.filter(n -> data.referralId.equals(n.getResourceId())).toList()).hasSize(2);
		assertThat(referralRepository.findById(data.referralId).orElseThrow().getStatus())
				.isEqualTo(ReferralStatus.REWARDED);
	}

	@Test
	void concurrentInvitationsCreateOnlyOneActiveReferral() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		UserEntity inviter = userRepository.saveAndFlush(new UserEntity(
				"invite-race-" + suffix + "@example.test",
				passwordEncoder.encode(PASSWORD), "Invite Race", "invite_race_" + suffix, true));
		walletRepository.saveAndFlush(new com.peergeek.wallet.WalletEntity(inviter, BigDecimal.ZERO, "PGC"));
		String token = login(inviter.getEmail());
		String email = "invite-target-" + suffix + "@example.test";
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Integer> first = executor.submit(() -> inviteAfter(start, token, email));
			Future<Integer> second = executor.submit(() -> inviteAfter(start, token, email));
			start.countDown();
			assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 400);
		} finally {
			executor.shutdownNow();
		}
		assertThat(referralRepository.findAllByInviter_Id(inviter.getId()).stream()
				.filter(r -> r.getInvitedEmail().equals(email) && r.getStatus() == ReferralStatus.PENDING))
				.hasSize(1);
	}

	private void redeemAfter(CountDownLatch start, Long referredId) {
		try {
			start.await();
			referralService.completeReferralReward(userRepository.findById(referredId).orElseThrow());
		} catch (BadRequestException ignored) {
			// A second redemption is safely idempotent.
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	private int inviteAfter(CountDownLatch start, String token, String email) {
		try {
			start.await();
			return mockMvc.perform(post("/api/v1/referrals/invite")
							.header("Authorization", "Bearer " + token)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"email\":\"" + email + "\"}"))
					.andReturn().getResponse().getStatus();
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	private TestReferralData createRegisteredReferral() {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		UserEntity inviter = userRepository.saveAndFlush(new UserEntity(
				"rollback-inviter-" + suffix + "@example.test",
				passwordEncoder.encode(PASSWORD), "Rollback Inviter", "rollback_inviter_" + suffix, true));
		UserEntity referred = userRepository.saveAndFlush(new UserEntity(
				"rollback-referred-" + suffix + "@example.test",
				passwordEncoder.encode(PASSWORD), "Rollback Referred", "rollback_referred_" + suffix, true));
		walletRepository.saveAndFlush(new com.peergeek.wallet.WalletEntity(inviter, new BigDecimal("10.00"), "PGC"));
		walletRepository.saveAndFlush(new com.peergeek.wallet.WalletEntity(referred, new BigDecimal("20.00"), "PGC"));
		ReferralEntity referral = new ReferralEntity(inviter, referred.getEmail(), "PG-" + suffix,
				new BigDecimal("100.00"), new BigDecimal("50.00"), 7);
		referral.markRegistered(referred, java.time.Instant.now());
		referral = referralRepository.saveAndFlush(referral);
		return new TestReferralData(inviter.getId(), referred.getId(), referral.getId());
	}

	private String login(String email) throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("accessToken").asText();
	}

	private long mailpitMessageCount() throws Exception {
		return objectMapper.readTree(mailpitMessages()).get("messages_count").asLong();
	}

	private JsonNode awaitMail(String recipient, long minimumCount, String bodyMarker) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		while (System.nanoTime() < deadline) {
			JsonNode messages = objectMapper.readTree(mailpitMessages());
			if (messages.get("messages_count").asLong() >= minimumCount) {
				for (JsonNode message : messages.get("messages")) {
					if (message.get("To").toString().contains(recipient)) {
						String id = message.get("ID").asText();
						HttpResponse<String> response = HttpClient.newHttpClient().send(
								HttpRequest.newBuilder(URI.create("http://localhost:8025/api/v1/message/" + id))
										.timeout(Duration.ofSeconds(3)).GET().build(),
								HttpResponse.BodyHandlers.ofString());
						JsonNode detail = objectMapper.readTree(response.body());
						if (detail.get("Text").asText().contains(bodyMarker)) {
							return detail;
						}
					}
				}
			}
			Thread.sleep(200);
		}
		throw new AssertionError("Mailpit did not receive a message for " + recipient);
	}

	private String mailpitMessages() throws Exception {
		return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:8025/api/v1/messages"))
						.timeout(Duration.ofSeconds(3)).GET().build(),
				HttpResponse.BodyHandlers.ofString()).body();
	}

	private record TestReferralData(Long inviterId, Long referredId, Long referralId) {}

	@TestConfiguration
	static class FailureInjectingWalletConfiguration {
		@Bean
		@Primary
		FailureInjectingWalletService failureInjectingWalletService(
				WalletRepository walletRepository,
				UserRepository userRepository,
				WalletTransactionRepository transactionRepository) {
			return new FailureInjectingWalletService(walletRepository, userRepository, transactionRepository);
		}
	}

	static class FailureInjectingWalletService extends WalletService {
		private final AtomicBoolean failNewUserCredit = new AtomicBoolean();

		void enableFailure() {
			failNewUserCredit.set(true);
		}

		void disableFailure() {
			failNewUserCredit.set(false);
		}

		FailureInjectingWalletService(
				WalletRepository walletRepository,
				UserRepository userRepository,
				WalletTransactionRepository transactionRepository) {
			super(walletRepository, userRepository, transactionRepository);
		}

		@Override
		@org.springframework.transaction.annotation.Transactional
		public void credit(Long userId, BigDecimal amount, WalletTransactionType type,
				String referenceType, String referenceId, String description) {
			super.credit(userId, amount, type, referenceType, referenceId, description);
			if (failNewUserCredit.get() && type == WalletTransactionType.REFERRAL_NEW_USER_REWARD) {
				throw new IllegalStateException("Controlled second wallet credit failure");
			}
		}
	}
}
