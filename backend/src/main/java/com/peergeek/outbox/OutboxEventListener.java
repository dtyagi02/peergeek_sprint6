package com.peergeek.outbox;

import com.peergeek.course.CoursePublishedEvent;
import com.peergeek.course.CourseUnpublishedEvent;
import com.peergeek.outbox.events.*;
import com.peergeek.report.ReportCreatedEvent;
import com.peergeek.review.ReviewCreatedEvent;
import com.peergeek.submission.SubmissionGradedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Captures domain events and persists them to outbox_events in the SAME transaction
 * before commit.
 */
@Component
public class OutboxEventListener {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventListener.class);

    private final OutboxEventWriter outboxEventWriter;

    public OutboxEventListener(OutboxEventWriter outboxEventWriter) {
        this.outboxEventWriter = outboxEventWriter;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onCoursePublished(CoursePublishedEvent event) {
        log.debug("Recording outbox event for CoursePublishedEvent {}", event.courseId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("courseId", event.courseId());
        payload.put("instructorId", event.instructorId());
        payload.put("publishedAt", event.publishedAt().toString());

        outboxEventWriter.recordEvent(
                "Course",
                String.valueOf(event.courseId()),
                "CoursePublished",
                "peergeek.course.course-published.v1",
                payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onCourseUnpublished(CourseUnpublishedEvent event) {
        log.debug("Recording outbox event for CourseUnpublishedEvent {}", event.courseId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("courseId", event.courseId());
        payload.put("instructorId", event.instructorId());
        payload.put("unpublishedAt", event.unpublishedAt().toString());

        outboxEventWriter.recordEvent(
                "Course",
                String.valueOf(event.courseId()),
                "CourseUnpublished",
                "peergeek.course.course-unpublished.v1",
                payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onSubmissionGraded(SubmissionGradedEvent event) {
        log.debug("Recording outbox event for SubmissionGradedEvent {}", event.getSubmissionId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("submissionId", event.getSubmissionId());
        payload.put("assignmentId", event.getAssignmentId());
        payload.put("courseId", event.getCourseId());
        payload.put("learnerId", event.getLearnerId());
        payload.put("totalPoints", event.getTotalPoints());
        payload.put("maxPoints", event.getMaxPoints());
        payload.put("isRegrade", event.isRegrade());

        outboxEventWriter.recordEvent(
                "Submission",
                String.valueOf(event.getSubmissionId()),
                "SubmissionGraded",
                "peergeek.learning.submission-graded.v1",
                payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onCourseCompleted(CourseCompletedEvent event) {
        log.debug("Recording outbox event for CourseCompletedEvent courseId={} userId={}", event.courseId(), event.userId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", event.userId());
        payload.put("courseId", event.courseId());
        payload.put("courseTitle", event.courseTitle());
        payload.put("certificateNumber", event.certificateNumber());
        payload.put("email", event.email());

        outboxEventWriter.recordEvent(
                "Certificate",
                event.courseId() + ":" + event.userId(),
                "CourseCompleted",
                "peergeek.learning.course-completed.v1",
                payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        log.debug("Recording outbox event for PaymentCapturedEvent orderId={}", event.paymentOrderId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", event.userId());
        payload.put("paymentOrderId", event.paymentOrderId());
        payload.put("razorpayOrderId", event.razorpayOrderId());
        payload.put("pgcAmount", event.pgcAmount() != null ? event.pgcAmount().stripTrailingZeros().toPlainString() : "0");

        outboxEventWriter.recordEvent(
                "Payment",
                String.valueOf(event.paymentOrderId()),
                "PaymentCaptured",
                "peergeek.wallet.payment-captured.v1",
                payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onUserRegistered(UserRegisteredEvent event) {
        log.debug("Recording outbox event for UserRegisteredEvent userId={}", event.userId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", event.userId());
        payload.put("email", event.email());
        payload.put("displayName", event.displayName());

        outboxEventWriter.recordEvent(
                "User",
                String.valueOf(event.userId()),
                "UserRegistered",
                "peergeek.identity.user-registered.v1",
                payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onWalletCredited(WalletCreditedEvent event) {
        log.debug("Recording outbox event for WalletCreditedEvent userId={}", event.userId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", event.userId());
        payload.put("amount", event.amount() != null ? event.amount().stripTrailingZeros().toPlainString() : "0");
        payload.put("transactionType", event.transactionType());
        payload.put("referenceId", event.referenceId());
        payload.put("description", event.description());

        outboxEventWriter.recordEvent(
                "Wallet",
                String.valueOf(event.userId()),
                "WalletCredited",
                "peergeek.wallet.wallet-credited.v1",
                payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onReferralRewarded(ReferralRewardedEvent event) {
        log.debug("Recording outbox event for ReferralRewardedEvent inviterId={}", event.inviterId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("inviterId", event.inviterId());
        payload.put("inviteeId", event.inviteeId());
        payload.put("inviterReward", event.inviterReward() != null ? event.inviterReward().stripTrailingZeros().toPlainString() : "0");
        payload.put("inviteeReward", event.inviteeReward() != null ? event.inviteeReward().stripTrailingZeros().toPlainString() : "0");

        outboxEventWriter.recordEvent(
                "Referral",
                event.inviterId() + ":" + event.inviteeId(),
                "ReferralRewarded",
                "peergeek.growth.referral-rewarded.v1",
                payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onDoubtSessionBooked(DoubtSessionBookedEvent event) {
        log.debug("Recording outbox event for DoubtSessionBookedEvent sessionId={}", event.sessionId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", event.sessionId());
        payload.put("studentId", event.studentId());
        payload.put("instructorId", event.instructorId());
        payload.put("courseId", event.courseId());
        payload.put("sessionTitle", event.sessionTitle());

        outboxEventWriter.recordEvent(
                "DoubtSession",
                String.valueOf(event.sessionId()),
                "DoubtSessionBooked",
                "peergeek.doubtsession.session-booked.v1",
                payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onReportCreated(ReportCreatedEvent event) {
        log.debug("Recording outbox event for ReportCreatedEvent {}", event.reportId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("reportId", event.reportId());
        payload.put("courseId", event.courseId());
        payload.put("instructorId", event.instructorId());
        payload.put("category", event.category() != null ? event.category().name() : "OTHER");

        outboxEventWriter.recordEvent(
                "Report",
                String.valueOf(event.reportId()),
                "ReportCreated",
                "peergeek.community.report-created.v1",
                payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onReviewCreated(ReviewCreatedEvent event) {
        log.debug("Recording outbox event for ReviewCreatedEvent {}", event.reviewId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("reviewId", event.reviewId());
        payload.put("courseId", event.courseId());
        payload.put("instructorId", event.instructorId());
        payload.put("rating", event.rating());

        outboxEventWriter.recordEvent(
                "Review",
                String.valueOf(event.reviewId()),
                "ReviewCreated",
                "peergeek.review.review-created.v1",
                payload);
    }
}
