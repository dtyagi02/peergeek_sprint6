package com.peergeek.notification.kafka;

import com.peergeek.notification.entity.NotificationType;
import com.peergeek.notification.rabbitmq.EmailProducer;
import com.peergeek.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaConsumer.class);

    private final NotificationService notificationService;
    private final EmailProducer emailQueueProducer;

    public NotificationKafkaConsumer(
            NotificationService notificationService,
            EmailProducer emailQueueProducer) {
        this.notificationService = notificationService;
        this.emailQueueProducer = emailQueueProducer;
    }

    @KafkaListener(topics = "peergeek.course.course-published.v1", groupId = "notification-course-listener")
    public void onCoursePublished(@Payload Map<String, Object> event) {
        log.info("Received CoursePublished event: {}", event);
        Number instructorIdNum = (Number) event.get("instructorId");
        Number courseIdNum = (Number) event.get("courseId");
        if (instructorIdNum == null || courseIdNum == null) return;

        Long instructorId = instructorIdNum.longValue();
        Long courseId = courseIdNum.longValue();
        String title = "Course published successfully";
        String message = "Your course is now live and discoverable for learners.";
        String actionUrl = "/instructor/courses/" + courseId;
        String eventKey = "COURSE_PUBLISHED:" + courseId + ":" + System.currentTimeMillis();

        notificationService.createNotification(
                instructorId,
                NotificationType.COURSE_PUBLISHED,
                title,
                message,
                "COURSE",
                courseId,
                actionUrl,
                eventKey);
    }

    @KafkaListener(topics = "peergeek.course.course-unpublished.v1", groupId = "notification-course-listener")
    public void onCourseUnpublished(@Payload Map<String, Object> event) {
        log.info("Received CourseUnpublished event: {}", event);
        Number instructorIdNum = (Number) event.get("instructorId");
        Number courseIdNum = (Number) event.get("courseId");
        if (instructorIdNum == null || courseIdNum == null) return;

        Long instructorId = instructorIdNum.longValue();
        Long courseId = courseIdNum.longValue();
        String title = "Course unpublished";
        String message = "Your course has been unpublished from public discovery.";
        String actionUrl = "/instructor/courses/" + courseId;
        String eventKey = "COURSE_UNPUBLISHED:" + courseId + ":" + System.currentTimeMillis();

        notificationService.createNotification(
                instructorId,
                NotificationType.COURSE_UNPUBLISHED,
                title,
                message,
                "COURSE",
                courseId,
                actionUrl,
                eventKey);
    }

    @KafkaListener(topics = "peergeek.learning.submission-graded.v1", groupId = "notification-submission-listener")
    public void onSubmissionGraded(@Payload Map<String, Object> event) {
        log.info("Received SubmissionGraded event: {}", event);
        Number learnerIdNum = (Number) event.get("learnerId");
        Number submissionIdNum = (Number) event.get("submissionId");
        Number courseIdNum = (Number) event.get("courseId");
        Number assignmentIdNum = (Number) event.get("assignmentId");
        if (learnerIdNum == null || submissionIdNum == null) return;

        Long learnerId = learnerIdNum.longValue();
        Long submissionId = submissionIdNum.longValue();
        Long courseId = courseIdNum != null ? courseIdNum.longValue() : 0L;
        Long assignmentId = assignmentIdNum != null ? assignmentIdNum.longValue() : 0L;

        Boolean isRegrade = Boolean.TRUE.equals(event.get("isRegrade"));
        String title = isRegrade ? "Assignment submission regraded" : "Assignment submission graded";
        String message = "Your assignment submission has been evaluated.";
        String actionUrl = "/learning/courses/" + courseId + "/assignments/" + assignmentId;
        String eventKey = "SUBMISSION_GRADED:" + submissionId + (isRegrade ? ":REGRADE" : "");

        notificationService.createNotification(
                learnerId,
                NotificationType.SUBMISSION_GRADED,
                title,
                message,
                "ASSIGNMENT",
                assignmentId,
                actionUrl,
                eventKey);
    }

    @KafkaListener(topics = "peergeek.learning.course-completed.v1", groupId = "notification-completion-listener")
    public void onCourseCompleted(@Payload Map<String, Object> event) {
        log.info("Received CourseCompleted event: {}", event);
        Number userIdNum = (Number) event.get("userId");
        Number courseIdNum = (Number) event.get("courseId");
        if (userIdNum == null) return;

        Long userId = userIdNum.longValue();
        Long courseId = courseIdNum != null ? courseIdNum.longValue() : 0L;
        String courseTitle = (String) event.getOrDefault("courseTitle", "Course");
        String certNumber = (String) event.getOrDefault("certificateNumber", "");

        String title = "Congratulations on completing your course!";
        String message = "You completed \"" + courseTitle + "\"! Your Certificate (" + certNumber + ") is ready.";
        String actionUrl = "/learning/certificates";
        String eventKey = "COURSE_COMPLETED:" + courseId + ":" + userId;

        notificationService.createNotification(
                userId,
                NotificationType.COURSE_COMPLETED,
                title,
                message,
                "CERTIFICATE",
                courseId,
                actionUrl,
                eventKey);

        String email = (String) event.get("email");
        if (email != null && !email.isBlank()) {
            emailQueueProducer.sendTransactionalEmail(
                    email,
                    "Learner",
                    "Certificate of Completion - " + courseTitle,
                    "Congratulations on completing " + courseTitle + "! View your certificate in your PeerGeek profile.",
                    "<h1>Congratulations!</h1><p>You have successfully completed <strong>" + courseTitle + "</strong>.</p>",
                    Map.of("courseId", courseId, "certificateNumber", certNumber));
        }
    }

    @KafkaListener(topics = "peergeek.wallet.payment-captured.v1", groupId = "notification-payment-listener")
    public void onPaymentCaptured(@Payload Map<String, Object> event) {
        log.info("Received PaymentCaptured event: {}", event);
        Number userIdNum = (Number) event.get("userId");
        if (userIdNum == null) return;

        Long userId = userIdNum.longValue();
        Object pgcAmount = event.getOrDefault("pgcAmount", "0.00");
        String orderId = (String) event.getOrDefault("razorpayOrderId", "");

        String title = "GeekCoins Recharged Successfully";
        String message = "Your wallet has been credited with " + pgcAmount + " PGC.";
        String actionUrl = "/wallet";
        String eventKey = "PAYMENT_CAPTURED:" + orderId;

        notificationService.createNotification(
                userId,
                NotificationType.PAYMENT_CAPTURED,
                title,
                message,
                "WALLET",
                null,
                actionUrl,
                eventKey);
    }

    @KafkaListener(topics = "peergeek.wallet.wallet-credited.v1", groupId = "notification-wallet-listener")
    public void onWalletCredited(@Payload Map<String, Object> event) {
        log.info("Received WalletCredited event: {}", event);
        Number userIdNum = (Number) event.get("userId");
        if (userIdNum == null) return;

        Long userId = userIdNum.longValue();
        String amount = String.valueOf(event.getOrDefault("amount", "0"));
        String desc = (String) event.getOrDefault("description", "Wallet Credited");
        String eventKey = "WALLET_CREDITED:" + userId + ":" + System.currentTimeMillis();

        notificationService.createNotification(
                userId,
                NotificationType.WALLET_CREDITED,
                "GeekCoins Credited",
                desc + " (+" + amount + " PGC)",
                "WALLET",
                null,
                "/wallet",
                eventKey);
    }

    @KafkaListener(topics = "peergeek.identity.user-registered.v1", groupId = "notification-identity-listener")
    public void onUserRegistered(@Payload Map<String, Object> event) {
        log.info("Received UserRegistered event: {}", event);
        Number userIdNum = (Number) event.get("userId");
        if (userIdNum == null) return;

        Long userId = userIdNum.longValue();
        String displayName = (String) event.getOrDefault("displayName", "User");
        String email = (String) event.get("email");

        String title = "Welcome to PeerGeek!";
        String message = "Welcome " + displayName + "! Explore courses, join discussions, and earn GeekCoins.";
        String eventKey = "USER_REGISTERED:" + userId;

        notificationService.createNotification(
                userId,
                NotificationType.SYSTEM_ANNOUNCEMENT,
                title,
                message,
                "USER",
                userId,
                "/courses",
                eventKey);

        if (email != null && !email.isBlank()) {
            emailQueueProducer.sendTransactionalEmail(
                    email,
                    displayName,
                    "Welcome to PeerGeek!",
                    "Welcome to PeerGeek, " + displayName + "! We're thrilled to have you join our learning community.",
                    "<h1>Welcome to PeerGeek!</h1><p>Hi " + displayName + ", welcome to PeerGeek! Start exploring our courses.</p>",
                    Map.of("userId", userId));
        }
    }

    @KafkaListener(topics = "peergeek.growth.referral-rewarded.v1", groupId = "notification-referral-listener")
    public void onReferralRewarded(@Payload Map<String, Object> event) {
        log.info("Received ReferralRewarded event: {}", event);
        Number inviterIdNum = (Number) event.get("inviterId");
        Number inviteeIdNum = (Number) event.get("inviteeId");
        if (inviterIdNum != null) {
            String inviterReward = String.valueOf(event.getOrDefault("inviterReward", "0"));
            notificationService.createNotification(
                    inviterIdNum.longValue(),
                    NotificationType.REFERRAL_SUCCESSFUL,
                    "Referral Reward Earned!",
                    "A friend joined using your referral! You earned " + inviterReward + " GeekCoins.",
                    "REFERRAL",
                    null,
                    "/wallet",
                    "REFERRAL_REWARD_INVITER:" + inviterIdNum + ":" + System.currentTimeMillis());
        }
        if (inviteeIdNum != null) {
            String inviteeReward = String.valueOf(event.getOrDefault("inviteeReward", "0"));
            notificationService.createNotification(
                    inviteeIdNum.longValue(),
                    NotificationType.REFERRAL_WELCOME_REWARD,
                    "Welcome Referral Reward!",
                    "You earned " + inviteeReward + " GeekCoins as a welcome referral bonus.",
                    "REFERRAL",
                    null,
                    "/wallet",
                    "REFERRAL_REWARD_INVITEE:" + inviteeIdNum + ":" + System.currentTimeMillis());
        }
    }

    @KafkaListener(topics = "peergeek.doubtsession.session-booked.v1", groupId = "notification-doubtsession-listener")
    public void onDoubtSessionBooked(@Payload Map<String, Object> event) {
        log.info("Received DoubtSessionBooked event: {}", event);
        Number instructorIdNum = (Number) event.get("instructorId");
        Number sessionIdNum = (Number) event.get("sessionId");
        String sessionTitle = (String) event.getOrDefault("sessionTitle", "Live Doubt Session");

        if (instructorIdNum != null && sessionIdNum != null) {
            notificationService.createNotification(
                    instructorIdNum.longValue(),
                    NotificationType.DOUBT_SESSION_BOOKED,
                    "Doubt Session Scheduled",
                    "A student scheduled a doubt session: " + sessionTitle,
                    "DOUBT_SESSION",
                    sessionIdNum.longValue(),
                    "/instructor/doubt-sessions",
                    "DOUBT_SESSION_BOOKED:" + sessionIdNum);
        }
    }

    @KafkaListener(topics = "peergeek.notification.created.v1", groupId = "notification-monolith-sync-listener")
    public void onMonolithNotificationCreated(@Payload Map<String, Object> event) {
        log.info("Received direct monolith notification sync event: {}", event);
        Number userIdNum = (Number) event.get("userId");
        if (userIdNum == null) return;

        Long userId = userIdNum.longValue();
        String typeStr = (String) event.getOrDefault("type", "SYSTEM_ANNOUNCEMENT");
        NotificationType type;
        try {
            type = NotificationType.valueOf(typeStr);
        } catch (Exception e) {
            type = NotificationType.SYSTEM_ANNOUNCEMENT;
        }

        String title = (String) event.getOrDefault("title", "");
        String message = (String) event.getOrDefault("message", "");
        String resourceType = (String) event.get("resourceType");
        Number resourceIdNum = (Number) event.get("resourceId");
        Long resourceId = resourceIdNum != null ? resourceIdNum.longValue() : null;
        String actionUrl = (String) event.get("actionUrl");
        String eventKey = (String) event.get("eventKey");

        notificationService.createNotification(
                userId,
                type,
                title,
                message,
                resourceType,
                resourceId,
                actionUrl,
                eventKey);
    }

    @KafkaListener(topics = "peergeek.community.mention-created.v1", groupId = "notification-community-mention-listener")
    public void onCommunityMentionCreated(@Payload Map<String, Object> event) {
        log.info("Received CommunityMentionCreated event: {}", event);
        Number targetUserIdNum = (Number) event.get("targetUserId");
        if (targetUserIdNum == null) return;

        Long targetUserId = targetUserIdNum.longValue();
        String title = (String) event.getOrDefault("title", "New mention in Community");
        String message = (String) event.getOrDefault("message", "You were mentioned in the community.");
        String resourceType = (String) event.getOrDefault("resourceType", "COMMUNITY");
        Number resourceIdNum = (Number) event.get("resourceId");
        Long resourceId = resourceIdNum != null ? resourceIdNum.longValue() : 0L;
        String actionUrl = (String) event.getOrDefault("actionUrl", "/community");
        String eventKey = (String) event.getOrDefault("eventKey", "mention:" + targetUserId + ":" + System.currentTimeMillis());

        notificationService.createNotification(
                targetUserId,
                NotificationType.COMMUNITY_MENTION,
                title,
                message,
                resourceType,
                resourceId,
                actionUrl,
                eventKey);
    }

    @KafkaListener(topics = "peergeek.community.report-created.v1", groupId = "notification-report-listener")
    public void onReportCreated(@Payload Map<String, Object> event) {
        log.info("Received ReportCreated event: {}", event);
        Number instructorIdNum = (Number) event.get("instructorId");
        Number reportIdNum = (Number) event.get("reportId");
        Number courseIdNum = (Number) event.get("courseId");
        if (instructorIdNum == null || reportIdNum == null) return;

        Long instructorId = instructorIdNum.longValue();
        Long reportId = reportIdNum.longValue();
        Long courseId = courseIdNum != null ? courseIdNum.longValue() : 0L;

        String title = "Course content reported";
        String message = "A learner reported an issue on your course.";
        String actionUrl = "/instructor/courses/" + courseId + "/reports";
        String eventKey = "REPORT_CREATED:" + reportId;

        notificationService.createNotification(
                instructorId,
                NotificationType.REPORT_CREATED,
                title,
                message,
                "REPORT",
                reportId,
                actionUrl,
                eventKey);
    }

    @KafkaListener(topics = "peergeek.review.review-created.v1", groupId = "notification-review-listener")
    public void onReviewCreated(@Payload Map<String, Object> event) {
        log.info("Received ReviewCreated event: {}", event);
        Number instructorIdNum = (Number) event.get("instructorId");
        Number reviewIdNum = (Number) event.get("reviewId");
        Number courseIdNum = (Number) event.get("courseId");
        if (instructorIdNum == null || reviewIdNum == null) return;

        Long instructorId = instructorIdNum.longValue();
        Long reviewId = reviewIdNum.longValue();
        Long courseId = courseIdNum != null ? courseIdNum.longValue() : 0L;
        Object rating = event.getOrDefault("rating", 5);

        String title = "New course review received";
        String message = "A learner left a " + rating + "-star review on your course.";
        String actionUrl = "/courses/" + courseId;
        String eventKey = "REVIEW_CREATED:" + reviewId;

        notificationService.createNotification(
                instructorId,
                NotificationType.REVIEW_CREATED,
                title,
                message,
                "REVIEW",
                reviewId,
                actionUrl,
                eventKey);
    }
}
