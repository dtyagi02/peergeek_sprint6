package com.peergeek.course;

import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class CourseContentChangePublisher {
	private final ApplicationEventPublisher events;

	public CourseContentChangePublisher(ApplicationEventPublisher events) {
		this.events = events;
	}

	public void touch(CourseEntity course) {
		course.markContentUpdated(Instant.now());
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			throw new IllegalStateException("Course content changes require an active transaction");
		}
		Long courseId = course.getId();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				events.publishEvent(new CourseContentChangedEvent(CourseContentChangePublisher.this, courseId));
			}
		});
	}
}
