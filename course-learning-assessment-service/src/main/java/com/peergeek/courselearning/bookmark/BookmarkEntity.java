package com.peergeek.courselearning.bookmark;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bookmarks")
public class BookmarkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType = "COURSE";

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "lesson_id")
    private Long lessonId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected BookmarkEntity() {}

    public BookmarkEntity(Long userId, String targetType, Long courseId, Long lessonId) {
        this.userId = userId;
        this.targetType = targetType != null ? targetType : "COURSE";
        this.courseId = courseId;
        this.lessonId = lessonId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getCourseId() {
        return courseId;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

