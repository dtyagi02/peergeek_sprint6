package com.peergeek.courselearning.review;

import java.time.Instant;

public record ReviewCreatedEvent(
        long reviewId,
        long courseId,
        long userId,
        long instructorId,
        int rating,
        String reviewText,
        Instant createdAt) {
}
