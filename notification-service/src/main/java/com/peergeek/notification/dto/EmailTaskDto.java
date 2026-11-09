package com.peergeek.notification.dto;

import java.io.Serializable;
import java.util.Map;

public record EmailTaskDto(
    String taskId,
    String recipientEmail,
    String recipientName,
    String subject,
    String bodyText,
    String bodyHtml,
    String correlationId,
    Map<String, Object> parameters
) implements Serializable {}

