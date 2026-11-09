package com.peergeek.courselearning.discovery;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record CourseStatsDto(
        Integer enrollmentCount,
        Integer completionCount,
        Double completionRate,
        BigDecimal ratingAverage,
        Integer ratingCount) {

    public static CourseStatsDto of(
            Integer enrollmentCount,
            Integer completionCount,
            BigDecimal ratingAverage,
            Integer ratingCount,
            int minCompletionSample) {
        Double rate = null;
        if (enrollmentCount != null && enrollmentCount >= minCompletionSample && completionCount != null) {
            double raw = (double) completionCount / enrollmentCount;
            rate = BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }
        return new CourseStatsDto(enrollmentCount, completionCount, rate, ratingAverage, ratingCount);
    }
}
