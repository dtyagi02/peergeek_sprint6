package com.peergeek.referral;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import com.peergeek.auth.InputValidationException;
import com.peergeek.common.PageResponse;
import com.peergeek.exception.BadRequestException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.notification.NotificationService;
import com.peergeek.notification.NotificationType;
import com.peergeek.referral.dto.ReferralHistoryItemDto;
import com.peergeek.referral.dto.ReferralInviteRequest;
import com.peergeek.referral.dto.ReferralInviteResponse;
import com.peergeek.referral.dto.ReferralSummaryDto;
import com.peergeek.referral.dto.ReferralValidateResponse;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import com.peergeek.wallet.WalletService;
import com.peergeek.wallet.WalletTransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {

	@Mock
	private ReferralRepository referralRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private WalletService walletService;

	@Mock
	private NotificationService notificationService;

	@Mock
	private ReferralEmailDispatcher emailDispatcher;

	private ReferralSettings settings;
	private ReferralService referralService;

	private UserEntity inviter;
	private UserEntity newReferredUser;

	@BeforeEach
	void setUp() {
		settings = new ReferralSettings();
		settings.setInviterReward(new BigDecimal("100.00"));
		settings.setNewUserReward(new BigDecimal("50.00"));
		settings.setExpiryDays(7);
		settings.setMaxInvitationsPerDay(20);
		settings.setBaseUrl("http://localhost:5173");

		referralService = new ReferralService(
				referralRepository,
				userRepository,
				walletService,
				notificationService,
				emailDispatcher,
				settings);

		inviter = new UserEntity("inviter@example.com", "hash1", "Alice Inviter", "alice_inviter", true);
		ReflectionTestUtils.setField(inviter, "id", 1L);

		newReferredUser = new UserEntity("friend@example.com", "hash2", "Bob Friend", "bob_friend", false);
		ReflectionTestUtils.setField(newReferredUser, "id", 2L);
	}

	@Test
	@DisplayName("sendInvitation: successfully creates referral and dispatches email")
	void sendInvitation_Success() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(inviter));
		when(userRepository.findByEmail("friend@example.com")).thenReturn(Optional.empty());
		when(referralRepository.existsByInviter_IdAndInvitedEmailAndStatus(1L, "friend@example.com", ReferralStatus.PENDING))
				.thenReturn(false);
		when(referralRepository.countRecentInvitations(eq(1L), any(Instant.class))).thenReturn(2L);
		when(referralRepository.existsByReferralCode(anyString())).thenReturn(false);
		when(referralRepository.save(any(ReferralEntity.class))).thenAnswer(inv -> {
			ReferralEntity r = inv.getArgument(0);
			ReflectionTestUtils.setField(r, "id", 100L);
			return r;
		});

		ReferralInviteResponse response = referralService.sendInvitation(1L, new ReferralInviteRequest("friend@example.com"));

		assertThat(response).isNotNull();
		assertThat(response.message()).isEqualTo("Invitation sent successfully");
		assertThat(response.invitedEmail()).isEqualTo("friend@example.com");
		assertThat(response.referralCode()).startsWith("PG-");

		ArgumentCaptor<ReferralEntity> captor = ArgumentCaptor.forClass(ReferralEntity.class);
		verify(referralRepository).save(captor.capture());
		ReferralEntity saved = captor.getValue();
		assertThat(saved.getStatus()).isEqualTo(ReferralStatus.PENDING);
		assertThat(saved.getInviterRewardAmount()).isEqualByComparingTo("100.00");
		assertThat(saved.getNewUserRewardAmount()).isEqualByComparingTo("50.00");
		assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));

		verify(emailDispatcher).dispatch(
				eq("friend@example.com"),
				eq("Alice Inviter"),
				eq(saved.getReferralCode()),
				anyString(),
				eq(new BigDecimal("100.00")),
				eq(new BigDecimal("50.00")),
				eq(7),
				any());
	}

	@Test
	@DisplayName("sendInvitation: rejects self-referral")
	void sendInvitation_RejectsSelfReferral() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(inviter));

		assertThatThrownBy(() -> referralService.sendInvitation(1L, new ReferralInviteRequest("inviter@example.com")))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("You cannot refer yourself");

		verify(referralRepository, never()).save(any());
	}

	@Test
	@DisplayName("sendInvitation: rejects already existing PeerGeek account without revealing details")
	void sendInvitation_RejectsExistingAccount() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(inviter));
		UserEntity existingUser = new UserEntity("already_member@example.com", "h", "Existing", "existing", true);
		when(userRepository.findByEmail("already_member@example.com")).thenReturn(Optional.of(existingUser));

		assertThatThrownBy(() -> referralService.sendInvitation(1L, new ReferralInviteRequest("already_member@example.com")))
				.isInstanceOf(BadRequestException.class)
				.hasMessage("Unable to send invitation to this email address.");

		verify(referralRepository, never()).save(any());
	}

	@Test
	@DisplayName("sendInvitation: prevents duplicate pending invitations to the same email from same inviter")
	void sendInvitation_PreventsDuplicatePendingInvitation() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(inviter));
		when(userRepository.findByEmail("pending@example.com")).thenReturn(Optional.empty());
		when(referralRepository.existsByInviter_IdAndInvitedEmailAndStatus(1L, "pending@example.com", ReferralStatus.PENDING))
				.thenReturn(true);

		assertThatThrownBy(() -> referralService.sendInvitation(1L, new ReferralInviteRequest("pending@example.com")))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("A pending invitation has already been sent to this email");

		verify(referralRepository, never()).save(any());
	}

	@Test
	@DisplayName("sendInvitation: maps the database active-invitation constraint to a duplicate response")
	void sendInvitation_ConcurrentDuplicateConstraint() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(inviter));
		when(userRepository.findByEmail("race@example.com")).thenReturn(Optional.empty());
		when(referralRepository.existsByInviter_IdAndInvitedEmailAndStatus(1L, "race@example.com", ReferralStatus.PENDING))
				.thenReturn(false);
		when(referralRepository.countRecentInvitations(eq(1L), any(Instant.class))).thenReturn(0L);
		when(referralRepository.existsByReferralCode(anyString())).thenReturn(false);
		when(referralRepository.save(any(ReferralEntity.class)))
				.thenThrow(new DataIntegrityViolationException("uk_referrals_active_invitation"));

		assertThatThrownBy(() -> referralService.sendInvitation(1L, new ReferralInviteRequest("race@example.com")))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("pending invitation");
		verify(emailDispatcher, never()).dispatch(any(), any(), any(), any(), any(), any(), anyInt(), any());
	}

	@Test
	@DisplayName("sendInvitation: passes the configured expiry to the email dispatcher")
	void sendInvitation_UsesConfiguredExpiry() {
		settings.setExpiryDays(10);
		when(userRepository.findById(1L)).thenReturn(Optional.of(inviter));
		when(userRepository.findByEmail("expiry@example.com")).thenReturn(Optional.empty());
		when(referralRepository.existsByInviter_IdAndInvitedEmailAndStatus(1L, "expiry@example.com", ReferralStatus.PENDING))
				.thenReturn(false);
		when(referralRepository.countRecentInvitations(eq(1L), any(Instant.class))).thenReturn(0L);
		when(referralRepository.existsByReferralCode(anyString())).thenReturn(false);
		when(referralRepository.save(any(ReferralEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		referralService.sendInvitation(1L, new ReferralInviteRequest("expiry@example.com"));

		verify(emailDispatcher).dispatch(
				eq("expiry@example.com"), eq("Alice Inviter"), anyString(), anyString(),
				eq(new BigDecimal("100.00")), eq(new BigDecimal("50.00")), eq(10), any());
	}

	@Test
	@DisplayName("sendInvitation: enforces daily invitation limit")
	void sendInvitation_EnforcesDailyInvitationLimit() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(inviter));
		when(userRepository.findByEmail("limit@example.com")).thenReturn(Optional.empty());
		when(referralRepository.existsByInviter_IdAndInvitedEmailAndStatus(1L, "limit@example.com", ReferralStatus.PENDING))
				.thenReturn(false);
		when(referralRepository.countRecentInvitations(eq(1L), any(Instant.class))).thenReturn(20L);

		assertThatThrownBy(() -> referralService.sendInvitation(1L, new ReferralInviteRequest("limit@example.com")))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("Daily invitation limit reached");

		verify(referralRepository, never()).save(any());
	}

	@Test
	@DisplayName("validateReferralCode: returns valid response for active code and matching email")
	void validateReferralCode_Success() {
		ReferralEntity referral = new ReferralEntity(inviter, "friend@example.com", "PG-VALID01",
				new BigDecimal("100.00"), new BigDecimal("50.00"), 7);
		when(referralRepository.findByReferralCode("PG-VALID01")).thenReturn(Optional.of(referral));

		ReferralValidateResponse res = referralService.validateReferralCode("PG-VALID01", "friend@example.com");

		assertThat(res.valid()).isTrue();
		assertThat(res.inviterDisplayName()).isEqualTo("Alice Inviter");
		assertThat(res.newUserRewardAmount()).isEqualByComparingTo("50.00");
		assertThat(res.inviterRewardAmount()).isEqualByComparingTo("100.00");
	}

	@Test
	@DisplayName("validateReferralCode: rejects expired code")
	void validateReferralCode_RejectsExpired() {
		ReferralEntity referral = new ReferralEntity(inviter, "friend@example.com", "PG-EXPIRED",
				new BigDecimal("100.00"), new BigDecimal("50.00"), -1);
		when(referralRepository.findByReferralCode("PG-EXPIRED")).thenReturn(Optional.of(referral));

		assertThatThrownBy(() -> referralService.validateReferralCode("PG-EXPIRED", "friend@example.com"))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("expired");
	}

	@Test
	@DisplayName("validateReferralCode: rejects mismatch email")
	void validateReferralCode_RejectsMismatchEmail() {
		ReferralEntity referral = new ReferralEntity(inviter, "friend@example.com", "PG-VALID01",
				new BigDecimal("100.00"), new BigDecimal("50.00"), 7);
		when(referralRepository.findByReferralCode("PG-VALID01")).thenReturn(Optional.of(referral));

		assertThatThrownBy(() -> referralService.validateReferralCode("PG-VALID01", "other@example.com"))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("not valid for this email address");
	}

	@Test
	@DisplayName("associateReferralOnRegistration: links referral to new user in REGISTERED state")
	void associateReferralOnRegistration_Success() {
		ReferralEntity referral = new ReferralEntity(inviter, "friend@example.com", "PG-REG0001",
				new BigDecimal("100.00"), new BigDecimal("50.00"), 7);
		when(referralRepository.findByReferralCodeForUpdate("PG-REG0001")).thenReturn(Optional.of(referral));
		when(referralRepository.existsByReferredUserId(2L)).thenReturn(false);

		referralService.associateReferralOnRegistration(newReferredUser, "PG-REG0001");

		assertThat(referral.getStatus()).isEqualTo(ReferralStatus.REGISTERED);
		assertThat(referral.getReferredUser()).isEqualTo(newReferredUser);
		assertThat(referral.getRegisteredAt()).isNotNull();
		verify(referralRepository).saveAndFlush(referral);
	}

	@Test
	@DisplayName("associateReferralOnRegistration: rejects self-referral")
	void associateReferralOnRegistration_RejectsSelfReferral() {
		ReferralEntity referral = new ReferralEntity(inviter, "inviter@example.com", "PG-SELF001",
				new BigDecimal("100.00"), new BigDecimal("50.00"), 7);
		when(referralRepository.findByReferralCodeForUpdate("PG-SELF001")).thenReturn(Optional.of(referral));

		assertThatThrownBy(() -> referralService.associateReferralOnRegistration(inviter, "PG-SELF001"))
				.isInstanceOf(InputValidationException.class)
				.hasMessageContaining("You cannot refer yourself");
	}

	@Test
	@DisplayName("completeReferralReward: atomically credits both wallets, records ledger, and notifies users")
	void completeReferralReward_Success() {
		ReferralEntity referral = new ReferralEntity(inviter, "friend@example.com", "PG-REWARD1",
				new BigDecimal("100.00"), new BigDecimal("50.00"), 7);
		ReflectionTestUtils.setField(referral, "id", 42L);
		referral.markRegistered(newReferredUser, Instant.now());

		when(referralRepository.findByReferredUserIdForUpdate(2L)).thenReturn(Optional.of(referral));

		referralService.completeReferralReward(newReferredUser);

		assertThat(referral.getStatus()).isEqualTo(ReferralStatus.REWARDED);
		assertThat(referral.getRewardedAt()).isNotNull();

		// Check inviter wallet credit
		verify(walletService).credit(
				eq(1L),
				eq(new BigDecimal("100.00")),
				eq(WalletTransactionType.REFERRAL_INVITER_REWARD),
				eq("REFERRAL"),
				eq("42"),
				eq("Referral reward for inviting friend@example.com"));

		// Check new user wallet credit
		verify(walletService).credit(
				eq(2L),
				eq(new BigDecimal("50.00")),
				eq(WalletTransactionType.REFERRAL_NEW_USER_REWARD),
				eq("REFERRAL"),
				eq("42"),
				eq("Welcome referral reward from Alice Inviter"));

		// Check inviter notification
		verify(notificationService).createNotification(
				eq(1L),
				eq(NotificationType.REFERRAL_SUCCESSFUL),
				eq("Referral Reward Earned!"),
				eq("Your friend has successfully joined PeerGeek using your referral code! You've earned 100 GeekCoins. 🪙"),
				eq("REFERRAL"),
				eq(42L),
				eq("/community"),
				eq("referral-reward-42"));

		// Check new user notification
		verify(notificationService).createNotification(
				eq(2L),
				eq(NotificationType.REFERRAL_WELCOME_REWARD),
				eq("Welcome to PeerGeek!"),
				eq("Welcome to PeerGeek! You've earned 50 GeekCoins as a welcome referral reward. 🪙"),
				eq("REFERRAL"),
				eq(42L),
				eq("/community"),
				eq("referral-welcome-42"));
	}

	@Test
	@DisplayName("completeReferralReward: idempotent - duplicate call on already rewarded referral does not reward again")
	void completeReferralReward_IdempotentDuplicateCall() {
		ReferralEntity referral = new ReferralEntity(inviter, "friend@example.com", "PG-REWARD1",
				new BigDecimal("100.00"), new BigDecimal("50.00"), 7);
		ReflectionTestUtils.setField(referral, "id", 42L);
		referral.markRegistered(newReferredUser, Instant.now());
		referral.markRewarded(Instant.now()); // Already rewarded!

		when(referralRepository.findByReferredUserIdForUpdate(2L)).thenReturn(Optional.of(referral));

		referralService.completeReferralReward(newReferredUser);

		// Must NOT credit or notify again
		verify(walletService, never()).credit(any(), any(), any(), any(), any(), any());
		verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("completeReferralReward: skips if user registered without referral code")
	void completeReferralReward_NoReferral() {
		when(referralRepository.findByReferredUserIdForUpdate(2L)).thenReturn(Optional.empty());

		referralService.completeReferralReward(newReferredUser);

		verify(walletService, never()).credit(any(), any(), any(), any(), any(), any());
		verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("getSummary: returns accurate counts and total coins earned")
	void getSummary_CalculatesStatsAccurately() {
		ReferralEntity r1 = new ReferralEntity(inviter, "p1@example.com", "PG-1", new BigDecimal("100.00"), new BigDecimal("50.00"), 7);
		ReferralEntity r2 = new ReferralEntity(inviter, "p2@example.com", "PG-2", new BigDecimal("100.00"), new BigDecimal("50.00"), 7);
		r2.markRegistered(newReferredUser, Instant.now());

		ReferralEntity r3 = new ReferralEntity(inviter, "r3@example.com", "PG-3", new BigDecimal("100.00"), new BigDecimal("50.00"), 7);
		r3.markRegistered(newReferredUser, Instant.now());
		r3.markRewarded(Instant.now());

		when(referralRepository.findAllByInviter_Id(1L)).thenReturn(List.of(r1, r2, r3));

		ReferralSummaryDto summary = referralService.getSummary(1L);

		assertThat(summary.totalInvitations()).isEqualTo(3);
		assertThat(summary.pendingReferrals()).isEqualTo(2);
		assertThat(summary.successfulReferrals()).isEqualTo(1);
		assertThat(summary.totalCoinsEarned()).isEqualByComparingTo("100.00");
		assertThat(summary.inviterRewardAmount()).isEqualByComparingTo("100.00");
		assertThat(summary.newUserRewardAmount()).isEqualByComparingTo("50.00");
	}

	@Test
	@DisplayName("getHistory: returns paginated list with auto-expired mapping")
	void getHistory_PagedResults() {
		ReferralEntity expired = new ReferralEntity(inviter, "old@example.com", "PG-OLD",
				new BigDecimal("100.00"), new BigDecimal("50.00"), -1);
		Pageable pageable = PageRequest.of(0, 10);
		when(referralRepository.findAllByInviter_IdOrderByCreatedAtDesc(1L, pageable))
				.thenReturn(new PageImpl<>(List.of(expired), pageable, 1));

		PageResponse<ReferralHistoryItemDto> history = referralService.getHistory(1L, pageable);

		assertThat(history.content()).hasSize(1);
		assertThat(history.content().get(0).status()).isEqualTo(ReferralStatus.EXPIRED);
		assertThat(history.content().get(0).referralCode()).isEqualTo("PG-OLD");
	}
}
