package com.peergeek.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CourseStatsRecorderTest {

	private CoursePublicStatsRepository statsRepository;
	private DiscoveryCacheManager cacheManager;
	private CourseStatsService statsService;

	@BeforeEach
	void setUp() {
		statsRepository = mock(CoursePublicStatsRepository.class);
		cacheManager = mock(DiscoveryCacheManager.class);
		statsService = new CourseStatsService(statsRepository, cacheManager);
	}

	@Test
	void rejectsNegativeEnrollmentCount() {
		assertThatThrownBy(() -> statsService.recordEnrollmentStats(1L, -1, 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("enrollmentCount must be non-negative");
	}

	@Test
	void rejectsNegativeCompletionCount() {
		assertThatThrownBy(() -> statsService.recordEnrollmentStats(1L, 10, -1))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("completionCount must be non-negative");
	}

	@Test
	void rejectsCompletionCountGreaterThanEnrollmentCount() {
		assertThatThrownBy(() -> statsService.recordEnrollmentStats(1L, 10, 11))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("completionCount cannot exceed enrollmentCount");
	}

	@Test
	void recordsEnrollmentStatsSuccessfullyAndEvictsCache() {
		when(statsRepository.findById(1L)).thenReturn(Optional.empty());

		statsService.recordEnrollmentStats(1L, 50, 25);

		ArgumentCaptor<CoursePublicStatsEntity> captor = ArgumentCaptor.forClass(CoursePublicStatsEntity.class);
		verify(statsRepository).save(captor.capture());
		CoursePublicStatsEntity saved = captor.getValue();
		assertThat(saved.getCourseId()).isEqualTo(1L);
		assertThat(saved.getEnrollmentCount()).isEqualTo(50);
		assertThat(saved.getCompletionCount()).isEqualTo(25);
		assertThat(saved.getUpdatedAt()).isNotNull();

		verify(cacheManager).evictStats(1L);
	}

	@Test
	void rejectsRatingBelowOne() {
		assertThatThrownBy(() -> statsService.recordRatingStats(1L, new BigDecimal("0.99"), 5))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ratingAverage must be between 1.00 and 5.00");
	}

	@Test
	void rejectsRatingAboveFive() {
		assertThatThrownBy(() -> statsService.recordRatingStats(1L, new BigDecimal("5.01"), 5))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ratingAverage must be between 1.00 and 5.00");
	}

	@Test
	void rejectsNegativeRatingCount() {
		assertThatThrownBy(() -> statsService.recordRatingStats(1L, new BigDecimal("4.50"), -1))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ratingCount must be non-negative");
	}

	@Test
	void recordsRatingStatsSuccessfullyAndEvictsCache() {
		CoursePublicStatsEntity existing = new CoursePublicStatsEntity(1L, 100, 50, null, null, Instant.now());
		when(statsRepository.findById(1L)).thenReturn(Optional.of(existing));

		statsService.recordRatingStats(1L, new BigDecimal("4.85"), 12);

		ArgumentCaptor<CoursePublicStatsEntity> captor = ArgumentCaptor.forClass(CoursePublicStatsEntity.class);
		verify(statsRepository).save(captor.capture());
		CoursePublicStatsEntity saved = captor.getValue();
		assertThat(saved.getEnrollmentCount()).isEqualTo(100);
		assertThat(saved.getCompletionCount()).isEqualTo(50);
		assertThat(saved.getRatingAverage()).isEqualByComparingTo(new BigDecimal("4.85"));
		assertThat(saved.getRatingCount()).isEqualTo(12);

		verify(cacheManager).evictStats(1L);
	}

	@Test
	void cacheFailureDoesNotPropagateToCaller() {
		when(statsRepository.findById(1L)).thenReturn(Optional.empty());
		doThrow(new RuntimeException("Redis connection error")).when(cacheManager).evictStats(anyLong());

		// Must not throw
		statsService.recordEnrollmentStats(1L, 10, 5);
		verify(statsRepository).save(any(CoursePublicStatsEntity.class));
	}
}
