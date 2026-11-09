package com.peergeek.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CourseContentChangePublisherTest {
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	private final CourseContentChangePublisher publisher = new CourseContentChangePublisher(events);

	@AfterEach
	void clearTransactionSynchronization() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void publishesOnlyAfterTheTransactionCommits() {
		CourseEntity course = mock(CourseEntity.class);
		when(course.getId()).thenReturn(42L);
		TransactionSynchronizationManager.initSynchronization();

		publisher.touch(course);

		verify(events, never()).publishEvent(any(CourseContentChangedEvent.class));
		TransactionSynchronizationManager.getSynchronizations()
				.forEach(TransactionSynchronization::afterCommit);

		var event = org.mockito.ArgumentCaptor.forClass(CourseContentChangedEvent.class);
		verify(events).publishEvent(event.capture());
		assertThat(event.getValue().getCourseId()).isEqualTo(42L);
	}

	@Test
	void refusesToScheduleAnEventWithoutAnActiveTransaction() {
		assertThatThrownBy(() -> publisher.touch(mock(CourseEntity.class)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Course content changes require an active transaction");
	}
}
