package com.peergeek.file;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ResourceEventPublisher {
	private final ApplicationEventPublisher events;

	public ResourceEventPublisher(ApplicationEventPublisher events) {
		this.events = events;
	}

	public void publishAfterCommit(
			Long courseId, Long lessonId, Long resourceId, ResourceChangeType changeType) {
		Runnable publish = () -> events.publishEvent(
				new ResourceChangedEvent(this, courseId, lessonId, resourceId, changeType));
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					publish.run();
				}
			});
		} else {
			publish.run();
		}
	}
}
