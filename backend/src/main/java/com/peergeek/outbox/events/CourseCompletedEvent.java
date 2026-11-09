package com.peergeek.outbox.events;

public record CourseCompletedEvent(
        Long userId,
        Long courseId,
        String courseTitle,
        String certificateNumber,
        String email
) {}

