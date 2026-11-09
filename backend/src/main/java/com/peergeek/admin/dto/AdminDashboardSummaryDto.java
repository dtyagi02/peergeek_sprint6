package com.peergeek.admin.dto;

public record AdminDashboardSummaryDto(
		long totalUsers,
		long totalStudents,
		long totalInstructors,
		long totalCourses,
		long publishedCourses,
		long openReportsCount,
		long totalActivitiesCount) {
}
