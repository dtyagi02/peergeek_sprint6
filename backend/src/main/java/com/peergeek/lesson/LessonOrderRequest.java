package com.peergeek.lesson;

import java.util.List;

public record LessonOrderRequest(List<Long> lessonIds) {
}
