package com.peergeek.referral;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import com.peergeek.auth.InputValidationException;
import com.peergeek.common.PageResponse;
import com.peergeek.exception.BadRequestException;
import com.peergeek.exception.FieldErrorResponse;
import com.peergeek.exception.NotFoundException;
import com.peergeek.notification.NotificationService;
import com.peergeek.notification.NotificationType;
import com.peergeek.referral.dto.ReferralConfigDto;
import com.peergeek.referral.dto.ReferralHistoryItemDto;
import com.peergeek.referral.dto.ReferralInviteRequest;
import com.peergeek.referral.dto.ReferralInviteResponse;
import com.peergeek.referral.dto.ReferralSummaryDto;
import com.peergeek.referral.dto.ReferralValidateResponse;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import com.peergeek.wallet.WalletService;
import com.peergeek.wallet.WalletTransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferralService {
	private static final Logger log = LoggerFactory.getLogger(ReferralService.class);
	private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_LENGTH = 8;

	private final ReferralRepository referralRepository;
	private final UserRepository userRepository;
	private final WalletService walletService;
	private final NotificationService notificationService;
	private final ReferralEmailDispatcher emailDispatcher;
	private final ReferralSettings settings;
	private final SecureRandom secureRandom = new SecureRandom();

	public ReferralService(
			ReferralRepository referralRepository,
			UserRepository userRepository,
			WalletService walletService,
			NotificationService notificationService,
			ReferralEmailDispatcher emailDispatcher,
			ReferralSettings settings) {
		this.referralRepository = referralRepository;
		this.userRepository = userRepository;
		this.walletService = walletService;
		this.notificationService = notificationService;
		this.emailDispatcher = emailDispatcher;
		this.settings = settings;
	}

	@Transactional
	public ReferralInviteResponse sendInvitation(Long inviterUserId, ReferralInviteRequest request) {
		if (request == null || request.email() == null || request.email().isBlank()) {
			throw new BadRequestException("Invited email is required");
		}
		String invitedEmail = request.email().trim().toLowerCase(Locale.ROOT);

		UserEntity inviter = userRepository.findById(inviterUserId)
				.orElseThrow(() -> new NotFoundException("Inviter user not found"));

		// 1. Check self-referral
		if (inviter.getEmail().equalsIgnoreCase(invitedEmail)) {
			throw new BadRequestException("You cannot refer yourself.");
		}

		// 2. Check if email already belongs to an existing PeerGeek account
		// Per requirements: Do not expose whether an arbitrary email belongs to an existing user through revealing messages
		if (userRepository.findByEmail(invitedEmail).isPresent()) {
			throw new BadRequestException("Unable to send invitation to this email address.");
		}

		// 3. Prevent duplicate pending invitations to the same email from the same inviter
		if (referralRepository.existsByInviter_IdAndInvitedEmailAndStatus(inviterUserId, invitedEmail, ReferralStatus.PENDING)) {
			throw new BadRequestException("A pending invitation has already been sent to this email.");
		}

		// 4. Rate limiting: check recent invitations from this user
		long recentCount = referralRepository.countRecentInvitations(
				inviterUserId, Instant.now().minus(24, ChronoUnit.HOURS));
		if (recentCount >= settings.getMaxInvitationsPerDay()) {
			throw new BadRequestException("Daily invitation limit reached. Please try again tomorrow.");
		}

		// 5. Generate unique referral code
		String referralCode = generateUniqueReferralCode();

		// 6. Save referral record
		ReferralEntity referral = new ReferralEntity(
				inviter,
				invitedEmail,
				referralCode,
				settings.getInviterReward(),
				settings.getNewUserReward(),
				settings.getExpiryDays());
		try {
			referral = referralRepository.save(referral);
		} catch (DataIntegrityViolationException exception) {
			throw new BadRequestException("A pending invitation has already been sent to this email.");
		}

		// 7. Dispatch invitation email
		String registrationUrl = settings.getBaseUrl() + "/register?ref=" + referralCode
				+ "&email=" + URLEncoder.encode(invitedEmail, StandardCharsets.UTF_8);

		emailDispatcher.dispatch(
				invitedEmail,
				inviter.getDisplayName(),
				referralCode,
				registrationUrl,
				settings.getInviterReward(),
				settings.getNewUserReward(),
				settings.getExpiryDays(),
				MDC.get("requestId"));

		log.info("Referral invitation sent by userId={} to email={} code={}", inviterUserId, invitedEmail, referralCode);

		return new ReferralInviteResponse(
				"Invitation sent successfully",
				referralCode,
				invitedEmail,
				referral.getExpiresAt());
	}

	@Transactional(readOnly = true)
	public ReferralValidateResponse validateReferralCode(String code, String email) {
		if (code == null || code.isBlank()) {
			throw new BadRequestException("Referral code is required");
		}
		String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
		ReferralEntity referral = referralRepository.findByReferralCode(normalizedCode)
				.orElseThrow(() -> new NotFoundException("Invalid referral code"));

		if (referral.isExpired()) {
			throw new BadRequestException("Referral code has expired");
		}

		if (referral.getStatus() != ReferralStatus.PENDING) {
			throw new BadRequestException("Referral code is no longer valid or has already been used");
		}

		if (email != null && !email.isBlank()) {
			String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
			if (!referral.getInvitedEmail().equalsIgnoreCase(normalizedEmail)) {
				throw new BadRequestException("Referral code is not valid for this email address");
			}
		}

		return new ReferralValidateResponse(
				true,
				referral.getInviter().getDisplayName(),
				referral.getNewUserRewardAmount(),
				referral.getInviterRewardAmount(),
				"Referral code is valid");
	}

	@Transactional
	public ReferralEntity validateAndPrepareRegistration(String referralCode, String registrationEmail) {
		if (referralCode == null || referralCode.isBlank()) {
			return null;
		}
		String normalizedCode = referralCode.trim().toUpperCase(Locale.ROOT);
		ReferralEntity referral = referralRepository.findByReferralCode(normalizedCode)
				.orElseThrow(() -> validationError("referralCode", "Invalid referral code"));

		if (referral.isExpired()) {
			referral.markExpired(Instant.now());
			referralRepository.save(referral);
			throw validationError("referralCode", "Referral code has expired");
		}

		if (referral.getStatus() != ReferralStatus.PENDING) {
			throw validationError("referralCode", "Referral code is no longer valid or has already been used");
		}

		if (!referral.getInvitedEmail().equalsIgnoreCase(registrationEmail)) {
			throw validationError("referralCode", "Referral code does not match registration email");
		}

		return referral;
	}

	@Transactional
	public void associateReferralOnRegistration(UserEntity user, String referralCode) {
		if (referralCode == null || referralCode.isBlank()) {
			return;
		}
		String normalizedCode = referralCode.trim().toUpperCase(Locale.ROOT);
		ReferralEntity referral = referralRepository.findByReferralCodeForUpdate(normalizedCode)
				.orElseThrow(() -> validationError("referralCode", "Invalid referral code"));

		if (referral.getInviter().getId().equals(user.getId())) {
			throw new InputValidationException("You cannot refer yourself",
					List.of(new FieldErrorResponse("referralCode", "You cannot refer yourself")));
		}

		if (referral.isExpired()) {
			referral.markExpired(Instant.now());
			throw validationError("referralCode", "Referral code has expired");
		}

		if (referral.getStatus() != ReferralStatus.PENDING) {
			throw validationError("referralCode", "Referral code is no longer valid or has already been used");
		}

		if (!referral.getInvitedEmail().equalsIgnoreCase(user.getEmail())) {
			throw validationError("referralCode", "Referral code does not match registration email");
		}

		if (referralRepository.existsByReferredUserId(user.getId())) {
			throw validationError("referralCode", "A referral reward has already been claimed for this account");
		}

		referral.markRegistered(user, Instant.now());
		referralRepository.saveAndFlush(referral);
		log.info("Referral code={} associated with new userId={}", normalizedCode, user.getId());
	}

	@Transactional
	public void completeReferralReward(UserEntity user) {
		if (user == null || user.getId() == null) {
			return;
		}

		Optional<ReferralEntity> referralOpt = referralRepository.findByReferredUserIdForUpdate(user.getId());
		if (referralOpt.isEmpty()) {
			return;
		}

		ReferralEntity referral = referralOpt.get();

		// Idempotency: only reward if status is REGISTERED
		if (referral.getStatus() != ReferralStatus.REGISTERED) {
			log.info("Referral id={} already processed or not in REGISTERED state (status={})",
					referral.getId(), referral.getStatus());
			return;
		}

		UserEntity inviter = referral.getInviter();
		if (inviter.getId().equals(user.getId())) {
			log.warn("Self-referral detected at reward stage for referralId={}, skipping rewards", referral.getId());
			return;
		}

		// Reward both inviter and new user atomically
		walletService.batchReferralReward(
				inviter.getId(),
				referral.getInviterRewardAmount(),
				user.getId(),
				referral.getNewUserRewardAmount(),
				referral.getId().toString(),
				"Referral reward for inviting " + user.getEmail(),
				"Welcome referral reward from " + inviter.getDisplayName());

		referral.markRewarded(Instant.now());
		referralRepository.saveAndFlush(referral);

		// Notifications are created only after both credits succeed.
		String inviterCoinsStr = referral.getInviterRewardAmount().stripTrailingZeros().toPlainString();
		notificationService.createNotification(
				inviter.getId(),
				NotificationType.REFERRAL_SUCCESSFUL,
				"Referral Reward Earned!",
				String.format("Your friend has successfully joined PeerGeek using your referral code! You've earned %s GeekCoins. 🪙", inviterCoinsStr),
				"REFERRAL",
				referral.getId(),
				"/community",
				"referral-reward-" + referral.getId());

		// 5. Notify new user
		String newUserCoinsStr = referral.getNewUserRewardAmount().stripTrailingZeros().toPlainString();
		notificationService.createNotification(
				user.getId(),
				NotificationType.REFERRAL_WELCOME_REWARD,
				"Welcome to PeerGeek!",
				String.format("Welcome to PeerGeek! You've earned %s GeekCoins as a welcome referral reward. 🪙", newUserCoinsStr),
				"REFERRAL",
				referral.getId(),
				"/community",
				"referral-welcome-" + referral.getId());

		log.info("Referral id={} rewarded successfully: inviterId={} ({} PGC), newUserId={} ({} PGC)",
				referral.getId(), inviter.getId(), inviterCoinsStr, user.getId(), newUserCoinsStr);
	}

	@Transactional(readOnly = true)
	public ReferralSummaryDto getSummary(Long userId) {
		List<ReferralEntity> referrals = referralRepository.findAllByInviter_Id(userId);

		long totalInvitations = referrals.size();
		long pendingReferrals = referrals.stream()
				.filter(r -> (r.getStatus() == ReferralStatus.PENDING && !r.isExpired())
						|| r.getStatus() == ReferralStatus.REGISTERED)
				.count();
		long successfulReferrals = referrals.stream()
				.filter(r -> r.getStatus() == ReferralStatus.REWARDED)
				.count();
		BigDecimal totalCoinsEarned = referrals.stream()
				.filter(r -> r.getStatus() == ReferralStatus.REWARDED)
				.map(ReferralEntity::getInviterRewardAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		return new ReferralSummaryDto(
				totalInvitations,
				pendingReferrals,
				successfulReferrals,
				totalCoinsEarned,
				settings.getInviterReward(),
				settings.getNewUserReward());
	}

	@Transactional(readOnly = true)
	public PageResponse<ReferralHistoryItemDto> getHistory(Long userId, Pageable pageable) {
		Page<ReferralEntity> page = referralRepository.findAllByInviter_IdOrderByCreatedAtDesc(userId, pageable);
		List<ReferralHistoryItemDto> items = page.getContent().stream()
				.map(r -> {
					ReferralStatus displayStatus = r.getStatus();
					if (displayStatus == ReferralStatus.PENDING && r.isExpired()) {
						displayStatus = ReferralStatus.EXPIRED;
					}
					return new ReferralHistoryItemDto(
							r.getId(),
							r.getInvitedEmail(),
							r.getReferralCode(),
							displayStatus,
							r.getInviterRewardAmount(),
							r.getNewUserRewardAmount(),
							r.getCreatedAt(),
							r.getRegisteredAt(),
							r.getRewardedAt(),
							r.getExpiresAt());
				})
				.toList();

		return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}

	public ReferralConfigDto getConfig() {
		return new ReferralConfigDto(
				settings.getInviterReward(),
				settings.getNewUserReward(),
				settings.getExpiryDays());
	}

	private String generateUniqueReferralCode() {
		for (int attempt = 0; attempt < 10; attempt++) {
			StringBuilder sb = new StringBuilder("PG-");
			for (int i = 0; i < CODE_LENGTH; i++) {
				int index = secureRandom.nextInt(CODE_CHARS.length());
				sb.append(CODE_CHARS.charAt(index));
			}
			String candidate = sb.toString();
			if (!referralRepository.existsByReferralCode(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Failed to generate a unique referral code after multiple attempts");
	}

	private InputValidationException validationError(String field, String message) {
		return new InputValidationException("Request validation failed",
				List.of(new FieldErrorResponse(field, message)));
	}
}
