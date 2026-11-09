package com.peergeek.certificate;

import java.time.Instant;
import com.peergeek.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "certificates", uniqueConstraints = {
		@UniqueConstraint(name = "uq_certificates_number", columnNames = "certificate_number"),
		@UniqueConstraint(name = "uq_certificates_verification", columnNames = "verification_code"),
		@UniqueConstraint(name = "uq_certificates_enrollment", columnNames = "enrollment_id")
})
public class CertificateEntity extends BaseEntity {
	@Column(name = "certificate_number", nullable = false, unique = true, length = 64)
	private String certificateNumber;

	@Column(name = "verification_code", nullable = false, unique = true, length = 128)
	private String verificationCode;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "course_id", nullable = false)
	private Long courseId;

	@Column(name = "enrollment_id", nullable = false, unique = true)
	private Long enrollmentId;

	@Column(name = "student_name", nullable = false)
	private String studentName;

	@Column(name = "course_title", nullable = false, length = 500)
	private String courseTitle;

	@Column(name = "instructor_name", nullable = false)
	private String instructorName;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt;

	@Column(name = "file_key", length = 500)
	private String fileKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private CertificateStatus status;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revocation_reason", length = 500)
	private String revocationReason;

	@Column(name = "revoked_by")
	private Long revokedBy;

	@Column(name = "failure_reason", length = 1000)
	private String failureReason;

	@Column(name = "retry_count", nullable = false)
	private int retryCount = 0;

	protected CertificateEntity() {}

	public CertificateEntity(String number, String verification, Long userId, Long courseId, Long enrollmentId,
			String studentName, String courseTitle, String instructorName, Instant issuedAt) {
		this.certificateNumber = number;
		this.verificationCode = verification;
		this.userId = userId;
		this.courseId = courseId;
		this.enrollmentId = enrollmentId;
		this.studentName = studentName;
		this.courseTitle = courseTitle;
		this.instructorName = instructorName;
		this.issuedAt = issuedAt;
		this.status = CertificateStatus.PENDING;
		this.retryCount = 0;
	}

	public String getCertificateNumber() { return certificateNumber; }
	public String getVerificationCode() { return verificationCode; }
	public Long getUserId() { return userId; }
	public Long getCourseId() { return courseId; }
	public Long getEnrollmentId() { return enrollmentId; }
	public String getStudentName() { return studentName; }
	public String getCourseTitle() { return courseTitle; }
	public String getInstructorName() { return instructorName; }
	public Instant getIssuedAt() { return issuedAt; }
	public String getFileKey() { return fileKey; }
	public CertificateStatus getStatus() { return status; }
	public Instant getRevokedAt() { return revokedAt; }
	public String getRevocationReason() { return revocationReason; }
	public Long getRevokedBy() { return revokedBy; }
	public String getFailureReason() { return failureReason; }
	public int getRetryCount() { return retryCount; }

	public void issue(String fileKey) {
		this.fileKey = fileKey;
		this.status = CertificateStatus.ISSUED;
		this.failureReason = null;
	}

	public void fail(String reason) {
		this.status = CertificateStatus.FAILED;
		this.failureReason = reason != null && reason.length() > 990 ? reason.substring(0, 990) : reason;
		this.retryCount++;
	}

	public void fail() {
		fail(null);
	}

	public void revoke(String reason, Long adminUserId) {
		this.status = CertificateStatus.REVOKED;
		this.revokedAt = Instant.now();
		this.revocationReason = reason;
		this.revokedBy = adminUserId;
	}

	public void revoke(String reason) {
		revoke(reason, null);
	}

	public void markPending() {
		this.status = CertificateStatus.PENDING;
		this.failureReason = null;
	}
}
