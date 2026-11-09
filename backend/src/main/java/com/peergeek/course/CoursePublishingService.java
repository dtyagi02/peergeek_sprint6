package com.peergeek.course;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.readiness.ReadinessService;
import com.peergeek.security.CurrentUserProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoursePublishingService {
	private final CourseStatusAccessor statuses;
	private final CourseRepository courses;
	private final ReadinessService readiness;
	private final CurrentUserProvider users;
	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final ApplicationEventPublisher events;

	public CoursePublishingService(CourseStatusAccessor statuses, CourseRepository courses,
			ReadinessService readiness, CurrentUserProvider users, JdbcTemplate jdbc,
			AuditService audit, ApplicationEventPublisher events) {
		this.statuses = statuses;
		this.courses = courses;
		this.readiness = readiness;
		this.users = users;
		this.jdbc = jdbc;
		this.audit = audit;
		this.events = events;
	}

	@Transactional
	public CourseStatusSnapshot publish(long courseId) {
		long actorId = users.requireCurrentUser().id();
		CourseStatusSnapshot snapshot = ownedSnapshot(courseId, actorId);
		lockCourse(courseId);
		snapshot = ownedSnapshot(courseId, actorId);
		if (snapshot.status() == CourseStatus.PUBLISHED) return snapshot;
		readiness.assertFreshPassingRun(courseId);
		int changed = statuses.transitionStatus(courseId,
				Set.of(CourseStatus.DRAFT, CourseStatus.UNPUBLISHED), CourseStatus.PUBLISHED, Instant.now());
		if (changed != 1) {
			throw new CourseStateConflictException("COURSE_STATE_CHANGED",
					"Course status changed while publishing; reload and try again.");
		}
		Instant now = Instant.now();
		audit.record(new AuditEvent(actorId, "COURSE_PUBLISHED", "COURSE", Long.toString(courseId),
				Map.of("courseVersion", snapshot.version() + 1)));
		events.publishEvent(new CoursePublishedEvent(courseId, actorId, now));
		return statuses.getStatusSnapshot(courseId).orElseThrow();
	}

	@Transactional
	public CourseStatusSnapshot unpublish(long courseId) {
		long actorId = users.requireCurrentUser().id();
		CourseStatusSnapshot snapshot = ownedSnapshot(courseId, actorId);
		lockCourse(courseId);
		snapshot = ownedSnapshot(courseId, actorId);
		if (snapshot.status() == CourseStatus.UNPUBLISHED) return snapshot;
		int changed = statuses.transitionStatus(courseId, Set.of(CourseStatus.PUBLISHED),
				CourseStatus.UNPUBLISHED, Instant.now());
		if (changed != 1) {
			throw new CourseStateConflictException("COURSE_STATE_CHANGED",
					"Course status changed while unpublishing; reload and try again.");
		}
		Instant now = Instant.now();
		audit.record(new AuditEvent(actorId, "COURSE_UNPUBLISHED", "COURSE", Long.toString(courseId),
				Map.of("courseVersion", snapshot.version() + 1)));
		events.publishEvent(new CourseUnpublishedEvent(courseId, actorId, now));
		return statuses.getStatusSnapshot(courseId).orElseThrow();
	}

	private CourseStatusSnapshot ownedSnapshot(long courseId, long actorId) {
		CourseStatusSnapshot snapshot = statuses.getStatusSnapshot(courseId)
				.filter(value -> !value.deleted())
				.orElseThrow(() -> new com.peergeek.exception.NotFoundException(
						"The requested resource was not found"));
		if (!snapshot.ownerId().equals(actorId)) {
			throw new com.peergeek.exception.NotFoundException("The requested resource was not found");
		}
		return snapshot;
	}

	private void lockCourse(long courseId) {
		jdbc.update("INSERT INTO course_publish_locks (course_id, updated_at) VALUES (?, CURRENT_TIMESTAMP(6)) "
				+ "ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(6)", courseId);
		jdbc.queryForObject("SELECT course_id FROM course_publish_locks WHERE course_id = ? FOR UPDATE",
				Long.class, courseId);
		courses.findByIdAndDeletedAtIsNullForUpdate(courseId)
				.orElseThrow(() -> new com.peergeek.exception.NotFoundException(
						"The requested resource was not found"));
	}

	private void publishAfterCommit(Object event) {
		org.springframework.transaction.support.TransactionSynchronizationManager
				.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
					@Override
					public void afterCommit() {
						events.publishEvent(event);
					}
				});
	}
}
