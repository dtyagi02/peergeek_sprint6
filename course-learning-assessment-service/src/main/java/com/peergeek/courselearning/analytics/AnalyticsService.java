package com.peergeek.courselearning.analytics;

public interface AnalyticsService {
    InstructorCourseAnalyticsDto getInstructorCourseAnalytics(long courseId, long instructorId);
}
