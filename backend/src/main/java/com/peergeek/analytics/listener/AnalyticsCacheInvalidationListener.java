package com.peergeek.analytics.listener;

import com.peergeek.analytics.AnalyticsCacheService;
import com.peergeek.assignment.AssignmentPublishedEvent;
import com.peergeek.course.CourseContentChangedEvent;
import com.peergeek.enrollment.LearnerEnrolledEvent;
import com.peergeek.enrollment.LearnerWithdrawnEvent;
import com.peergeek.enrollment.LessonCompletedEvent;
import com.peergeek.review.ReviewCreatedEvent;
import com.peergeek.submission.SubmissionGradedEvent;
import com.peergeek.submission.SubmissionSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AnalyticsCacheInvalidationListener {

	private static final Logger log = LoggerFactory.getLogger(AnalyticsCacheInvalidationListener.class);

	private final AnalyticsCacheService analyticsCacheService;

	public AnalyticsCacheInvalidationListener(AnalyticsCacheService analyticsCacheService) {
		this.analyticsCacheService = analyticsCacheService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onLearnerEnrolled(LearnerEnrolledEvent event) {
		evictSafely(event.courseId(), "LearnerEnrolledEvent");
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onLearnerWithdrawn(LearnerWithdrawnEvent event) {
		evictSafely(event.courseId(), "LearnerWithdrawnEvent");
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onLessonCompleted(LessonCompletedEvent event) {
		evictSafely(event.courseId(), "LessonCompletedEvent");
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onSubmissionSubmitted(SubmissionSubmittedEvent event) {
		evictSafely(event.courseId(), "SubmissionSubmittedEvent");
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onSubmissionGraded(SubmissionGradedEvent event) {
		evictSafely(event.courseId(), "SubmissionGradedEvent");
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onAssignmentPublished(AssignmentPublishedEvent event) {
		evictSafely(event.courseId(), "AssignmentPublishedEvent");
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onReviewCreated(ReviewCreatedEvent event) {
		evictSafely(event.courseId(), "ReviewCreatedEvent");
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onCourseContentChanged(CourseContentChangedEvent event) {
		evictSafely(event.getCourseId(), "CourseContentChangedEvent");
	}

	private void evictSafely(Long courseId, String eventName) {
		if (courseId == null) {
			return;
		}
		try {
			log.debug("Invalidating analytics cache for courseId={} due to {}", courseId, eventName);
			analyticsCacheService.evictCourseAnalytics(courseId);
		} catch (Exception ex) {
			log.warn("Failed to invalidate analytics cache for courseId={} on {}: {}", courseId, eventName, ex.getMessage());
		}
	}
}
