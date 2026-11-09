package com.peergeek.community.domain;

import com.peergeek.community.common.PageResponse;
import com.peergeek.community.domain.dto.DomainChatMessageDto;
import com.peergeek.community.domain.dto.DomainCommunityDto;
import com.peergeek.community.domain.dto.DomainMemberDto;
import com.peergeek.community.domain.dto.SendDomainMessageRequest;
import com.peergeek.community.exception.BadRequestException;
import com.peergeek.community.exception.ForbiddenException;
import com.peergeek.community.exception.NotFoundException;
import com.peergeek.community.global.GlobalCommunityBlockRepository;
import com.peergeek.community.outbox.OutboxEventWriter;
import com.peergeek.community.posts.dto.CommunityAuthorDto;
import com.peergeek.community.profile.CommunityUserProfileEntity;
import com.peergeek.community.profile.CommunityUserProfileRepository;
import com.peergeek.community.websocket.CommunityRealtimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class DomainCommunityServiceImpl implements DomainCommunityService {

	private static final Logger log = LoggerFactory.getLogger(DomainCommunityServiceImpl.class);
	private static final Pattern MENTION_PATTERN = Pattern.compile("(?<!\\w)@([a-zA-Z0-9_]{3,30})\\b");

	private final DomainCommunityRepository domainCommunityRepository;
	private final DomainCommunityMembershipRepository membershipRepository;
	private final DomainChatMessageRepository messageRepository;
	private final CommunityUserProfileRepository userProfileRepository;
	private final GlobalCommunityBlockRepository blockRepository;
	private final OutboxEventWriter outboxEventWriter;
	private final SimpMessagingTemplate messagingTemplate;

	public DomainCommunityServiceImpl(
			DomainCommunityRepository domainCommunityRepository,
			DomainCommunityMembershipRepository membershipRepository,
			DomainChatMessageRepository messageRepository,
			CommunityUserProfileRepository userProfileRepository,
			GlobalCommunityBlockRepository blockRepository,
			OutboxEventWriter outboxEventWriter,
			SimpMessagingTemplate messagingTemplate) {
		this.domainCommunityRepository = domainCommunityRepository;
		this.membershipRepository = membershipRepository;
		this.messageRepository = messageRepository;
		this.userProfileRepository = userProfileRepository;
		this.blockRepository = blockRepository;
		this.outboxEventWriter = outboxEventWriter;
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	@Transactional(readOnly = true)
	public DomainCommunityDto getDomainCommunity(String slug, Long currentUserId) {
		DomainCommunityEntity domain = findDomainBySlug(slug);
		long memberCount = membershipRepository.countByDomainId(domain.getId());
		boolean isMember = currentUserId != null && membershipRepository.existsByDomainIdAndUserId(domain.getId(), currentUserId);

		return toDto(domain, memberCount, isMember);
	}

	@Override
	@Transactional(readOnly = true)
	public List<DomainCommunityDto> listDomainCommunities(Long currentUserId) {
		List<DomainCommunityEntity> domains = domainCommunityRepository.findAllByOrderByCreatedAtAsc();
		return domains.stream().map(domain -> {
			long memberCount = membershipRepository.countByDomainId(domain.getId());
			boolean isMember = currentUserId != null && membershipRepository.existsByDomainIdAndUserId(domain.getId(), currentUserId);
			return toDto(domain, memberCount, isMember);
		}).toList();
	}

	@Override
	public DomainCommunityDto joinCommunity(String slug, Long userId) {
		DomainCommunityEntity domain = findDomainBySlug(slug);
		CommunityUserProfileEntity user = userProfileRepository.findById(userId).orElse(null);

		boolean alreadyMember = membershipRepository.existsByDomainIdAndUserId(domain.getId(), userId);
		if (!alreadyMember) {
			membershipRepository.save(new DomainCommunityMembershipEntity(domain.getId(), userId, Instant.now()));

			String displayName = user != null ? user.getDisplayName() : "user_" + userId;
			String systemContent = displayName + " joined " + domain.getName() + ".";
			messageRepository.save(new DomainChatMessageEntity(
					domain.getId(),
					userId,
					displayName,
					user != null ? user.getUsername() : "user_" + userId,
					user != null ? user.getAvatarUrl() : null,
					MessageType.SYSTEM_JOIN,
					systemContent
			));

			outboxEventWriter.writeEvent(
					"peergeek.community.member-joined.v1",
					"DomainMemberJoined",
					Map.of(
							"domainId", domain.getId(),
							"domainSlug", domain.getSlug(),
							"userId", userId,
							"joinedAt", Instant.now().toString()
					)
			);
		}

		long memberCount = membershipRepository.countByDomainId(domain.getId());
		return toDto(domain, memberCount, true);
	}

	@Override
	public DomainCommunityDto leaveCommunity(String slug, Long userId) {
		DomainCommunityEntity domain = findDomainBySlug(slug);
		membershipRepository.deleteByDomainIdAndUserId(domain.getId(), userId);

		outboxEventWriter.writeEvent(
				"peergeek.community.member-left.v1",
				"DomainMemberLeft",
				Map.of(
						"domainId", domain.getId(),
						"domainSlug", domain.getSlug(),
						"userId", userId,
						"leftAt", Instant.now().toString()
				)
		);

		long memberCount = membershipRepository.countByDomainId(domain.getId());
		return toDto(domain, memberCount, false);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<DomainMemberDto> getMembers(String slug, Pageable pageable) {
		DomainCommunityEntity domain = findDomainBySlug(slug);
		Page<DomainCommunityMembershipEntity> membershipPage = membershipRepository.findByDomainIdOrderByJoinedAtDesc(domain.getId(), pageable);

		List<DomainMemberDto> members = membershipPage.getContent().stream().map(m -> {
			CommunityUserProfileEntity profile = userProfileRepository.findById(m.getUserId()).orElse(null);
			String username = profile != null ? profile.getUsername() : "user_" + m.getUserId();
			String displayName = profile != null ? profile.getDisplayName() : username;
			String avatarUrl = profile != null ? profile.getAvatarUrl() : null;

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
		DomainCommunityEntity domain = findDomainBySlug(slug);
		int safeLimit = Math.min(100, Math.max(1, limit));
		Pageable pageable = PageRequest.of(0, safeLimit);

		List<DomainChatMessageEntity> entitiesDesc = beforeId != null
				? messageRepository.findMessagesBeforeDesc(domain.getId(), beforeId, pageable)
				: messageRepository.findRecentMessagesDesc(domain.getId(), pageable);

		if (entitiesDesc.isEmpty()) {
			return Collections.emptyList();
		}

		List<DomainChatMessageEntity> entitiesAsc = new ArrayList<>(entitiesDesc);
		Collections.reverse(entitiesAsc);

		return mapMessages(entitiesAsc, currentUserId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<DomainChatMessageDto> getNewMessagesAfter(String slug, Long afterId, Long currentUserId) {
		DomainCommunityEntity domain = findDomainBySlug(slug);
		List<DomainChatMessageEntity> entitiesAsc = messageRepository.findMessagesAfterAsc(domain.getId(), afterId);
		return mapMessages(entitiesAsc, currentUserId);
	}

	@Override
	public DomainChatMessageDto sendMessage(String slug, Long userId, SendDomainMessageRequest request) {
		DomainCommunityEntity domain = findDomainBySlug(slug);

		if (blockRepository.isUserBlocked(userId)) {
			throw new ForbiddenException("You are blocked from participating in community discussions.");
		}

		boolean isMember = membershipRepository.existsByDomainIdAndUserId(domain.getId(), userId);
		if (!isMember) {
			throw new ForbiddenException("You must join this domain community before sending messages");
		}

		String content = request.content().trim();
		if (content.isEmpty()) {
			throw new BadRequestException("Message content cannot be blank");
		}

		CommunityUserProfileEntity sender = userProfileRepository.findById(userId).orElse(null);
		String senderName = sender != null ? sender.getDisplayName() : "User " + userId;
		String senderUsername = sender != null ? sender.getUsername() : "user_" + userId;
		String senderAvatar = sender != null ? sender.getAvatarUrl() : null;

		DomainChatMessageEntity message = new DomainChatMessageEntity(
				domain.getId(),
				userId,
				senderName,
				senderUsername,
				senderAvatar,
				MessageType.CHAT,
				content
		);
		message = messageRepository.save(message);

		// Process @username mentions
		processMentions(domain, userId, senderName, senderUsername, content);

		CommunityAuthorDto senderDto = new CommunityAuthorDto(userId, senderName, senderUsername, senderAvatar);
		DomainChatMessageDto dto = new DomainChatMessageDto(
				message.getId(),
				domain.getId(),
				senderDto,
				message.getMessageType(),
				message.getContent(),
				message.getCreatedAt(),
				true
		);

		outboxEventWriter.writeEvent(
				"peergeek.community.message-created.v1",
				"DomainMessageCreated",
				Map.of(
						"channel", "domain",
						"domainId", domain.getId(),
						"domainSlug", slug,
						"messageId", message.getId(),
						"senderId", userId,
						"senderName", senderName,
						"content", content,
						"createdAt", message.getCreatedAt().toString()
				)
		);

		try {
			messagingTemplate.convertAndSend("/topic/community.domain." + slug,
					new CommunityRealtimeEvent<>(CommunityRealtimeEvent.MESSAGE_CREATED, dto));
			messagingTemplate.convertAndSend("/topic/admin.community",
					new CommunityRealtimeEvent<>("DOMAIN_MESSAGE_CREATED",
							Map.of("domainSlug", slug, "domainId", domain.getId(), "message", dto)));
		} catch (Exception ex) {
			log.warn("Failed to broadcast domain message via STOMP: {}", ex.getMessage());
		}

		return dto;
	}

	@Override
	public void deleteMessage(String slug, Long messageId, Long currentUserId, boolean isAdmin) {
		DomainCommunityEntity domain = findDomainBySlug(slug);
		DomainChatMessageEntity message = messageRepository.findById(messageId)
				.orElseThrow(() -> new NotFoundException("Message not found: " + messageId));

		boolean isAuthor = message.getSenderId() != null && message.getSenderId().equals(currentUserId);
		if (!isAdmin && !isAuthor) {
			throw new ForbiddenException("You do not have permission to delete this message.");
		}

		messageRepository.delete(message);

		outboxEventWriter.writeEvent(
				"peergeek.community.message-deleted.v1",
				"DomainMessageDeleted",
				Map.of(
						"channel", "domain",
						"domainId", domain.getId(),
						"domainSlug", slug,
						"messageId", messageId,
						"deletedBy", currentUserId,
						"isAdmin", isAdmin,
						"deletedAt", Instant.now().toString()
				)
		);

		try {
			messagingTemplate.convertAndSend("/topic/community.domain." + slug,
					new CommunityRealtimeEvent<>(CommunityRealtimeEvent.MESSAGE_DELETED,
							Map.of("messageId", messageId, "domainSlug", slug)));
			messagingTemplate.convertAndSend("/topic/admin.community",
					new CommunityRealtimeEvent<>(CommunityRealtimeEvent.MESSAGE_DELETED,
							Map.of("messageId", messageId, "domainSlug", slug, "deletedBy", currentUserId)));
		} catch (Exception ex) {
			log.warn("Failed to broadcast domain message deletion via STOMP: {}", ex.getMessage());
		}
	}

	private void processMentions(DomainCommunityEntity domain, Long senderId, String senderName, String senderUsername, String content) {
		Matcher matcher = MENTION_PATTERN.matcher(content);
		Set<String> usernames = new HashSet<>();
		while (matcher.find()) {
			usernames.add(matcher.group(1));
		}

		for (String username : usernames) {
			userProfileRepository.findByUsername(username).ifPresent(mentioned -> {
				if (!mentioned.getUserId().equals(senderId)) {
					String snippet = content.length() > 80 ? content.substring(0, 77) + "..." : content;
					String title = "New mention in " + domain.getName();
					String msg = senderName + " (@" + senderUsername + ") mentioned you: \"" + snippet + "\"";
					String actionUrl = "/domains/" + domain.getSlug() + "/community";
					String eventKey = "mention:domain:" + domain.getId() + ":user:" + mentioned.getUserId() + ":msg:" + System.currentTimeMillis();

					outboxEventWriter.writeEvent(
							"peergeek.community.mention-created.v1",
							"CommunityMentionCreated",
							Map.of(
									"targetUserId", mentioned.getUserId(),
									"title", title,
									"message", msg,
									"resourceType", "DOMAIN_COMMUNITY",
									"resourceId", domain.getId(),
									"actionUrl", actionUrl,
									"eventKey", eventKey
							)
					);
				}
			});
		}
	}

	private List<DomainChatMessageDto> mapMessages(List<DomainChatMessageEntity> entities, Long currentUserId) {
		return entities.stream().map(msg -> {
			CommunityAuthorDto sender = null;
			if (msg.getSenderId() != null) {
				String displayName = msg.getSenderName() != null ? msg.getSenderName() : "user_" + msg.getSenderId();
				String username = msg.getSenderUsername() != null ? msg.getSenderUsername() : "user_" + msg.getSenderId();
				sender = new CommunityAuthorDto(msg.getSenderId(), displayName, username, msg.getSenderAvatarUrl());
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

	private DomainCommunityEntity findDomainBySlug(String slug) {
		return domainCommunityRepository.findBySlug(slug)
				.orElseThrow(() -> new NotFoundException("Domain community not found: " + slug));
	}

	private DomainCommunityDto toDto(DomainCommunityEntity domain, long memberCount, boolean isMember) {
		return new DomainCommunityDto(
				domain.getId(),
				domain.getCategoryId(),
				domain.getName(),
				domain.getSlug(),
				domain.getDescription(),
				memberCount,
				isMember
		);
	}
}

