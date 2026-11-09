package com.peergeek.courselearning.enrollment;

import com.peergeek.courselearning.common.EnrollmentStatus;
import java.time.Instant;

public record EnrollmentDto(
        Long courseId,
        EnrollmentStatus status,
        Instant enrolledAt,
        CourseProgressDto progress
) {}

