package com.peergeek.course;

public record CourseCountRow(Long courseId, long moduleCount, long lessonCount, long totalWordCount) {
}
