package com.peergeek.notification.rabbitmq;

import com.peergeek.notification.config.RabbitMQConfig;
import com.peergeek.notification.dto.EmailTaskDto;
import com.rabbitmq.client.Channel;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class EmailDispatchWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchWorker.class);

    private final JavaMailSender mailSender;
    private final String mailFrom;

    public EmailDispatchWorker(
            JavaMailSender mailSender,
            @Value("${peergeek.mail.from:no-reply@peergeek.local}") String mailFrom) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
    }

    @RabbitListener(queues = RabbitMQConfig.EMAIL_DISPATCH_QUEUE)
    public void processEmailTask(
            @Payload EmailTaskDto task,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {

        log.info("Processing email task {} for recipient {}", task.taskId(), task.recipientEmail());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(task.recipientEmail());
            helper.setFrom(mailFrom);
            helper.setSubject(task.subject());

            String text = task.bodyText() != null ? task.bodyText() : "";
            String html = task.bodyHtml() != null ? task.bodyHtml() : text;
            helper.setText(text, html);

            mailSender.send(message);
            log.info("Successfully dispatched email task {} to {}", task.taskId(), task.recipientEmail());

            // Acknowledge receipt upon successful 250 OK dispatch
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("Failed to deliver email task {}: {}", task.taskId(), ex.getMessage(), ex);
            // Requeue or forward to DLX based on error
            channel.basicNack(deliveryTag, false, false); // NACK without requeue routes to DLQ via x-dead-letter-exchange
        }
    }
}

