package com.peergeek.discovery;

import java.math.BigDecimal;

public interface CourseStatsRecorder {
	void recordEnrollmentStats(long courseId, int enrollmentCount, int completionCount);
	void recordRatingStats(long courseId, BigDecimal ratingAverage, int ratingCount);
}
