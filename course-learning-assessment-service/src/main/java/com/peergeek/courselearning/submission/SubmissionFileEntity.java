package com.peergeek.courselearning.submission;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "submission_files")
public class SubmissionFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "mime_type", nullable = false, length = 128)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "READY";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected SubmissionFileEntity() {}

    public SubmissionFileEntity(Long submissionId, String originalFilename, String objectKey, String mimeType, Long sizeBytes) {
        this.submissionId = submissionId;
        this.originalFilename = originalFilename;
        this.objectKey = objectKey;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.status = "READY";
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

