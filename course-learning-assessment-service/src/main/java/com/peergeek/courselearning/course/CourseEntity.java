package com.peergeek.courselearning.course;

import com.peergeek.courselearning.common.CourseDifficulty;
import com.peergeek.courselearning.common.CourseStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "courses")
public class CourseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", length = 32)
    private CourseDifficulty difficulty = CourseDifficulty.BEGINNER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CourseStatus status = CourseStatus.DRAFT;

    @Column(name = "thumbnail_resource_id")
    private Long thumbnailResourceId;

    @Transient
    private String thumbnailKey;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "content_updated_at", nullable = false)
    private Instant contentUpdatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CourseEntity() {}

    public CourseEntity(Long ownerId, String title, String description, Long categoryId, CourseDifficulty difficulty) {
        this.ownerId = ownerId;
        this.title = title;
        this.description = description;
        this.categoryId = categoryId;
        this.difficulty = difficulty != null ? difficulty : CourseDifficulty.BEGINNER;
        this.status = CourseStatus.DRAFT;
        this.contentUpdatedAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.contentUpdatedAt = Instant.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.contentUpdatedAt = Instant.now();
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
        this.contentUpdatedAt = Instant.now();
    }

    public CourseDifficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(CourseDifficulty difficulty) {
        this.difficulty = difficulty;
        this.contentUpdatedAt = Instant.now();
    }

    public CourseStatus getStatus() {
        return status;
    }

    public void setStatus(CourseStatus status) {
        this.status = status;
    }

    public Long getThumbnailResourceId() {
        return thumbnailResourceId;
    }

    public void setThumbnailResourceId(Long thumbnailResourceId) {
        this.thumbnailResourceId = thumbnailResourceId;
    }

    public String getThumbnailKey() {
        return thumbnailKey;
    }

    public void setThumbnailKey(String thumbnailKey) {
        this.thumbnailKey = thumbnailKey;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getContentUpdatedAt() {
        return contentUpdatedAt;
    }

    public void setContentUpdatedAt(Instant contentUpdatedAt) {
        this.contentUpdatedAt = contentUpdatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
