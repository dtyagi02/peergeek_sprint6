package com.peergeek.courselearning.lesson;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "lesson_contents")
public class LessonContentEntity {

    @Id
    @Column(name = "lesson_id")
    private Long lessonId;

    @Column(name = "content_format", nullable = false, length = 64)
    private String contentFormat = "DOCUMENT_JSON";

    @Column(name = "content_document", nullable = false, columnDefinition = "JSON")
    private String contentDocument;

    @Column(name = "word_count", nullable = false)
    private int wordCount = 0;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected LessonContentEntity() {}

    public LessonContentEntity(Long lessonId, String contentFormat, String contentDocument, int wordCount) {
        this.lessonId = lessonId;
        this.contentFormat = contentFormat != null ? contentFormat : "DOCUMENT_JSON";
        this.contentDocument = contentDocument;
        this.wordCount = wordCount;
        this.updatedAt = Instant.now();
    }

    public Long getLessonId() {
        return lessonId;
    }

    public void setLessonId(Long lessonId) {
        this.lessonId = lessonId;
    }

    public String getContentFormat() {
        return contentFormat;
    }

    public void setContentFormat(String contentFormat) {
        this.contentFormat = contentFormat;
    }

    public String getContentDocument() {
        return contentDocument;
    }

    public void setContentDocument(String contentDocument) {
        this.contentDocument = contentDocument;
        this.updatedAt = Instant.now();
    }

    public int getWordCount() {
        return wordCount;
    }

    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
        this.updatedAt = Instant.now();
    }

    public Long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
