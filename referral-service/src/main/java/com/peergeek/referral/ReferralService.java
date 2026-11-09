package com.peergeek.referral;

import com.peergeek.referral.client.IdentityClient;
import com.peergeek.referral.client.NotificationClient;
import com.peergeek.referral.client.WalletClient;
import com.peergeek.referral.dto.PageResponse;
import com.peergeek.referral.dto.ReferralConfigDto;
import com.peergeek.referral.dto.ReferralHistoryItemDto;
import com.peergeek.referral.dto.ReferralInviteRequest;
import com.peergeek.referral.dto.ReferralInviteResponse;
import com.peergeek.referral.dto.ReferralSummaryDto;
import com.peergeek.referral.dto.ReferralValidateResponse;
import com.peergeek.referral.exception.BadRequestException;
import com.peergeek.referral.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ReferralService {

	private static final Logger log = LoggerFactory.getLogger(ReferralService.class);
	private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_LENGTH = 8;

	private final ReferralRepository referralRepository;
	private final WalletClient walletClient;
	private final NotificationClient notificationClient;
	private final IdentityClient identityClient;
	private final ReferralSettings settings;
	private final SecureRandom secureRandom = new SecureRandom();

	public ReferralService(
			ReferralRepository referralRepository,
			WalletClient walletClient,
			NotificationClient notificationClient,
			IdentityClient identityClient,
			ReferralSettings settings) {
		this.referralRepository = referralRepository;
		this.walletClient = walletClient;
		this.notificationClient = notificationClient;
		this.identityClient = identityClient;
		this.settings = settings;
	}

	@Transactional
	public ReferralInviteResponse sendInvitation(Long inviterUserId, ReferralInviteRequest request) {
		if (request == null || request.email() == null || request.email().isBlank()) {
			throw new BadRequestException("Invited email is required");
		}
		String invitedEmail = request.email().trim().toLowerCase(Locale.ROOT);

		IdentityClient.UserInfo inviter = identityClient.getUserById(inviterUserId)
				.orElseThrow(() -> new NotFoundException("Inviter user not found"));

		if (referralRepository.existsByInviterIdAndInvitedEmailAndStatus(inviterUserId, invitedEmail, ReferralStatus.PENDING)) {
			throw new BadRequestException("A pending invitation has already been sent to this email.");
		}

		long recentCount = referralRepository.countRecentInvitations(
				inviterUserId, Instant.now().minus(24, ChronoUnit.HOURS));
		if (recentCount >= settings.getMaxInvitationsPerDay()) {
			throw new BadRequestException("Daily invitation limit reached. Please try again tomorrow.");
		}

		String referralCode = generateUniqueReferralCode();

		ReferralEntity referral = new ReferralEntity(
				inviterUserId,
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

		String registrationUrl = settings.getBaseUrl() + "/register?ref=" + referralCode
				+ "&email=" + URLEncoder.encode(invitedEmail, StandardCharsets.UTF_8);

		notificationClient.sendReferralInvitationEmail(
				invitedEmail,
				inviter.displayName(),
				referralCode,
				registrationUrl,
				settings.getInviterReward().stripTrailingZeros().toPlainString(),
				settings.getNewUserReward().stripTrailingZeros().toPlainString(),
				settings.getExpiryDays());

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

		IdentityClient.UserInfo inviter = identityClient.getUserById(referral.getInviterId()).orElse(null);
		String inviterName = inviter != null ? inviter.displayName() : "A friend";

		return new ReferralValidateResponse(
				true,
				inviterName,
				referral.getNewUserRewardAmount(),
				referral.getInviterRewardAmount(),
				"Referral code is valid");
	}

	@Transactional
	public void associateReferralOnRegistration(Long newUserId, String referralCode, String registrationEmail) {
		if (referralCode == null || referralCode.isBlank()) {
			return;
		}
		String normalizedCode = referralCode.trim().toUpperCase(Locale.ROOT);
		ReferralEntity referral = referralRepository.findByReferralCodeForUpdate(normalizedCode)
				.orElseThrow(() -> new BadRequestException("Invalid referral code"));

		if (referral.getInviterId().equals(newUserId)) {
			throw new BadRequestException("You cannot refer yourself");
		}

		if (referral.isExpired()) {
			referral.markExpired(Instant.now());
			throw new BadRequestException("Referral code has expired");
		}

		if (referral.getStatus() != ReferralStatus.PENDING) {
			throw new BadRequestException("Referral code is no longer valid or has already been used");
		}

		if (registrationEmail != null && !referral.getInvitedEmail().equalsIgnoreCase(registrationEmail)) {
			throw new BadRequestException("Referral code does not match registration email");
		}

		if (referralRepository.existsByReferredUserId(newUserId)) {
			throw new BadRequestException("A referral reward has already been claimed for this account");
		}

		referral.markRegistered(newUserId, Instant.now());
		referralRepository.saveAndFlush(referral);
		log.info("Referral code={} associated with new userId={}", normalizedCode, newUserId);
	}

	@Transactional
	public void completeReferralReward(Long newUserId) {
		if (newUserId == null) {
			return;
		}

		Optional<ReferralEntity> referralOpt = referralRepository.findByReferredUserIdForUpdate(newUserId);
		if (referralOpt.isEmpty()) {
			return;
		}

		ReferralEntity referral = referralOpt.get();
		if (referral.getStatus() != ReferralStatus.REGISTERED) {
			log.info("Referral id={} already processed or not in REGISTERED state (status={})",
					referral.getId(), referral.getStatus());
			return;
		}

		Long inviterId = referral.getInviterId();
		if (inviterId.equals(newUserId)) {
			log.warn("Self-referral detected at reward stage for referralId={}, skipping", referral.getId());
			return;
		}

		IdentityClient.UserInfo inviter = identityClient.getUserById(inviterId).orElse(null);
		String inviterName = inviter != null ? inviter.displayName() : "Friend";

		walletClient.batchReferralReward(
				referral.getId(),
				inviterId,
				referral.getInviterRewardAmount(),
				newUserId,
				referral.getNewUserRewardAmount(),
				"Referral reward for inviting " + referral.getInvitedEmail(),
				"Welcome referral reward from " + inviterName
		);

		referral.markRewarded(Instant.now());
		referralRepository.saveAndFlush(referral);

		String inviterCoinsStr = referral.getInviterRewardAmount().stripTrailingZeros().toPlainString();
		notificationClient.createNotification(
				inviterId,
				"REFERRAL_SUCCESSFUL",
				"Referral Reward Earned!",
				String.format("Your friend has successfully joined PeerGeek using your referral code! You've earned %s GeekCoins. 🪙", inviterCoinsStr),
				"REFERRAL",
				referral.getId(),
				"/community",
				"referral-reward-" + referral.getId()
		);

		String newUserCoinsStr = referral.getNewUserRewardAmount().stripTrailingZeros().toPlainString();
		notificationClient.createNotification(
				newUserId,
				"REFERRAL_WELCOME_REWARD",
				"Welcome to PeerGeek!",
				String.format("Welcome to PeerGeek! You've earned %s GeekCoins as a welcome referral reward. 🪙", newUserCoinsStr),
				"REFERRAL",
				referral.getId(),
				"/community",
				"referral-welcome-" + referral.getId()
		);

		log.info("Referral id={} rewarded successfully: inviterId={} ({} PGC), newUserId={} ({} PGC)",
				referral.getId(), inviterId, inviterCoinsStr, newUserId, newUserCoinsStr);
	}

	@Transactional(readOnly = true)
	public ReferralSummaryDto getSummary(Long userId) {
		List<ReferralEntity> referrals = referralRepository.findAllByInviterId(userId);

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
		Page<ReferralEntity> page = referralRepository.findAllByInviterIdOrderByCreatedAtDesc(userId, pageable);
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
				settings.getExpiryDays(),
				settings.getMaxInvitationsPerDay());
	}

	private String generateUniqueReferralCode() {
		for (int attempt = 0; attempt < 10; attempt++) {
			StringBuilder code = new StringBuilder("PG-");
			for (int i = 0; i < CODE_LENGTH; i++) {
				code.append(CODE_CHARS.charAt(secureRandom.nextInt(CODE_CHARS.length())));
			}
			String candidate = code.toString();
			if (!referralRepository.existsByReferralCode(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Failed to generate unique referral code after 10 attempts");
	}
}

