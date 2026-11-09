package com.peergeek.notification.dto;

import com.peergeek.notification.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateNotificationRequest(
    @NotNull Long userId,
    @NotNull NotificationType type,
    @NotBlank String title,
    @NotBlank String message,
    String resourceType,
    Long resourceId,
    String actionUrl,
    String eventKey
) {}

