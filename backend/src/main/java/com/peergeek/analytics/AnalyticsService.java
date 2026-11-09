package com.peergeek.analytics;

import com.peergeek.analytics.dto.InstructorCourseAnalyticsDto;

public interface AnalyticsService {

	InstructorCourseAnalyticsDto getInstructorCourseAnalytics(long courseId, long instructorId);
}
