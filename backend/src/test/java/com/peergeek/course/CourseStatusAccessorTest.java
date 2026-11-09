package com.peergeek.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CourseStatusAccessorTest {
	@Test
	void returnsTheRepositorySnapshot() {
		CourseRepository courses = mock(CourseRepository.class);
		CourseStatusSnapshot snapshot =
				new CourseStatusSnapshot(7L, 3L, CourseStatus.DRAFT, false, 2L, "Draft");
		when(courses.findStatusSnapshot(7L)).thenReturn(Optional.of(snapshot));

		assertThat(new CourseStatusAccessor(courses).getStatusSnapshot(7L))
				.contains(snapshot);
	}

	@Test
	void delegatesConditionalTransitionAndSkipsEmptyAllowedStates() {
		CourseRepository courses = mock(CourseRepository.class);
		CourseStatusAccessor accessor = new CourseStatusAccessor(courses);
		Instant publishedAt = Instant.parse("2026-10-16T12:00:00Z");
		when(courses.transitionStatus(7L, Set.of(CourseStatus.DRAFT),
				CourseStatus.PUBLISHED, publishedAt)).thenReturn(1);

		assertThat(accessor.transitionStatus(7L, Set.of(CourseStatus.DRAFT),
				CourseStatus.PUBLISHED, publishedAt)).isEqualTo(1);
		assertThat(accessor.transitionStatus(7L, Set.of(),
				CourseStatus.PUBLISHED, publishedAt)).isZero();
		verify(courses).transitionStatus(7L, Set.of(CourseStatus.DRAFT),
				CourseStatus.PUBLISHED, publishedAt);
		verify(courses, never()).transitionStatus(7L, Set.of(),
				CourseStatus.PUBLISHED, publishedAt);
	}
}
