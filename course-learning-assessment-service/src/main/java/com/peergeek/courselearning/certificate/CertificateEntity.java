package com.peergeek.courselearning.certificate;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "certificates")
public class CertificateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    private Instant issuedAt = Instant.now();

    @Column(name = "file_key", length = 500)
    private String fileKey;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ISSUED";

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CertificateEntity() {}

    public CertificateEntity(
            String certificateNumber,
            String verificationCode,
            Long userId,
            Long courseId,
            Long enrollmentId,
            String studentName,
            String courseTitle,
            String instructorName) {
        this(certificateNumber, verificationCode, userId, courseId, enrollmentId, studentName, courseTitle, instructorName, Instant.now());
    }

    public CertificateEntity(
            String certificateNumber,
            String verificationCode,
            Long userId,
            Long courseId,
            Long enrollmentId,
            String studentName,
            String courseTitle,
            String instructorName,
            Instant issuedAt) {
        this.certificateNumber = certificateNumber;
        this.verificationCode = verificationCode;
        this.userId = userId;
        this.courseId = courseId;
        this.enrollmentId = enrollmentId;
        this.studentName = studentName;
        this.courseTitle = courseTitle;
        this.instructorName = instructorName;
        this.status = "ISSUED";
        this.issuedAt = issuedAt != null ? issuedAt : Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCertificateNumber() {
        return certificateNumber;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public Long getEnrollmentId() {
        return enrollmentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public String getInstructorName() {
        return instructorName;
    }

    public void setInstructorName(String instructorName) {
        this.instructorName = instructorName;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public String getFileKey() {
        return fileKey;
    }

    public void setFileKey(String fileKey) {
        this.fileKey = fileKey;
        this.updatedAt = Instant.now();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public void setRevocationReason(String revocationReason) {
        this.revocationReason = revocationReason;
    }

    public Long getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(Long revokedBy) {
        this.revokedBy = revokedBy;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
