package com.peergeek.notification;

import com.peergeek.common.PageResponse;
import com.peergeek.notification.dto.NotificationDto;
import com.peergeek.notification.dto.UnreadCountDto;
import com.peergeek.security.CurrentUserProvider;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

	private final NotificationService notificationService;
	private final CurrentUserProvider currentUserProvider;

	public NotificationController(
			NotificationService notificationService,
			CurrentUserProvider currentUserProvider) {
		this.notificationService = notificationService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping
	public ResponseEntity<PageResponse<NotificationDto>> listNotifications(
			@RequestParam(value = "page", required = false, defaultValue = "0") int page,
			@RequestParam(value = "size", required = false, defaultValue = "20") int size,
			@RequestParam(value = "unreadOnly", required = false) Boolean unreadOnly) {
		Long userId = currentUserProvider.requireCurrentUser().id();
		PageResponse<NotificationDto> notifications =
				notificationService.getUserNotifications(userId, unreadOnly, page, size);
		return ResponseEntity.ok(notifications);
	}

	@GetMapping("/unread-count")
	public ResponseEntity<UnreadCountDto> getUnreadCount() {
		Long userId = currentUserProvider.requireCurrentUser().id();
		long count = notificationService.getUnreadCount(userId);
		return ResponseEntity.ok(new UnreadCountDto(count));
	}

	@PatchMapping("/{id}/read")
	public ResponseEntity<NotificationDto> markAsRead(@PathVariable("id") Long id) {
		Long userId = currentUserProvider.requireCurrentUser().id();
		NotificationDto updated = notificationService.markAsRead(id, userId);
		return ResponseEntity.ok(updated);
	}

	@PostMapping("/mark-all-read")
	public ResponseEntity<Map<String, Object>> markAllAsRead() {
		Long userId = currentUserProvider.requireCurrentUser().id();
		int markedCount = notificationService.markAllAsRead(userId);
		return ResponseEntity.ok(Map.of("markedCount", markedCount));
	}
}
