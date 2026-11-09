package com.peergeek.notification.service;

import com.peergeek.notification.dto.NotificationDto;
import com.peergeek.notification.dto.PageResponse;
import com.peergeek.notification.entity.NotificationEntity;
import com.peergeek.notification.entity.NotificationType;
import com.peergeek.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository repository;

    public NotificationServiceImpl(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public NotificationDto createNotification(
            Long userId,
            NotificationType type,
            String title,
            String message,
            String resourceType,
            Long resourceId,
            String actionUrl,
            String eventKey) {

        if (eventKey != null && repository.existsByUserIdAndEventKey(userId, eventKey)) {
            log.info("Duplicate notification ignored for user {} with eventKey {}", userId, eventKey);
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
                Instant.now(),
                eventKey);

        NotificationEntity saved = repository.save(entity);
        log.info("Created notification {} of type {} for user {}", saved.getId(), type, userId);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> getUserNotifications(
            Long userId,
            Boolean unreadOnly,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<NotificationEntity> entityPage;

        if (Boolean.TRUE.equals(unreadOnly)) {
            entityPage = repository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false, pageable);
        } else {
            entityPage = repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        List<NotificationDto> content = entityPage.getContent().stream()
                .map(this::toDto)
                .toList();

        return new PageResponse<>(
                content,
                entityPage.getNumber(),
                entityPage.getSize(),
                entityPage.getTotalElements(),
                entityPage.getTotalPages(),
                entityPage.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return repository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    public NotificationDto markAsRead(Long id, Long userId) {
        NotificationEntity entity = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found or access denied"));

        if (!entity.isRead()) {
            entity.markAsRead(Instant.now());
            repository.save(entity);
        }

        return toDto(entity);
    }

    @Override
    public int markAllAsRead(Long userId) {
        return repository.markAllAsReadForUser(userId, Instant.now());
    }

    private NotificationDto toDto(NotificationEntity entity) {
        return new NotificationDto(
                entity.getId(),
                entity.getUserId(),
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

