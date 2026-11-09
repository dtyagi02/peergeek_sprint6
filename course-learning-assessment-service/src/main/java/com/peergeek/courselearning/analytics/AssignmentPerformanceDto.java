package com.peergeek.courselearning.analytics;

public record AssignmentPerformanceDto(
        long assignmentId,
        String title,
        long totalSubmissions,
        long gradedSubmissions,
        Double averageScorePercentage) {}
