package com.peergeek.directmessage;

import com.peergeek.common.PageResponse;
import com.peergeek.community.dto.CommunityAuthorDto;
import com.peergeek.directmessage.dto.*;
import com.peergeek.exception.*;
import com.peergeek.notification.*;
import com.peergeek.user.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DirectMessageServiceImpl implements DirectMessageService {
	private final DirectConversationRepository conversations;
	private final DirectMessageRepository messages;
	private final DirectParticipantReadStateRepository reads;
	private final UserRepository users;
	private final NotificationService notifications;
	private final SimpMessagingTemplate messaging;
	public DirectMessageServiceImpl(DirectConversationRepository conversations, DirectMessageRepository messages,
			DirectParticipantReadStateRepository reads, UserRepository users, NotificationService notifications,
			SimpMessagingTemplate messaging) {
		this.conversations=conversations; this.messages=messages; this.reads=reads; this.users=users;
		this.notifications=notifications; this.messaging=messaging;
	}
	private UserEntity user(Long id) {
		return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
	}
	private DirectConversationEntity conversation(Long a, Long b, boolean create) {
		if (a.equals(b)) throw new BadRequestException("You cannot message yourself");
		Long low=Math.min(a,b), high=Math.max(a,b);
		Optional<DirectConversationEntity> existing=conversations.findBetween(low,high);
		if (existing.isPresent()) return existing.get();
		if (!create) throw new NotFoundException("Conversation not found");
		UserEntity first=user(low), second=user(high);
		if (!first.isEnabled() || !second.isEnabled()) throw new ForbiddenException("Direct messaging is unavailable for disabled users");
		return conversations.save(new DirectConversationEntity(first,second));
	}
	private void ensureEnabled(UserEntity u) { if (!u.isEnabled()) throw new ForbiddenException("Your account is disabled"); }
	private CommunityAuthorDto author(UserEntity u) { return new CommunityAuthorDto(u.getId(),u.getDisplayName(),u.getUsername(),u.getAvatarUrl()); }
	private DirectMessageDto dto(DirectMessageEntity m, Long current) {
		return new DirectMessageDto(m.getId(),m.getConversation().getId(),author(m.getSender()),m.getContent(),m.getCreatedAt(),m.getSender().getId().equals(current));
	}
	private DirectConversationDto conversationDto(DirectConversationEntity c, Long userId) {
		DirectMessageDto last=messages.findTopByConversationIdOrderByCreatedAtDesc(c.getId()).map(m->dto(m,userId)).orElse(null);
		Long read=reads.findByConversationIdAndUserId(c.getId(),userId)
				.map(DirectParticipantReadStateEntity::getLastReadMessageId).orElse(0L);
		long unread=messages.countByConversationIdAndIdGreaterThanAndSenderIdNot(
				c.getId(), read == null ? 0L : read, userId);
		return new DirectConversationDto(c.getId(),author(c.other(userId)),last,unread,c.getUpdatedAt());
	}
	private DirectConversationEntity participantConversation(Long userId, Long conversationId) {
		DirectConversationEntity c=conversations.findById(conversationId)
				.orElseThrow(() -> new NotFoundException("Conversation not found"));
		if (!c.hasParticipant(userId)) throw new ForbiddenException("You are not a participant");
		return c;
	}
	@Override
	public DirectConversationDto getOrCreateConversation(Long userId, Long otherUserId) {
		UserEntity current=user(userId);
		ensureEnabled(current);
		if (!user(otherUserId).isEnabled()) throw new ForbiddenException("Direct messaging is unavailable for disabled users");
		DirectConversationEntity c=conversation(userId,otherUserId,true);
		return conversationDto(c,userId);
	}
	@Override @Transactional(readOnly=true)
	public PageResponse<DirectMessageDto> getMessages(Long userId, Long otherUserId, int page, int size) {
		ensureEnabled(user(userId)); DirectConversationEntity c=conversation(userId,otherUserId,false);
		return getMessagesByConversation(userId,c.getId(),page,size);
	}
	@Override @Transactional(readOnly=true)
	public PageResponse<DirectMessageDto> getMessagesByConversation(Long userId, Long conversationId, int page, int size) {
		ensureEnabled(user(userId)); DirectConversationEntity c=participantConversation(userId,conversationId);
		Page<DirectMessageEntity> p=messages.findByConversationIdOrderByCreatedAtDesc(c.getId(),PageRequest.of(Math.max(0,page),Math.min(100,Math.max(1,size))));
		List<DirectMessageDto> list=p.getContent().stream().sorted(Comparator.comparing(DirectMessageEntity::getCreatedAt)).map(m->dto(m,userId)).toList();
		return new PageResponse<>(list,p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages());
	}
	@Override
	public DirectMessageDto send(Long userId, Long otherUserId, SendDirectMessageRequest request) {
		UserEntity sender=user(userId); UserEntity recipient=user(otherUserId); ensureEnabled(sender);
		if (!recipient.isEnabled()) throw new ForbiddenException("Recipient account is disabled");
		String content=request.content()==null?"":request.content().trim();
		if (content.isEmpty()) throw new BadRequestException("Message content cannot be blank");
		if (content.length()>2000) throw new BadRequestException("Message content cannot exceed 2000 characters");
		DirectConversationEntity c=conversation(userId,otherUserId,true);
		DirectMessageEntity saved=messages.save(new DirectMessageEntity(c,sender,content));
		DirectMessageDto result=dto(saved,userId);
		messaging.convertAndSendToUser(otherUserId.toString(),"/queue/direct-messages/"+c.getId(),result);
		notifications.createNotification(otherUserId,NotificationType.DIRECT_MESSAGE,"New direct message",
				sender.getDisplayName()+" sent you a message","DIRECT_MESSAGE",c.getId(),
				"/messages/"+c.getId(),"direct-message:"+saved.getId());
		return result;
	}
	@Override
	public DirectMessageDto sendToConversation(Long userId, Long conversationId, SendDirectMessageRequest request) {
		DirectConversationEntity c=conversations.findById(conversationId).orElseThrow(()->new NotFoundException("Conversation not found"));
		if (!c.hasParticipant(userId)) throw new ForbiddenException("You are not a participant");
		return send(userId,c.other(userId).getId(),request);
	}
	@Override @Transactional(readOnly=true)
	public List<DirectConversationDto> listConversations(Long userId) {
		ensureEnabled(user(userId));
		return conversations.findForUser(userId).stream().map(c -> conversationDto(c,userId)).toList();
	}
	@Override public void markRead(Long userId, Long otherUserId, Long messageId) {
		UserEntity u=user(userId); ensureEnabled(u); DirectConversationEntity c=conversation(userId,otherUserId,false);
		if (!c.hasParticipant(userId)) throw new ForbiddenException("You are not a participant");
		DirectMessageEntity m=messages.findById(messageId).orElseThrow(()->new NotFoundException("Message not found"));
		if (!m.getConversation().getId().equals(c.getId())) throw new ForbiddenException("Message is not in this conversation");
		DirectParticipantReadStateEntity state=reads.findByConversationIdAndUserId(c.getId(),userId).orElseGet(()->new DirectParticipantReadStateEntity(c,u));
		Long previous=state.getLastReadMessageId();
		if (previous != null && previous >= messageId) return;
		state.markRead(messageId,Instant.now()); reads.save(state);
	}
	@Override public void markReadLatest(Long userId, Long conversationId) {
		UserEntity u=user(userId); ensureEnabled(u);
		DirectConversationEntity c=participantConversation(userId,conversationId);
		messages.findTopByConversationIdOrderByCreatedAtDesc(c.getId())
				.ifPresent(m -> markRead(userId,c.other(userId).getId(),m.getId()));
	}
	@Override @Transactional(readOnly=true) public boolean isParticipant(Long userId, Long conversationId) {
		return conversations.findById(conversationId).map(c->c.hasParticipant(userId)).orElse(false);
	}
}
