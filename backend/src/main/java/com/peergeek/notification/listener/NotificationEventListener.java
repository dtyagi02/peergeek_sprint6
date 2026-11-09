package com.peergeek.notification.listener;

import com.peergeek.course.CoursePublishedEvent;
import com.peergeek.course.CourseUnpublishedEvent;
import com.peergeek.notification.NotificationService;
import com.peergeek.notification.NotificationType;
import com.peergeek.report.ReportCreatedEvent;
import com.peergeek.review.ReviewCreatedEvent;
import com.peergeek.submission.SubmissionGradedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

	private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

	private final NotificationService notificationService;

	public NotificationEventListener(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onSubmissionGraded(SubmissionGradedEvent event) {
		log.info("Handling SubmissionGradedEvent for submission {}", event.getSubmissionId());
		String title = event.isRegrade() ? "Assignment submission regraded" : "Assignment submission graded";
		String scoreText = event.getTotalPoints() != null && event.getMaxPoints() != null
				? " Score: " + event.getTotalPoints() + " / " + event.getMaxPoints() + "."
				: "";
		String message = "Your assignment submission has been evaluated." + scoreText;
		String actionUrl = "/learning/courses/" + event.getCourseId() + "/assignments/" + event.getAssignmentId();
		String eventKey = "SUBMISSION_GRADED:" + event.getSubmissionId() + (event.isRegrade() ? ":REGRADE" : "");

		notificationService.createNotification(
				event.getLearnerId(),
				NotificationType.SUBMISSION_GRADED,
				title,
				message,
				"ASSIGNMENT",
				event.getAssignmentId(),
				actionUrl,
				eventKey);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onCoursePublished(CoursePublishedEvent event) {
		log.info("Handling CoursePublishedEvent for course {}", event.courseId());
		String title = "Course published successfully";
		String message = "Your course is now live and discoverable for learners.";
		String actionUrl = "/instructor/courses/" + event.courseId();
		String eventKey = "COURSE_PUBLISHED:" + event.courseId() + ":" + event.publishedAt().toEpochMilli();

		notificationService.createNotification(
				event.instructorId(),
				NotificationType.COURSE_PUBLISHED,
				title,
				message,
				"COURSE",
				event.courseId(),
				actionUrl,
				eventKey);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onCourseUnpublished(CourseUnpublishedEvent event) {
		log.info("Handling CourseUnpublishedEvent for course {}", event.courseId());
		String title = "Course unpublished";
		String message = "Your course has been unpublished from public discovery.";
		String actionUrl = "/instructor/courses/" + event.courseId();
		String eventKey = "COURSE_UNPUBLISHED:" + event.courseId() + ":" + event.unpublishedAt().toEpochMilli();

		notificationService.createNotification(
				event.instructorId(),
				NotificationType.COURSE_UNPUBLISHED,
				title,
				message,
				"COURSE",
				event.courseId(),
				actionUrl,
				eventKey);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onReportCreated(ReportCreatedEvent event) {
		log.info("Handling ReportCreatedEvent for report {}", event.reportId());
		String title = "Course content reported";
		String message = "A learner reported an issue (" + event.category().name() + ") on your course.";
		String actionUrl = "/instructor/courses/" + event.courseId() + "/reports";
		String eventKey = "REPORT_CREATED:" + event.reportId();

		notificationService.createNotification(
				event.instructorId(),
				NotificationType.REPORT_CREATED,
				title,
				message,
				"REPORT",
				event.reportId(),
				actionUrl,
				eventKey);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onReviewCreated(ReviewCreatedEvent event) {
		log.info("Handling ReviewCreatedEvent for review {}", event.reviewId());
		String title = "New course review received";
		String message = "A learner left a " + event.rating() + "-star review on your course.";
		String actionUrl = "/courses/" + event.courseId();
		String eventKey = "REVIEW_CREATED:" + event.reviewId();

		notificationService.createNotification(
				event.instructorId(),
				NotificationType.REVIEW_CREATED,
				title,
				message,
				"REVIEW",
				event.reviewId(),
				actionUrl,
				eventKey);
	}
}
