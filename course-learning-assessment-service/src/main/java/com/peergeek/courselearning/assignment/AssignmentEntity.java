package com.peergeek.courselearning.assignment;

import com.peergeek.courselearning.common.AssignmentKind;
import com.peergeek.courselearning.common.AssignmentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "assignments")
public class AssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "module_id")
    private Long moduleId;

    @Column(name = "lesson_id")
    private Long lessonId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private AssignmentKind kind = AssignmentKind.MCQ;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "instructions", nullable = false, columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    @Column(name = "max_points", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxPoints = new BigDecimal("100.00");

    @Column(name = "opens_at")
    private Instant opensAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(name = "allow_late", nullable = false)
    private boolean allowLate = false;

    @Column(name = "allow_text", nullable = false)
    private boolean allowText = false;

    @Column(name = "allow_files", nullable = false)
    private boolean allowFiles = false;

    @Column(name = "show_correct_answers", nullable = false)
    private boolean showCorrectAnswers = false;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AssignmentEntity() {}

    public AssignmentEntity(Long courseId, Long createdBy, String title, String instructions, AssignmentKind kind) {
        this.courseId = courseId;
        this.createdBy = createdBy;
        this.title = title;
        this.instructions = instructions;
        this.kind = kind != null ? kind : AssignmentKind.MCQ;
        this.status = AssignmentStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public AssignmentEntity(
            Long courseId,
            Long moduleId,
            Long lessonId,
            AssignmentKind kind,
            String title,
            String instructions,
            BigDecimal maxPoints) {
        this.courseId = courseId;
        this.moduleId = moduleId;
        this.lessonId = lessonId;
        this.kind = kind != null ? kind : AssignmentKind.MCQ;
        this.title = title;
        this.instructions = instructions;
        this.maxPoints = maxPoints;
        this.status = AssignmentStatus.DRAFT;
        this.createdBy = 1L;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public void setLessonId(Long lessonId) {
        this.lessonId = lessonId;
    }

    public AssignmentKind getKind() {
        return kind;
    }

    public void setKind(AssignmentKind kind) {
        this.kind = kind;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = Instant.now();
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
        this.updatedAt = Instant.now();
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public BigDecimal getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(BigDecimal maxPoints) {
        this.maxPoints = maxPoints;
    }

    public Instant getOpensAt() {
        return opensAt;
    }

    public void setOpensAt(Instant opensAt) {
        this.opensAt = opensAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Integer getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public void setTimeLimitMinutes(Integer timeLimitMinutes) {
        this.timeLimitMinutes = timeLimitMinutes;
    }

    public boolean isAllowLate() {
        return allowLate;
    }

    public void setAllowLate(boolean allowLate) {
        this.allowLate = allowLate;
    }

    public boolean isAllowText() {
        return allowText;
    }

    public void setAllowText(boolean allowText) {
        this.allowText = allowText;
    }

    public boolean isAllowFiles() {
        return allowFiles;
    }

    public void setAllowFiles(boolean allowFiles) {
        this.allowFiles = allowFiles;
    }

    public boolean isShowCorrectAnswers() {
        return showCorrectAnswers;
    }

    public void setShowCorrectAnswers(boolean showCorrectAnswers) {
        this.showCorrectAnswers = showCorrectAnswers;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Long getVersion() {
        return version;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
