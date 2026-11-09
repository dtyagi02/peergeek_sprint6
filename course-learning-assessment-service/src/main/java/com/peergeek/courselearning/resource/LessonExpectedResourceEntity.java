package com.peergeek.courselearning.resource;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "lesson_expected_resources")
public class LessonExpectedResourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "min_count", nullable = false)
    private int minCount = 1;

    @Column(name = "label")
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected LessonExpectedResourceEntity() {}

    public LessonExpectedResourceEntity(Long lessonId, String resourceType, int minCount, String label) {
        this.lessonId = lessonId;
        this.resourceType = resourceType;
        this.minCount = minCount;
        this.label = label;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public int getMinCount() {
        return minCount;
    }

    public void setMinCount(int minCount) {
        this.minCount = minCount;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

