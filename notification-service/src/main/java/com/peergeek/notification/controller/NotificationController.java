package com.peergeek.notification.controller;

import com.peergeek.notification.dto.NotificationDto;
import com.peergeek.notification.dto.PageResponse;
import com.peergeek.notification.dto.UnreadCountDto;
import com.peergeek.notification.security.CurrentUser;
import com.peergeek.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<NotificationDto>> listNotifications(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size,
            @RequestParam(value = "unreadOnly", required = false) Boolean unreadOnly) {

        Long userId = requireUserId(currentUser);
        PageResponse<NotificationDto> notifications =
                notificationService.getUserNotifications(userId, unreadOnly, page, size);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountDto> getUnreadCount(
            @AuthenticationPrincipal CurrentUser currentUser) {

        Long userId = requireUserId(currentUser);
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(new UnreadCountDto(count));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markAsRead(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable("id") Long id) {

        Long userId = requireUserId(currentUser);
        NotificationDto updated = notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Map<String, Object>> markAllAsRead(
            @AuthenticationPrincipal CurrentUser currentUser) {

        Long userId = requireUserId(currentUser);
        int markedCount = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("markedCount", markedCount));
    }

    private Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return currentUser.id();
    }
}

