package com.peergeek.notification.rabbitmq;

import com.peergeek.notification.dto.EmailTaskDto;
import com.rabbitmq.client.Channel;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;
import java.util.Properties;

import static org.mockito.Mockito.*;

class EmailDispatchWorkerIntegrationTest {

    private JavaMailSender mailSender;
    private EmailDispatchWorker worker;
    private Channel channel;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        MimeMessage realMimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage);
        channel = mock(Channel.class);
        worker = new EmailDispatchWorker(mailSender, "test@peergeek.local");
    }

    @Test
    void successfulEmailDeliveryManuallyAcked() throws Exception {
        EmailTaskDto task = new EmailTaskDto(
                "task-123",
                "testuser@example.com",
                "Test User",
                "Test Subject",
                "Hello text",
                "<p>Hello html</p>",
                "corr-123",
                Map.of()
        );

        long deliveryTag = 42L;
        worker.processEmailTask(task, channel, deliveryTag);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        verify(channel, times(1)).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void failedEmailDeliveryRoutesToDeadLetterViaNack() throws Exception {
        EmailTaskDto task = new EmailTaskDto(
                "task-failed",
                "invalid@example.com",
                "Invalid User",
                "Failing Subject",
                "Fail text",
                "<p>Fail html</p>",
                "corr-failed",
                Map.of()
        );

        doThrow(new RuntimeException("SMTP connection error")).when(mailSender).send(any(MimeMessage.class));

        long deliveryTag = 99L;
        worker.processEmailTask(task, channel, deliveryTag);

        // Verify basicNack with requeue=false (routes to x-dead-letter-exchange)
        verify(channel, times(1)).basicNack(deliveryTag, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
