package com.peergeek.notification;

import com.peergeek.common.PageResponse;
import com.peergeek.notification.dto.NotificationDto;

public interface NotificationService {

	NotificationEntity createNotification(
			Long userId,
			NotificationType type,
			String title,
			String message,
			String resourceType,
			Long resourceId,
			String actionUrl,
			String eventKey);

	PageResponse<NotificationDto> getUserNotifications(Long userId, Boolean unreadOnly, int page, int size);

	long getUnreadCount(Long userId);

	NotificationDto markAsRead(Long notificationId, Long userId);

	int markAllAsRead(Long userId);
}
