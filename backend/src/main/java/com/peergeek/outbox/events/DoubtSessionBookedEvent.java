package com.peergeek.outbox.events;

public record DoubtSessionBookedEvent(
        Long sessionId,
        Long studentId,
        Long instructorId,
        Long courseId,
        String sessionTitle
) {}

