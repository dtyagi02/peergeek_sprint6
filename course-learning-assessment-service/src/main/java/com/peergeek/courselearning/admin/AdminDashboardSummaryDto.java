package com.peergeek.courselearning.admin;

public record AdminDashboardSummaryDto(
        long totalUsers,
        long totalStudents,
        long totalInstructors,
        long totalCourses,
        long publishedCourses,
        long openReportsCount,
        long totalActivitiesCount) {}
