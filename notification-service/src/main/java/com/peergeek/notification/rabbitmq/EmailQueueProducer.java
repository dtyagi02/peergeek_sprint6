package com.peergeek.notification.rabbitmq;

import com.peergeek.notification.config.RabbitMQConfig;
import com.peergeek.notification.dto.EmailTaskDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class EmailQueueProducer implements EmailProducer {

    private static final Logger log = LoggerFactory.getLogger(EmailQueueProducer.class);
    private final RabbitTemplate rabbitTemplate;

    public EmailQueueProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void enqueueEmail(EmailTaskDto task) {
        String effectiveTaskId = (task.taskId() != null && !task.taskId().isBlank())
                ? task.taskId()
                : "task_mail_" + UUID.randomUUID().toString().substring(0, 8);
        EmailTaskDto effectiveTask = task.taskId() != null ? task : new EmailTaskDto(
                effectiveTaskId,
                task.recipientEmail(),
                task.recipientName(),
                task.subject(),
                task.bodyText(),
                task.bodyHtml(),
                task.correlationId() != null ? task.correlationId() : UUID.randomUUID().toString(),
                task.parameters()
        );
        log.info("Enqueuing email task {} for recipient {}", effectiveTask.taskId(), effectiveTask.recipientEmail());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.DIRECT_EXCHANGE,
                RabbitMQConfig.EMAIL_ROUTING_KEY,
                effectiveTask);
    }

    public void sendTransactionalEmail(
            String recipientEmail,
            String recipientName,
            String subject,
            String bodyText,
            String bodyHtml,
            Map<String, Object> parameters) {

        String taskId = "task_mail_" + UUID.randomUUID().toString().substring(0, 8);
        EmailTaskDto task = new EmailTaskDto(
                taskId,
                recipientEmail,
                recipientName,
                subject,
                bodyText,
                bodyHtml,
                UUID.randomUUID().toString(),
                parameters);

        enqueueEmail(task);
    }
}

