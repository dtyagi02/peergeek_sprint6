package com.peergeek.notification.kafka;

import com.peergeek.notification.entity.NotificationType;
import com.peergeek.notification.rabbitmq.EmailProducer;
import com.peergeek.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationKafkaConsumerTest {

    private NotificationService notificationService;
    private EmailProducer emailQueueProducer;
    private NotificationKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        emailQueueProducer = mock(EmailProducer.class);
        consumer = new NotificationKafkaConsumer(notificationService, emailQueueProducer);
    }

    @Test
    void onCoursePublishedCreatesNotification() {
        consumer.onCoursePublished(Map.of("instructorId", 10L, "courseId", 101L));
        verify(notificationService).createNotification(
                eq(10L), eq(NotificationType.COURSE_PUBLISHED), anyString(), anyString(), eq("COURSE"), eq(101L), anyString(), anyString());
    }

    @Test
    void onSubmissionGradedCreatesNotification() {
        consumer.onSubmissionGraded(Map.of(
                "learnerId", 20L, "submissionId", 201L, "courseId", 101L, "assignmentId", 5L, "isRegrade", false));
        verify(notificationService).createNotification(
                eq(20L), eq(NotificationType.SUBMISSION_GRADED), anyString(), anyString(), eq("ASSIGNMENT"), eq(5L), anyString(), anyString());
    }

    @Test
    void onCourseCompletedCreatesNotificationAndQueuesEmail() {
        consumer.onCourseCompleted(Map.of(
                "userId", 30L, "courseId", 101L, "courseTitle", "Java Microservices", "certificateNumber", "CERT-1234", "email", "learner@peergeek.com"));

        verify(notificationService).createNotification(
                eq(30L), eq(NotificationType.COURSE_COMPLETED), anyString(), anyString(), eq("CERTIFICATE"), eq(101L), anyString(), anyString());
        verify(emailQueueProducer).sendTransactionalEmail(
                eq("learner@peergeek.com"), eq("Learner"), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void onPaymentCapturedCreatesNotification() {
        consumer.onPaymentCaptured(Map.of("userId", 40L, "pgcAmount", "500.00", "razorpayOrderId", "order_rzp_999"));
        verify(notificationService).createNotification(
                eq(40L), eq(NotificationType.PAYMENT_CAPTURED), anyString(), anyString(), eq("WALLET"), isNull(), anyString(), eq("PAYMENT_CAPTURED:order_rzp_999"));
    }

    @Test
    void onWalletCreditedCreatesNotification() {
        consumer.onWalletCredited(Map.of("userId", 40L, "amount", "100.00", "description", "Referral Bonus"));
        verify(notificationService).createNotification(
                eq(40L), eq(NotificationType.WALLET_CREDITED), anyString(), anyString(), eq("WALLET"), isNull(), anyString(), anyString());
    }

    @Test
    void onUserRegisteredCreatesNotificationAndQueuesEmail() {
        consumer.onUserRegistered(Map.of("userId", 50L, "email", "newuser@example.com", "displayName", "Alice"));
        verify(notificationService).createNotification(
                eq(50L), eq(NotificationType.SYSTEM_ANNOUNCEMENT), anyString(), anyString(), eq("USER"), eq(50L), anyString(), eq("USER_REGISTERED:50"));
        verify(emailQueueProducer).sendTransactionalEmail(
                eq("newuser@example.com"), eq("Alice"), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void onReferralRewardedCreatesNotifications() {
        consumer.onReferralRewarded(Map.of("inviterId", 1L, "inviteeId", 2L, "inviterReward", "100", "inviteeReward", "50"));
        verify(notificationService).createNotification(
                eq(1L), eq(NotificationType.REFERRAL_SUCCESSFUL), anyString(), anyString(), eq("REFERRAL"), isNull(), anyString(), anyString());
        verify(notificationService).createNotification(
                eq(2L), eq(NotificationType.REFERRAL_WELCOME_REWARD), anyString(), anyString(), eq("REFERRAL"), isNull(), anyString(), anyString());
    }

    @Test
    void onDoubtSessionBookedCreatesNotification() {
        consumer.onDoubtSessionBooked(Map.of("instructorId", 10L, "sessionId", 888L, "sessionTitle", "Live Debugging"));
        verify(notificationService).createNotification(
                eq(10L), eq(NotificationType.DOUBT_SESSION_BOOKED), anyString(), anyString(), eq("DOUBT_SESSION"), eq(888L), anyString(), eq("DOUBT_SESSION_BOOKED:888"));
    }

    @Test
    void onReportCreatedCreatesNotification() {
        consumer.onReportCreated(Map.of("instructorId", 10L, "reportId", 777L, "courseId", 101L));
        verify(notificationService).createNotification(
                eq(10L), eq(NotificationType.REPORT_CREATED), anyString(), anyString(), eq("REPORT"), eq(777L), anyString(), eq("REPORT_CREATED:777"));
    }

    @Test
    void onReviewCreatedCreatesNotification() {
        consumer.onReviewCreated(Map.of("instructorId", 10L, "reviewId", 666L, "courseId", 101L, "rating", 5));
        verify(notificationService).createNotification(
                eq(10L), eq(NotificationType.REVIEW_CREATED), anyString(), anyString(), eq("REVIEW"), eq(666L), anyString(), eq("REVIEW_CREATED:666"));
    }

    @Test
    void onMonolithNotificationSyncCreatesNotification() {
        consumer.onMonolithNotificationCreated(Map.of(
                "userId", 60L,
                "type", "CERTIFICATE_ISSUED",
                "title", "Certificate Ready",
                "message", "Your certificate is available",
                "resourceType", "CERTIFICATE",
                "resourceId", 123L,
                "actionUrl", "/certificates",
                "eventKey", "cert-key-123"));

        verify(notificationService).createNotification(
                eq(60L), eq(NotificationType.CERTIFICATE_ISSUED), eq("Certificate Ready"), eq("Your certificate is available"),
                eq("CERTIFICATE"), eq(123L), eq("/certificates"), eq("cert-key-123"));
    }
}

