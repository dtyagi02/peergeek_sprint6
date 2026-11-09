package com.peergeek.submission;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "submission_files")
public class SubmissionFileEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "submission_id", nullable = false)
	private SubmissionEntity submission;

	@Column(name = "uploaded_by", nullable = false)
	private Long uploadedBy;

	@Column(name = "original_filename", nullable = false)
	private String originalFilename;

	@Column(name = "object_key", nullable = false, unique = true, length = 512)
	private String objectKey;

	@Column(name = "mime_type", nullable = false, length = 127)
	private String mimeType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "checksum_sha256", length = 64)
	private String checksumSha256;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private SubmissionFileStatus status = SubmissionFileStatus.PENDING;

	@Column(name = "failure_reason", length = 64)
	private String failureReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected SubmissionFileEntity() {
	}

	public SubmissionFileEntity(
			SubmissionEntity submission,
			Long uploadedBy,
			String originalFilename,
			String objectKey,
			String mimeType,
			long sizeBytes,
			Instant now) {
		this.submission = submission;
		this.uploadedBy = uploadedBy;
		this.originalFilename = originalFilename;
		this.objectKey = objectKey;
		this.mimeType = mimeType;
		this.sizeBytes = sizeBytes;
		this.status = SubmissionFileStatus.PENDING;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public SubmissionEntity getSubmission() {
		return submission;
	}

	public Long getUploadedBy() {
		return uploadedBy;
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

	public long getSizeBytes() {
		return sizeBytes;
	}

	public void setSizeBytes(long sizeBytes) {
		this.sizeBytes = sizeBytes;
	}

	public String getChecksumSha256() {
		return checksumSha256;
	}

	public void setChecksumSha256(String checksumSha256) {
		this.checksumSha256 = checksumSha256;
	}

	public SubmissionFileStatus getStatus() {
		return status;
	}

	public void setStatus(SubmissionFileStatus status) {
		this.status = status;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public void setFailureReason(String failureReason) {
		this.failureReason = failureReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
