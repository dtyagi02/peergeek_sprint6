package com.peergeek.courselearning.analytics;

import java.util.List;

public record InstructorCourseAnalyticsDto(
        long courseId,
        String courseTitle,
        long totalEnrollments,
        long activeLearners,
        long completedLearners,
        double completionRate,
        Double averageAssessmentScore,
        Double averageRating,
        long ratingCount,
        List<LessonDropOffDto> lessonDropOff,
        List<ScoreBucketDto> assessmentScoreDistribution,
        List<AssignmentPerformanceDto> assignmentPerformance) {}
