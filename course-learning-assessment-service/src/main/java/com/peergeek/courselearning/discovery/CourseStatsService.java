package com.peergeek.courselearning.discovery;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseStatsService implements CourseStatsRecorder {
    private final CoursePublicStatsRepository statsRepository;

    public CourseStatsService(CoursePublicStatsRepository statsRepository) {
        this.statsRepository = statsRepository;
    }

    @Override
    @Transactional
    public void recordEnrollment(long courseId) {
        CoursePublicStatsEntity entity = statsRepository.findById(courseId)
                .orElseGet(() -> new CoursePublicStatsEntity(courseId, 0, 0, null, 0, Instant.now()));
        int current = entity.getEnrollmentCount() != null ? entity.getEnrollmentCount() : 0;
        entity.setEnrollmentCount(current + 1);
        entity.setUpdatedAt(Instant.now());
        statsRepository.save(entity);
    }

    @Override
    @Transactional
    public void recordCompletion(long courseId) {
        CoursePublicStatsEntity entity = statsRepository.findById(courseId)
                .orElseGet(() -> new CoursePublicStatsEntity(courseId, 0, 0, null, 0, Instant.now()));
        int current = entity.getCompletionCount() != null ? entity.getCompletionCount() : 0;
        entity.setCompletionCount(current + 1);
        entity.setUpdatedAt(Instant.now());
        statsRepository.save(entity);
    }

    @Override
    @Transactional
    public void recordRating(long courseId, BigDecimal ratingAverage, int ratingCount) {
        CoursePublicStatsEntity entity = statsRepository.findById(courseId)
                .orElseGet(() -> new CoursePublicStatsEntity(courseId, 0, 0, null, 0, Instant.now()));
        entity.setRatingAverage(ratingAverage);
        entity.setRatingCount(ratingCount);
        entity.setUpdatedAt(Instant.now());
        statsRepository.save(entity);
    }
}
