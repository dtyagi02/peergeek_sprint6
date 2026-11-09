package com.peergeek.courselearning.discovery;

import java.math.BigDecimal;

public interface CourseStatsRecorder {
    void recordEnrollment(long courseId);
    void recordCompletion(long courseId);
    void recordRating(long courseId, BigDecimal ratingAverage, int ratingCount);
}
