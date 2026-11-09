package com.peergeek.enrollment;

import com.peergeek.discovery.CourseStatsRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StatsRefresher {

	private static final Logger log = LoggerFactory.getLogger(StatsRefresher.class);

	private final EnrollmentRepository enrollmentRepository;
	private final CourseStatsRecorder courseStatsRecorder;

	public StatsRefresher(
			EnrollmentRepository enrollmentRepository,
			CourseStatsRecorder courseStatsRecorder) {
		this.enrollmentRepository = enrollmentRepository;
		this.courseStatsRecorder = courseStatsRecorder;
	}

	@EventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onLearnerEnrolled(LearnerEnrolledEvent event) {
		refresh(event.getCourseId());
	}

	@EventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onLearnerWithdrawn(LearnerWithdrawnEvent event) {
		refresh(event.getCourseId());
	}

	@EventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onLessonCompleted(LessonCompletedEvent event) {
		refresh(event.getCourseId());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void refresh(long courseId) {
		try {
			int enrollmentCount = enrollmentRepository.countByCourseIdAndStatus(courseId, EnrollmentStatus.ACTIVE);
			int completionCount = enrollmentRepository.countCompletedByCourseId(courseId);
			courseStatsRecorder.recordEnrollmentStats(courseId, enrollmentCount, completionCount);
		} catch (Exception exception) {
			log.error("Failed to refresh course public stats courseId={}", courseId, exception);
		}
	}
}
