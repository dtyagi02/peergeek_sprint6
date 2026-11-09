package com.peergeek.notification.dto;

import com.peergeek.notification.entity.NotificationType;
import java.time.Instant;

public record NotificationDto(
    Long id,
    Long userId,
    NotificationType type,
    String title,
    String message,
    String resourceType,
    Long resourceId,
    String actionUrl,
    boolean isRead,
    Instant createdAt,
    Instant readAt
) {}

