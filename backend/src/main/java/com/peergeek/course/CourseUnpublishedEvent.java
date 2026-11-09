package com.peergeek.course;

import java.time.Instant;

public record CourseUnpublishedEvent(long courseId, long instructorId, Instant unpublishedAt) {
}
