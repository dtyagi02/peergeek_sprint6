package com.peergeek.notification.service;

import com.peergeek.notification.dto.NotificationDto;
import com.peergeek.notification.dto.PageResponse;
import com.peergeek.notification.entity.NotificationType;

public interface NotificationService {

    NotificationDto createNotification(
            Long userId,
            NotificationType type,
            String title,
            String message,
            String resourceType,
            Long resourceId,
            String actionUrl,
            String eventKey);

    PageResponse<NotificationDto> getUserNotifications(
            Long userId,
            Boolean unreadOnly,
            int page,
            int size);

    long getUnreadCount(Long userId);

    NotificationDto markAsRead(Long id, Long userId);

    int markAllAsRead(Long userId);
}

