package com.peergeek.courselearning.readiness;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "readiness_runs")
public class ReadinessRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "COMPLETED";

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "blocking_count", nullable = false)
    private int blockingCount = 0;

    @Column(name = "warning_count", nullable = false)
    private int warningCount = 0;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ReadinessRunEntity() {}

    public ReadinessRunEntity(Long courseId, Long requestedBy, String fingerprint, int blockingCount, int warningCount) {
        this.courseId = courseId;
        this.requestedBy = requestedBy;
        this.fingerprint = fingerprint;
        this.blockingCount = blockingCount;
        this.warningCount = warningCount;
        this.status = "COMPLETED";
        this.startedAt = Instant.now();
        this.finishedAt = Instant.now();
        this.createdAt = Instant.now();
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

    public Long getRequestedBy() {
        return requestedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public int getBlockingCount() {
        return blockingCount;
    }

    public void setBlockingCount(int blockingCount) {
        this.blockingCount = blockingCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public void setWarningCount(int warningCount) {
        this.warningCount = warningCount;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
