package com.peergeek.community.directmessage;

import com.peergeek.community.common.PageResponse;
import com.peergeek.community.directmessage.dto.DirectConversationDto;
import com.peergeek.community.directmessage.dto.DirectMessageDto;
import com.peergeek.community.directmessage.dto.SendDirectMessageRequest;
import com.peergeek.community.exception.BadRequestException;
import com.peergeek.community.exception.ForbiddenException;
import com.peergeek.community.exception.NotFoundException;
import com.peergeek.community.outbox.OutboxEventWriter;
import com.peergeek.community.posts.dto.CommunityAuthorDto;
import com.peergeek.community.profile.CommunityUserProfileEntity;
import com.peergeek.community.profile.CommunityUserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class DirectMessageServiceImpl implements DirectMessageService {

	private static final Logger log = LoggerFactory.getLogger(DirectMessageServiceImpl.class);

	private final DirectConversationRepository conversations;
	private final DirectMessageRepository messages;
	private final DirectParticipantReadStateRepository reads;
	private final CommunityUserProfileRepository userProfiles;
	private final SimpMessagingTemplate messaging;
	private final OutboxEventWriter outboxEventWriter;

	public DirectMessageServiceImpl(
			DirectConversationRepository conversations,
			DirectMessageRepository messages,
			DirectParticipantReadStateRepository reads,
			CommunityUserProfileRepository userProfiles,
			SimpMessagingTemplate messaging,
			OutboxEventWriter outboxEventWriter) {
		this.conversations = conversations;
		this.messages = messages;
		this.reads = reads;
		this.userProfiles = userProfiles;
		this.messaging = messaging;
		this.outboxEventWriter = outboxEventWriter;
	}

	private CommunityUserProfileEntity user(Long id) {
		return userProfiles.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
	}

	private DirectConversationEntity conversation(Long a, Long b, boolean create) {
		if (a.equals(b)) {
			throw new BadRequestException("You cannot message yourself");
		}
		Long low = Math.min(a, b);
		Long high = Math.max(a, b);
		Optional<DirectConversationEntity> existing = conversations.findBetween(low, high);
		if (existing.isPresent()) {
			return existing.get();
		}
		if (!create) {
			throw new NotFoundException("Conversation not found");
		}
		CommunityUserProfileEntity first = user(low);
		CommunityUserProfileEntity second = user(high);
		if (!first.isEnabled() || !second.isEnabled()) {
			throw new ForbiddenException("Direct messaging is unavailable for disabled users");
		}
		return conversations.save(new DirectConversationEntity(low, high));
	}

	private void ensureEnabled(CommunityUserProfileEntity u) {
		if (!u.isEnabled()) {
			throw new ForbiddenException("Your account is disabled");
		}
	}

	private CommunityAuthorDto author(Long userId) {
		return userProfiles.findById(userId)
				.map(u -> new CommunityAuthorDto(u.getUserId(), u.getDisplayName(), u.getUsername(), u.getAvatarUrl()))
				.orElseGet(() -> new CommunityAuthorDto(userId, "User #" + userId, "user" + userId, null));
	}

	private DirectMessageDto dto(DirectMessageEntity m, Long currentUserId) {
		return new DirectMessageDto(
				m.getId(),
				m.getConversationId(),
				author(m.getSenderId()),
				m.getContent(),
				m.getCreatedAt(),
				m.getSenderId().equals(currentUserId));
	}

	private DirectConversationDto conversationDto(DirectConversationEntity c, Long userId) {
		DirectMessageDto last = messages.findTopByConversationIdOrderByCreatedAtDesc(c.getId())
				.map(m -> dto(m, userId)).orElse(null);
		Long read = reads.findByIdConversationIdAndIdUserId(c.getId(), userId)
				.map(DirectParticipantReadStateEntity::getLastReadMessageId).orElse(0L);
		long unread = messages.countByConversationIdAndIdGreaterThanAndSenderIdNot(
				c.getId(), read == null ? 0L : read, userId);
		return new DirectConversationDto(c.getId(), author(c.other(userId)), last, unread, c.getUpdatedAt());
	}

	private DirectConversationEntity participantConversation(Long userId, Long conversationId) {
		DirectConversationEntity c = conversations.findById(conversationId)
				.orElseThrow(() -> new NotFoundException("Conversation not found"));
		if (!c.hasParticipant(userId)) {
			throw new ForbiddenException("You are not a participant");
		}
		return c;
	}

	@Override
	public DirectConversationDto getOrCreateConversation(Long userId, Long otherUserId) {
		CommunityUserProfileEntity current = user(userId);
		ensureEnabled(current);
		CommunityUserProfileEntity recipient = user(otherUserId);
		if (!recipient.isEnabled()) {
			throw new ForbiddenException("Direct messaging is unavailable for disabled users");
		}
		DirectConversationEntity c = conversation(userId, otherUserId, true);
		return conversationDto(c, userId);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<DirectMessageDto> getMessages(Long userId, Long otherUserId, int page, int size) {
		ensureEnabled(user(userId));
		DirectConversationEntity c = conversation(userId, otherUserId, false);
		return getMessagesByConversation(userId, c.getId(), page, size);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<DirectMessageDto> getMessagesByConversation(Long userId, Long conversationId, int page, int size) {
		ensureEnabled(user(userId));
		DirectConversationEntity c = participantConversation(userId, conversationId);
		Page<DirectMessageEntity> p = messages.findByConversationIdOrderByCreatedAtDesc(
				c.getId(),
				PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
		List<DirectMessageDto> list = p.getContent().stream()
				.sorted(Comparator.comparing(DirectMessageEntity::getCreatedAt))
				.map(m -> dto(m, userId))
				.toList();
		return new PageResponse<>(list, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
	}

	@Override
	public DirectMessageDto send(Long userId, Long otherUserId, SendDirectMessageRequest request) {
		CommunityUserProfileEntity sender = user(userId);
		ensureEnabled(sender);
		CommunityUserProfileEntity recipient = user(otherUserId);
		if (!recipient.isEnabled()) {
			throw new ForbiddenException("Recipient account is disabled");
		}
		String content = request.content() == null ? "" : request.content().trim();
		if (content.isEmpty()) {
			throw new BadRequestException("Message content cannot be blank");
		}
		if (content.length() > 2000) {
			throw new BadRequestException("Message content cannot exceed 2000 characters");
		}
		DirectConversationEntity c = conversation(userId, otherUserId, true);
		c.setUpdatedAt(Instant.now());
		conversations.save(c);

		DirectMessageEntity saved = messages.save(new DirectMessageEntity(c.getId(), userId, content));
		DirectMessageDto result = dto(saved, userId);

		try {
			messaging.convertAndSendToUser(otherUserId.toString(), "/queue/direct-messages/" + c.getId(), result);
		} catch (Exception ex) {
			log.warn("Failed to push direct message over websocket: {}", ex.getMessage());
		}

		outboxEventWriter.writeEvent(
				"peergeek.community.direct-message-sent.v1",
				"DirectMessageSent",
				Map.of(
						"messageId", saved.getId(),
						"conversationId", c.getId(),
						"senderId", userId,
						"senderName", sender.getDisplayName(),
						"recipientId", otherUserId,
						"content", content,
						"createdAt", saved.getCreatedAt().toString()
				)
		);

		return result;
	}

	@Override
	public DirectMessageDto sendToConversation(Long userId, Long conversationId, SendDirectMessageRequest request) {
		DirectConversationEntity c = conversations.findById(conversationId)
				.orElseThrow(() -> new NotFoundException("Conversation not found"));
		if (!c.hasParticipant(userId)) {
			throw new ForbiddenException("You are not a participant");
		}
		return send(userId, c.other(userId), request);
	}

	@Override
	@Transactional(readOnly = true)
	public List<DirectConversationDto> listConversations(Long userId) {
		ensureEnabled(user(userId));
		return conversations.findForUser(userId).stream()
				.map(c -> conversationDto(c, userId))
				.toList();
	}

	@Override
	public void markRead(Long userId, Long otherUserId, Long messageId) {
		CommunityUserProfileEntity u = user(userId);
		ensureEnabled(u);
		DirectConversationEntity c = conversation(userId, otherUserId, false);
		if (!c.hasParticipant(userId)) {
			throw new ForbiddenException("You are not a participant");
		}
		DirectMessageEntity m = messages.findById(messageId)
				.orElseThrow(() -> new NotFoundException("Message not found"));
		if (!m.getConversationId().equals(c.getId())) {
			throw new ForbiddenException("Message is not in this conversation");
		}
		DirectParticipantReadStateEntity state = reads.findByIdConversationIdAndIdUserId(c.getId(), userId)
				.orElseGet(() -> new DirectParticipantReadStateEntity(c.getId(), userId));
		Long previous = state.getLastReadMessageId();
		if (previous != null && previous >= messageId) {
			return;
		}
		state.markRead(messageId, Instant.now());
		reads.save(state);
	}

	@Override
	public void markReadLatest(Long userId, Long conversationId) {
		CommunityUserProfileEntity u = user(userId);
		ensureEnabled(u);
		DirectConversationEntity c = participantConversation(userId, conversationId);
		messages.findTopByConversationIdOrderByCreatedAtDesc(c.getId())
				.ifPresent(m -> markRead(userId, c.other(userId), m.getId()));
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isParticipant(Long userId, Long conversationId) {
		return conversations.findById(conversationId).map(c -> c.hasParticipant(userId)).orElse(false);
	}
}

