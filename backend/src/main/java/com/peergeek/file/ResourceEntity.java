package com.peergeek.file;

import java.time.Instant;
import com.peergeek.common.BaseEntity;
import com.peergeek.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "resources")
public class ResourceEntity extends BaseEntity {
	@Column(name = "course_id", nullable = false)
	private Long courseId;

	@Column(name = "lesson_id")
	private Long lessonId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "uploaded_by", nullable = false)
	private UserEntity uploadedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ResourcePurpose purpose;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ResourceType type;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(name = "original_filename", length = 255)
	private String originalFilename;

	@Column(name = "object_key", unique = true, length = 512)
	private String objectKey;

	@Column(name = "mime_type", length = 255)
	private String mimeType;

	@Column(name = "size_bytes")
	private Long sizeBytes;

	@Column(name = "checksum_sha256", length = 64)
	private String checksumSha256;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ResourceStatus status;

	@Column(name = "failure_reason", length = 64)
	private String failureReason;

	@Column(name = "processing_attempts", nullable = false)
	private int processingAttempts;

	@Column(name = "external_url", length = 2048)
	private String externalUrl;

	@Column(nullable = false)
	private int position;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected ResourceEntity() {
	}

	public ResourceEntity(
			long courseId,
			Long lessonId,
			UserEntity uploadedBy,
			ResourcePurpose purpose,
			ResourceType type,
			String title,
			String originalFilename,
			String objectKey,
			String mimeType,
			Long sizeBytes,
			String externalUrl,
			int position,
			ResourceStatus status) {
		this.courseId = courseId;
		this.lessonId = lessonId;
		this.uploadedBy = uploadedBy;
		this.purpose = purpose;
		this.type = type;
		this.title = title;
		this.originalFilename = originalFilename;
		this.objectKey = objectKey;
		this.mimeType = mimeType;
		this.sizeBytes = sizeBytes;
		this.externalUrl = externalUrl;
		this.position = position;
		this.status = status;
	}

	public Long getCourseId() { return courseId; }
	public Long getLessonId() { return lessonId; }
	public UserEntity getUploadedBy() { return uploadedBy; }
	public ResourcePurpose getPurpose() { return purpose; }
	public ResourceType getType() { return type; }
	public String getTitle() { return title; }
	public String getOriginalFilename() { return originalFilename; }
	public String getObjectKey() { return objectKey; }
	public String getMimeType() { return mimeType; }
	public Long getSizeBytes() { return sizeBytes; }
	public String getChecksumSha256() { return checksumSha256; }
	public ResourceStatus getStatus() { return status; }
	public String getFailureReason() { return failureReason; }
	public int getProcessingAttempts() { return processingAttempts; }
	public String getExternalUrl() { return externalUrl; }
	public int getPosition() { return position; }
	public Instant getDeletedAt() { return deletedAt; }

	public void rename(String title) { this.title = title; }
	public void updateExternalUrl(String url) { this.externalUrl = url; }
	public void changePosition(int position) { this.position = position; }
	public void setStatus(ResourceStatus status) { this.status = status; }
	public void markUploaded() { this.status = ResourceStatus.UPLOADED; }
	public void markProcessing() { this.status = ResourceStatus.PROCESSING; }
	public int beginProcessingAttempt() {
		this.processingAttempts++;
		this.status = ResourceStatus.PROCESSING;
		return this.processingAttempts;
	}
	public void markReady(String checksumSha256, long sizeBytes) {
		this.checksumSha256 = checksumSha256;
		this.sizeBytes = sizeBytes;
		this.status = ResourceStatus.READY;
		this.failureReason = null;
	}
	public void markFailed(String failureReason) {
		this.status = ResourceStatus.FAILED;
		this.failureReason = failureReason;
	}
	public void softDelete(Instant now) { this.deletedAt = now; }
}
