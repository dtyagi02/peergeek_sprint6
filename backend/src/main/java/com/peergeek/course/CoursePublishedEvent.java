package com.peergeek.course;

import java.time.Instant;

public record CoursePublishedEvent(long courseId, long instructorId, Instant publishedAt) {
}
