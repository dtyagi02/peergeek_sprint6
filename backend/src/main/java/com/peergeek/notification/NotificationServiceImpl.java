package com.peergeek.notification;

import com.peergeek.common.PageResponse;
import com.peergeek.exception.NotFoundException;
import com.peergeek.notification.dto.NotificationDto;
import com.peergeek.outbox.OutboxEventWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

	private final NotificationRepository notificationRepository;
	private final Clock clock;
	private final ObjectProvider<OutboxEventWriter> outboxEventWriterProvider;

	public NotificationServiceImpl(
			NotificationRepository notificationRepository,
			Clock clock,
			ObjectProvider<OutboxEventWriter> outboxEventWriterProvider) {
		this.notificationRepository = notificationRepository;
		this.clock = clock;
		this.outboxEventWriterProvider = outboxEventWriterProvider;
	}

	@Override
	@Transactional
	public NotificationEntity createNotification(
			Long userId,
			NotificationType type,
			String title,
			String message,
			String resourceType,
			Long resourceId,
			String actionUrl,
			String eventKey) {
		if (eventKey != null && notificationRepository.existsByUserIdAndEventKey(userId, eventKey)) {
			log.info("Duplicate notification prevented for user {} with eventKey {}", userId, eventKey);
			return null;
		}

		NotificationEntity entity = new NotificationEntity(
				userId,
				type,
				title,
				message,
				resourceType,
				resourceId,
				actionUrl,
				Instant.now(clock),
				eventKey);

		try {
			NotificationEntity saved = notificationRepository.save(entity);
			OutboxEventWriter writer = outboxEventWriterProvider.getIfAvailable();
			if (writer != null && saved != null) {
				Map<String, Object> payload = new HashMap<>();
				payload.put("id", saved.getId());
				payload.put("userId", userId);
				payload.put("type", type.name());
				payload.put("title", title);
				payload.put("message", message);
				payload.put("resourceType", resourceType);
				payload.put("resourceId", resourceId);
				payload.put("actionUrl", actionUrl);
				payload.put("eventKey", eventKey);
				payload.put("createdAt", saved.getCreatedAt().toString());
				writer.recordEvent("Notification", String.valueOf(saved.getId()), "NotificationCreated", "peergeek.notification.created.v1", payload);
			}
			return saved;
		} catch (DataIntegrityViolationException e) {
			log.info("Data integrity duplicate prevented for user {} eventKey {}", userId, eventKey);
			return null;
		}
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<NotificationDto> getUserNotifications(
			Long userId, Boolean unreadOnly, int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		PageRequest pageRequest = PageRequest.of(safePage, safeSize);

		Page<NotificationEntity> entities;
		if (Boolean.TRUE.equals(unreadOnly)) {
			entities = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageRequest);
		} else {
			entities = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);
		}

		return PageResponse.from(entities.map(this::toDto));
	}

	@Override
	@Transactional(readOnly = true)
	public long getUnreadCount(Long userId) {
		return notificationRepository.countByUserIdAndIsReadFalse(userId);
	}

	@Override
	public NotificationDto markAsRead(Long notificationId, Long userId) {
		NotificationEntity notification = notificationRepository.findByIdAndUserId(notificationId, userId)
				.orElseThrow(() -> new NotFoundException("Notification not found"));

		if (!notification.isRead()) {
			notification.setRead(true);
			notification.setReadAt(Instant.now(clock));
			notification = notificationRepository.save(notification);
		}

		return toDto(notification);
	}

	@Override
	public int markAllAsRead(Long userId) {
		return notificationRepository.markAllAsRead(userId, Instant.now(clock));
	}

	private NotificationDto toDto(NotificationEntity entity) {
		return new NotificationDto(
				entity.getId(),
				entity.getType(),
				entity.getTitle(),
				entity.getMessage(),
				entity.getResourceType(),
				entity.getResourceId(),
				entity.getActionUrl(),
				entity.isRead(),
				entity.getCreatedAt(),
				entity.getReadAt());
	}
}
