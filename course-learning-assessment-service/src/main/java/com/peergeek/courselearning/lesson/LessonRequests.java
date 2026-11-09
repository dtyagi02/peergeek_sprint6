package com.peergeek.courselearning.lesson;

import java.util.List;

public class LessonRequests {
    public record Create(String title, String summary) {}
    public record Move(Long targetModuleId, Integer position) {}
    public record Order(List<Long> lessonIds) {}
}

