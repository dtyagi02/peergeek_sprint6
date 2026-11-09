package com.peergeek.discovery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseStatsService implements CourseStatsRecorder {
	private static final Logger log = LoggerFactory.getLogger(CourseStatsService.class);
	private static final BigDecimal MIN_RATING = BigDecimal.valueOf(1.00);
	private static final BigDecimal MAX_RATING = BigDecimal.valueOf(5.00);

	private final CoursePublicStatsRepository statsRepository;
	private final DiscoveryCacheManager cacheManager;

	public CourseStatsService(
			CoursePublicStatsRepository statsRepository,
			DiscoveryCacheManager cacheManager) {
		this.statsRepository = statsRepository;
		this.cacheManager = cacheManager;
	}

	@Override
	@Transactional
	public void recordEnrollmentStats(long courseId, int enrollmentCount, int completionCount) {
		if (enrollmentCount < 0) {
			throw new IllegalArgumentException("enrollmentCount must be non-negative");
		}
		if (completionCount < 0) {
			throw new IllegalArgumentException("completionCount must be non-negative");
		}
		if (completionCount > enrollmentCount) {
			throw new IllegalArgumentException("completionCount cannot exceed enrollmentCount");
		}

		Instant now = Instant.now();
		CoursePublicStatsEntity entity = statsRepository.findById(courseId)
				.orElseGet(() -> new CoursePublicStatsEntity(courseId, null, null, null, null, now));

		entity.setEnrollmentCount(enrollmentCount);
		entity.setCompletionCount(completionCount);
		entity.setUpdatedAt(now);
		statsRepository.save(entity);

		try {
			cacheManager.evictStats(courseId);
		} catch (Exception exception) {
			log.warn("Cache eviction failed during enrollment stats recording courseId={}", courseId, exception);
		}
		log.info("Recorded enrollment stats courseId={}, enrollments={}, completions={}",
				courseId, enrollmentCount, completionCount);
	}

	@Override
	@Transactional
	public void recordRatingStats(long courseId, BigDecimal ratingAverage, int ratingCount) {
		if (ratingCount < 0) {
			throw new IllegalArgumentException("ratingCount must be non-negative");
		}
		BigDecimal scaledRating = null;
		if (ratingCount > 0) {
			if (ratingAverage == null) {
				throw new IllegalArgumentException("ratingAverage is required when ratingCount > 0");
			}
			if (ratingAverage.compareTo(MIN_RATING) < 0 || ratingAverage.compareTo(MAX_RATING) > 0) {
				throw new IllegalArgumentException("ratingAverage must be between 1.00 and 5.00");
			}
			scaledRating = ratingAverage.setScale(2, RoundingMode.HALF_UP);
		}

		Instant now = Instant.now();
		CoursePublicStatsEntity entity = statsRepository.findById(courseId)
				.orElseGet(() -> new CoursePublicStatsEntity(courseId, null, null, null, null, now));

		entity.setRatingAverage(scaledRating);
		entity.setRatingCount(ratingCount);
		entity.setUpdatedAt(now);
		statsRepository.save(entity);

		try {
			cacheManager.evictStats(courseId);
		} catch (Exception exception) {
			log.warn("Cache eviction failed during rating stats recording courseId={}", courseId, exception);
		}
		log.info("Recorded rating stats courseId={}, ratingAvg={}, ratings={}",
				courseId, scaledRating, ratingCount);
	}
}
