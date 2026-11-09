package com.peergeek.courselearning.discovery;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "public_course_index")
public class PublicCourseIndexEntity {
    @Id
    @Column(name = "course_id")
    private Long courseId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "short_description", length = 300)
    private String shortDescription;

    @Column(name = "search_text", nullable = false, columnDefinition = "TEXT")
    private String searchText;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "category_name", length = 80)
    private String categoryName;

    @Column(name = "category_slug", length = 100)
    private String categorySlug;

    @Column(length = 16)
    private String difficulty;

    @Column(name = "topics_text", length = 1000)
    private String topicsText;

    @Column(name = "instructor_id", nullable = false)
    private Long instructorId;

    @Column(name = "instructor_name", length = 120)
    private String instructorName;

    @Column(name = "module_count", nullable = false)
    private int moduleCount;

    @Column(name = "lesson_count", nullable = false)
    private int lessonCount;

    @Column(name = "thumbnail_resource_id")
    private Long thumbnailResourceId;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "source_fingerprint", nullable = false, length = 64, columnDefinition = "char(64)")
    private String sourceFingerprint;

    @Column(name = "indexed_at", nullable = false)
    private Instant indexedAt;

    protected PublicCourseIndexEntity() {}

    public PublicCourseIndexEntity(
            Long courseId,
            String title,
            String shortDescription,
            String searchText,
            Long categoryId,
            String categoryName,
            String categorySlug,
            String difficulty,
            String topicsText,
            Long instructorId,
            String instructorName,
            int moduleCount,
            int lessonCount,
            Long thumbnailResourceId,
            Instant publishedAt,
            String sourceFingerprint,
            Instant indexedAt) {
        this.courseId = courseId;
        this.title = title;
        this.shortDescription = shortDescription;
        this.searchText = searchText;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.categorySlug = categorySlug;
        this.difficulty = difficulty;
        this.topicsText = topicsText;
        this.instructorId = instructorId;
        this.instructorName = instructorName;
        this.moduleCount = moduleCount;
        this.lessonCount = lessonCount;
        this.thumbnailResourceId = thumbnailResourceId;
        this.publishedAt = publishedAt;
        this.sourceFingerprint = sourceFingerprint;
        this.indexedAt = indexedAt;
    }

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public String getSearchText() { return searchText; }
    public void setSearchText(String searchText) { this.searchText = searchText; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getCategorySlug() { return categorySlug; }
    public void setCategorySlug(String categorySlug) { this.categorySlug = categorySlug; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getTopicsText() { return topicsText; }
    public void setTopicsText(String topicsText) { this.topicsText = topicsText; }
    public Long getInstructorId() { return instructorId; }
    public void setInstructorId(Long instructorId) { this.instructorId = instructorId; }
    public String getInstructorName() { return instructorName; }
    public void setInstructorName(String instructorName) { this.instructorName = instructorName; }
    public int getModuleCount() { return moduleCount; }
    public void setModuleCount(int moduleCount) { this.moduleCount = moduleCount; }
    public int getLessonCount() { return lessonCount; }
    public void setLessonCount(int lessonCount) { this.lessonCount = lessonCount; }
    public Long getThumbnailResourceId() { return thumbnailResourceId; }
    public void setThumbnailResourceId(Long thumbnailResourceId) { this.thumbnailResourceId = thumbnailResourceId; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public void setSourceFingerprint(String sourceFingerprint) { this.sourceFingerprint = sourceFingerprint; }
    public Instant getIndexedAt() { return indexedAt; }
    public void setIndexedAt(Instant indexedAt) { this.indexedAt = indexedAt; }
}
