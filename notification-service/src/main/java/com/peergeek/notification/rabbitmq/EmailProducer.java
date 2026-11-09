package com.peergeek.notification.rabbitmq;

import com.peergeek.notification.dto.EmailTaskDto;
import java.util.Map;

public interface EmailProducer {
    void enqueueEmail(EmailTaskDto task);
    void sendTransactionalEmail(
            String recipientEmail,
            String recipientName,
            String subject,
            String bodyText,
            String bodyHtml,
            Map<String, Object> parameters);
}

