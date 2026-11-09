package com.peergeek.notification.controller;

import com.peergeek.notification.dto.CreateNotificationRequest;
import com.peergeek.notification.dto.EmailTaskDto;
import com.peergeek.notification.dto.NotificationDto;
import com.peergeek.notification.rabbitmq.EmailQueueProducer;
import com.peergeek.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications/internal")
public class InternalNotificationController {

    private final NotificationService notificationService;
    private final EmailQueueProducer emailQueueProducer;

    public InternalNotificationController(
            NotificationService notificationService,
            EmailQueueProducer emailQueueProducer) {
        this.notificationService = notificationService;
        this.emailQueueProducer = emailQueueProducer;
    }

    @PostMapping
    public ResponseEntity<NotificationDto> createNotification(
            @Valid @RequestBody CreateNotificationRequest request) {

        NotificationDto dto = notificationService.createNotification(
                request.userId(),
                request.type(),
                request.title(),
                request.message(),
                request.resourceType(),
                request.resourceId(),
                request.actionUrl(),
                request.eventKey());

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/email")
    public ResponseEntity<Map<String, String>> enqueueEmail(
            @Valid @RequestBody EmailTaskDto emailTask) {

        String taskId = (emailTask.taskId() != null && !emailTask.taskId().isBlank())
                ? emailTask.taskId()
                : "task_mail_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        EmailTaskDto effectiveTask = emailTask.taskId() != null ? emailTask : new EmailTaskDto(
                taskId,
                emailTask.recipientEmail(),
                emailTask.recipientName(),
                emailTask.subject(),
                emailTask.bodyText(),
                emailTask.bodyHtml(),
                emailTask.correlationId() != null ? emailTask.correlationId() : java.util.UUID.randomUUID().toString(),
                emailTask.parameters()
        );

        emailQueueProducer.enqueueEmail(effectiveTask);
        return ResponseEntity.accepted().body(Map.of("status", "QUEUED", "taskId", taskId));
    }
}

