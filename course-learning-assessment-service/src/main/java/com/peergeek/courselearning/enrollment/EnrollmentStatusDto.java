package com.peergeek.courselearning.enrollment;

import com.peergeek.courselearning.common.EnrollmentStatus;

public record EnrollmentStatusDto(
        boolean enrolled,
        EnrollmentStatus status,
        boolean isOwner,
        boolean canEnroll,
        CourseProgressDto progress
) {}

