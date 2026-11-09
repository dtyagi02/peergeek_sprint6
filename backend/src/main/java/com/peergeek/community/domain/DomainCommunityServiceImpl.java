package com.peergeek.community.domain;

import com.peergeek.common.PageResponse;
import com.peergeek.community.domain.dto.DomainChatMessageDto;
import com.peergeek.community.domain.dto.DomainCommunityDto;
import com.peergeek.community.domain.dto.DomainMemberDto;
import com.peergeek.community.domain.dto.SendDomainMessageRequest;
import com.peergeek.community.dto.CommunityAuthorDto;
import com.peergeek.course.CategoryEntity;
import com.peergeek.course.CategoryRepository;
import com.peergeek.exception.BadRequestException;
import com.peergeek.exception.ForbiddenException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.notification.NotificationService;
import com.peergeek.notification.NotificationType;
import com.peergeek.user.PublicProfileDto;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import com.peergeek.user.UserService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.peergeek.audit.AuditAction;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.community.global.GlobalCommunityBlockRepository;
import com.peergeek.community.websocket.CommunityRealtimeEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DomainCommunityServiceImpl implements DomainCommunityService {

	private static final Pattern MENTION_PATTERN = Pattern.compile("(?<!\\w)@([a-zA-Z0-9_]{3,30})\\b");

	private final DomainCommunityRepository domainCommunityRepository;
	private final DomainCommunityMembershipRepository membershipRepository;
	private final DomainChatMessageRepository messageRepository;
	private final CategoryRepository categoryRepository;
	private final UserRepository userRepository;
	private final UserService userService;
	private final NotificationService notificationService;
	private final GlobalCommunityBlockRepository blockRepository;
	private final AuditService auditService;
	private final SimpMessagingTemplate messagingTemplate;

	public DomainCommunityServiceImpl(
			DomainCommunityRepository domainCommunityRepository,
			DomainCommunityMembershipRepository membershipRepository,
			DomainChatMessageRepository messageRepository,
			CategoryRepository categoryRepository,
			UserRepository userRepository,
			UserService userService,
			NotificationService notificationService,
			GlobalCommunityBlockRepository blockRepository,
			AuditService auditService,
			SimpMessagingTemplate messagingTemplate) {
		this.domainCommunityRepository = domainCommunityRepository;
		this.membershipRepository = membershipRepository;
		this.messageRepository = messageRepository;
		this.categoryRepository = categoryRepository;
		this.userRepository = userRepository;
		this.userService = userService;
		this.notificationService = notificationService;
		this.blockRepository = blockRepository;
		this.auditService = auditService;
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	@Transactional(readOnly = true)
	public DomainCommunityDto getDomainCommunity(String slug, Long currentUserId) {
		DomainCommunityEntity domain = findOrCreateDomainBySlug(slug);
		long memberCount = membershipRepository.countByDomainId(domain.getId());
		boolean isMember = currentUserId != null && membershipRepository.existsByDomainIdAndUserId(domain.getId(), currentUserId);

		return toDto(domain, memberCount, isMember);
	}

	@Override
	@Transactional(readOnly = true)
	public List<DomainCommunityDto> listDomainCommunities(Long currentUserId) {
		List<DomainCommunityEntity> domains = domainCommunityRepository.findAllActive();
		return domains.stream().map(domain -> {
			long memberCount = membershipRepository.countByDomainId(domain.getId());
			boolean isMember = currentUserId != null && membershipRepository.existsByDomainIdAndUserId(domain.getId(), currentUserId);
			return toDto(domain, memberCount, isMember);
		}).toList();
	}

	@Override
	public DomainCommunityDto joinCommunity(String slug, Long userId) {
		DomainCommunityEntity domain = findOrCreateDomainBySlug(slug);
		UserEntity user = userRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("User not found"));

		boolean alreadyMember = membershipRepository.existsByDomainIdAndUserId(domain.getId(), userId);
		if (!alreadyMember) {
			membershipRepository.save(new DomainCommunityMembershipEntity(domain.getId(), userId, Instant.now()));

			// Record join activity as a system message
			String displayName = user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();
			String systemContent = displayName + " joined " + domain.getCategory().getName() + ".";
			messageRepository.save(new DomainChatMessageEntity(
					domain.getId(),
					userId,
					MessageType.SYSTEM_JOIN,
					systemContent
			));
		}

		long memberCount = membershipRepository.countByDomainId(domain.getId());
		return toDto(domain, memberCount, true);
	}

	@Override
	public DomainCommunityDto leaveCommunity(String slug, Long userId) {
		DomainCommunityEntity domain = findOrCreateDomainBySlug(slug);
		membershipRepository.deleteByDomainIdAndUserId(domain.getId(), userId);

		long memberCount = membershipRepository.countByDomainId(domain.getId());
		return toDto(domain, memberCount, false);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<DomainMemberDto> getMembers(String slug, Pageable pageable) {
		DomainCommunityEntity domain = findOrCreateDomainBySlug(slug);
		Page<DomainCommunityMembershipEntity> membershipPage = membershipRepository.findMembersByDomainId(domain.getId(), pageable);

		List<Long> userIds = membershipPage.getContent().stream()
				.map(DomainCommunityMembershipEntity::getUserId)
				.toList();

		Map<Long, PublicProfileDto> profileMap = userService.getPublicProfiles(userIds);

		List<DomainMemberDto> members = membershipPage.getContent().stream().map(m -> {
			PublicProfileDto profile = profileMap.get(m.getUserId());
			String username = profile != null ? profile.username() : "user_" + m.getUserId();
			String displayName = profile != null ? profile.displayName() : username;
			String avatarUrl = profile != null ? profile.avatarUrl() : null;

			return new DomainMemberDto(
					m.getUserId(),
					username,
					displayName,
					avatarUrl,
					m.getJoinedAt()
			);
		}).toList();

		return new PageResponse<>(
				members,
				membershipPage.getNumber(),
				membershipPage.getSize(),
				membershipPage.getTotalElements(),
				membershipPage.getTotalPages()
		);
	}

	@Override
	@Transactional(readOnly = true)
	public List<DomainChatMessageDto> getMessages(String slug, Long beforeId, int limit, Long currentUserId) {
		DomainCommunityEntity domain = findOrCreateDomainBySlug(slug);
		int safeLimit = Math.min(100, Math.max(1, limit));
		Pageable pageable = PageRequest.of(0, safeLimit);

		List<DomainChatMessageEntity> entitiesDesc = beforeId != null
				? messageRepository.findMessagesBeforeDesc(domain.getId(), beforeId, pageable)
				: messageRepository.findRecentMessagesDesc(domain.getId(), pageable);

		if (entitiesDesc.isEmpty()) {
			return Collections.emptyList();
		}

		// Reverse to chronological order (oldest to newest)
		List<DomainChatMessageEntity> entitiesAsc = new ArrayList<>(entitiesDesc);
		Collections.reverse(entitiesAsc);

		return mapMessages(entitiesAsc, currentUserId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<DomainChatMessageDto> getNewMessagesAfter(String slug, Long afterId, Long currentUserId) {
		DomainCommunityEntity domain = findOrCreateDomainBySlug(slug);
		List<DomainChatMessageEntity> entitiesAsc = messageRepository.findMessagesAfterAsc(domain.getId(), afterId);
		return mapMessages(entitiesAsc, currentUserId);
	}

	@Override
	public DomainChatMessageDto sendMessage(String slug, Long userId, SendDomainMessageRequest request) {
		DomainCommunityEntity domain = findOrCreateDomainBySlug(slug);
		UserEntity senderEntity = userRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("User not found"));

		// Enforce community participation block
		if (blockRepository.isUserBlocked(userId)) {
			throw new ForbiddenException("You are blocked from participating in community discussions.");
		}

		// Enforce community membership rule
		boolean isMember = membershipRepository.existsByDomainIdAndUserId(domain.getId(), userId);
		if (!isMember) {
			throw new ForbiddenException("You must join this domain community before sending messages");
		}

		String content = request.content().trim();
		if (content.isEmpty()) {
			throw new BadRequestException("Message content cannot be blank");
		}

		DomainChatMessageEntity message = new DomainChatMessageEntity(
				domain.getId(),
				userId,
				MessageType.CHAT,
				content
		);
		message = messageRepository.save(message);

		// Process @username mentions
		processMentions(domain, senderEntity, content);

		PublicProfileDto profile = userService.getPublicProfiles(List.of(userId)).get(userId);
		CommunityAuthorDto senderDto = toAuthorDto(senderEntity, profile);

		DomainChatMessageDto dto = new DomainChatMessageDto(
				message.getId(),
				domain.getId(),
				senderDto,
				message.getMessageType(),
				message.getContent(),
				message.getCreatedAt(),
				true
		);

		try {
			messagingTemplate.convertAndSend("/topic/community.domain." + slug,
					new CommunityRealtimeEvent<>(CommunityRealtimeEvent.MESSAGE_CREATED, dto));
			messagingTemplate.convertAndSend("/topic/admin.community",
					new CommunityRealtimeEvent<>("DOMAIN_MESSAGE_CREATED",
							Map.of("domainSlug", slug, "domainId", domain.getId(), "message", dto)));
		} catch (Exception ignored) {
		}

		return dto;
	}

	@Override
	public void deleteMessage(String slug, Long messageId, Long currentUserId, boolean isAdmin) {
		DomainCommunityEntity domain = findOrCreateDomainBySlug(slug);
		DomainChatMessageEntity message = messageRepository.findById(messageId)
				.orElseThrow(() -> new NotFoundException("Message not found: " + messageId));

		boolean isAuthor = message.getSenderId() != null && message.getSenderId().equals(currentUserId);
		if (!isAdmin && !isAuthor) {
			throw new ForbiddenException("You do not have permission to delete this message.");
		}

		messageRepository.delete(message);

		auditService.record(new AuditEvent(
				currentUserId,
				AuditAction.COMMUNITY_MESSAGE_DELETED,
				"DOMAIN_COMMUNITY_MESSAGE",
				Long.toString(messageId),
				Map.of(
						"domainSlug", slug,
						"domainId", domain.getId(),
						"deletedBy", currentUserId,
						"isAdmin", isAdmin)));

		try {
			messagingTemplate.convertAndSend("/topic/community.domain." + slug,
					new CommunityRealtimeEvent<>(CommunityRealtimeEvent.MESSAGE_DELETED,
							Map.of("messageId", messageId, "domainSlug", slug)));
			messagingTemplate.convertAndSend("/topic/admin.community",
					new CommunityRealtimeEvent<>(CommunityRealtimeEvent.MESSAGE_DELETED,
							Map.of("messageId", messageId, "domainSlug", slug, "deletedBy", currentUserId)));
		} catch (Exception ignored) {
		}
	}

	private void processMentions(DomainCommunityEntity domain, UserEntity sender, String content) {
		Matcher matcher = MENTION_PATTERN.matcher(content);
		Set<String> usernames = new HashSet<>();
		while (matcher.find()) {
			usernames.add(matcher.group(1));
		}

		if (usernames.isEmpty()) return;

		for (String username : usernames) {
			userRepository.findByUsername(username).ifPresent(mentioned -> {
				// Don't notify self
				if (!mentioned.getId().equals(sender.getId())) {
					String senderName = sender.getDisplayName() != null ? sender.getDisplayName() : sender.getUsername();
					String domainName = domain.getCategory().getName();
					String slug = domain.getCategory().getSlug();
					String snippet = content.length() > 80 ? content.substring(0, 77) + "..." : content;
					String title = "New mention in " + domainName;
					String msg = senderName + " (@" + sender.getUsername() + ") mentioned you: \"" + snippet + "\"";
					String actionUrl = "/domains/" + slug + "/community";
					String eventKey = "mention:domain:" + domain.getId() + ":user:" + mentioned.getId() + ":msg:" + System.currentTimeMillis();

					try {
						notificationService.createNotification(
								mentioned.getId(),
								NotificationType.COMMUNITY_MENTION,
								title,
								msg,
								"DOMAIN_COMMUNITY",
								domain.getId(),
								actionUrl,
								eventKey
						);
					} catch (Exception ignored) {
					}
				}
			});
		}
	}

	private List<DomainChatMessageDto> mapMessages(List<DomainChatMessageEntity> entities, Long currentUserId) {
		Set<Long> senderIds = new HashSet<>();
		for (DomainChatMessageEntity msg : entities) {
			if (msg.getSenderId() != null) {
				senderIds.add(msg.getSenderId());
			}
		}

		Map<Long, PublicProfileDto> profileMap = senderIds.isEmpty()
				? Collections.emptyMap()
				: userService.getPublicProfiles(senderIds);

		return entities.stream().map(msg -> {
			CommunityAuthorDto sender = null;
			if (msg.getSenderId() != null) {
				PublicProfileDto profile = profileMap.get(msg.getSenderId());
				String username = profile != null ? profile.username() : "user_" + msg.getSenderId();
				String displayName = profile != null ? profile.displayName() : username;
				String avatarUrl = profile != null ? profile.avatarUrl() : null;
				sender = new CommunityAuthorDto(msg.getSenderId(), displayName, username, avatarUrl);
			}

			boolean isSender = currentUserId != null && currentUserId.equals(msg.getSenderId());

			return new DomainChatMessageDto(
					msg.getId(),
					msg.getDomainId(),
					sender,
					msg.getMessageType(),
					msg.getContent(),
					msg.getCreatedAt(),
					isSender
			);
		}).toList();
	}

	private DomainCommunityEntity findOrCreateDomainBySlug(String slug) {
		return domainCommunityRepository.findByCategorySlug(slug).orElseGet(() -> {
			// Find category by slug
			CategoryEntity category = categoryRepository.findAll().stream()
					.filter(c -> c.getSlug().equalsIgnoreCase(slug))
					.findFirst()
					.orElseThrow(() -> new NotFoundException("Domain category not found: " + slug));

			DomainCommunityEntity domain = new DomainCommunityEntity(
					category,
					"Learn, discuss, and collaborate with fellow learners in " + category.getName() + "."
			);
			return domainCommunityRepository.save(domain);
		});
	}

	private DomainCommunityDto toDto(DomainCommunityEntity domain, long memberCount, boolean isMember) {
		return new DomainCommunityDto(
				domain.getId(),
				domain.getCategory().getId(),
				domain.getCategory().getName(),
				domain.getCategory().getSlug(),
				domain.getDescription(),
				memberCount,
				isMember
		);
	}

	private CommunityAuthorDto toAuthorDto(UserEntity user, PublicProfileDto profile) {
		String username = profile != null ? profile.username() : user.getUsername();
		String displayName = profile != null ? profile.displayName() : (user.getDisplayName() != null ? user.getDisplayName() : username);
		String avatarUrl = profile != null ? profile.avatarUrl() : null;
		return new CommunityAuthorDto(user.getId(), displayName, username, avatarUrl);
	}
}
