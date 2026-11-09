package com.peergeek.courselearning.instructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CourseStudentDetailDto(
        StudentSummary student,
        Long courseId,
        String courseTitle,
        String enrollmentStatus,
        Instant enrolledAt,
        Instant lastAccessedAt,
        int totalLessons,
        int completedLessons,
        int progressPercent,
        Instant completedAt,
        List<StudentSubmissionSummary> submissions) {

    public record StudentSummary(
            Long id,
            String username,
            String displayName,
            String headline,
            String bio,
            String avatarUrl) {}

    public record StudentSubmissionSummary(
            Long assignmentId,
            String assignmentTitle,
            String status,
            BigDecimal marks,
            BigDecimal totalPoints,
            Instant submittedAt,
            boolean late) {}
}
