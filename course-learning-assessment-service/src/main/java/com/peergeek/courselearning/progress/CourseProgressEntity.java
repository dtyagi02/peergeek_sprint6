package com.peergeek.courselearning.progress;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "course_progress")
public class CourseProgressEntity {

    @Id
    @Column(name = "enrollment_id")
    private Long enrollmentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "total_lessons", nullable = false)
    private int totalLessons = 0;

    @Column(name = "completed_lessons", nullable = false)
    private int completedLessons = 0;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.TINYINT)
    @Column(name = "percent", nullable = false)
    private int percent = 0;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "stale", nullable = false)
    private boolean stale = false;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CourseProgressEntity() {}

    public CourseProgressEntity(Long enrollmentId, Long courseId, int totalLessons, int completedLessons, int percent, Instant completedAt) {
        this.enrollmentId = enrollmentId;
        this.courseId = courseId;
        this.totalLessons = totalLessons;
        this.completedLessons = completedLessons;
        this.percent = percent;
        this.completedAt = completedAt;
        this.stale = false;
        this.computedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(Long enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public int getTotalLessons() {
        return totalLessons;
    }

    public void setTotalLessons(int totalLessons) {
        this.totalLessons = totalLessons;
    }

    public int getCompletedLessons() {
        return completedLessons;
    }

    public void setCompletedLessons(int completedLessons) {
        this.completedLessons = completedLessons;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
