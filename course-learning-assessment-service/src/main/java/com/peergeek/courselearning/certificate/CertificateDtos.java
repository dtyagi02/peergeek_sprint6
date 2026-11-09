package com.peergeek.courselearning.certificate;

import java.time.Instant;
import java.util.List;

public class CertificateDtos {

    public record CertificateDto(
            Long id,
            Long courseId,
            String certificateNumber,
            String verificationCode,
            String studentName,
            String courseTitle,
            String instructorName,
            Instant issuedAt,
            String status,
            String downloadUrl
    ) {
        public static CertificateDto from(CertificateEntity c, String downloadUrl) {
            return new CertificateDto(
                    c.getId(),
                    c.getCourseId(),
                    c.getCertificateNumber(),
                    c.getVerificationCode(),
                    c.getStudentName(),
                    c.getCourseTitle(),
                    c.getInstructorName(),
                    c.getIssuedAt(),
                    c.getStatus(),
                    downloadUrl
            );
        }
    }

    public record CertificateVerificationDto(
            boolean valid,
            String status,
            String certificateNumber,
            String studentName,
            String courseTitle,
            String instructorName,
            Instant issuedAt,
            Instant revokedAt,
            String revocationReason
    ) {
        public CertificateVerificationDto(boolean valid, String status, String certificateNumber,
                                          String studentName, String courseTitle, String instructorName, Instant issuedAt) {
            this(valid, status, certificateNumber, studentName, courseTitle, instructorName, issuedAt, null, null);
        }
    }

    public record AdminCertificateDto(
            Long id,
            String certificateNumber,
            String verificationCode,
            Long userId,
            String studentName,
            Long courseId,
            String courseTitle,
            String instructorName,
            String status,
            Instant issuedAt,
            Instant revokedAt,
            String revocationReason,
            Long revokedBy,
            String failureReason,
            int retryCount,
            String downloadUrl
    ) {
        public static AdminCertificateDto from(CertificateEntity c, String downloadUrl) {
            return new AdminCertificateDto(
                    c.getId(),
                    c.getCertificateNumber(),
                    c.getVerificationCode(),
                    c.getUserId(),
                    c.getStudentName(),
                    c.getCourseId(),
                    c.getCourseTitle(),
                    c.getInstructorName(),
                    c.getStatus(),
                    c.getIssuedAt(),
                    c.getRevokedAt(),
                    c.getRevocationReason(),
                    c.getRevokedBy(),
                    c.getFailureReason(),
                    c.getRetryCount(),
                    downloadUrl
            );
        }
    }

    public record ReconciliationSummaryDto(
            int totalExamined,
            int eligible,
            int alreadyIssued,
            int newlyIssued,
            int failed,
            int ineligible,
            List<String> details
    ) {
        public static ReconciliationSummaryDto empty() {
            return new ReconciliationSummaryDto(0, 0, 0, 0, 0, 0, List.of());
        }
    }

    public record RevokeCertificateRequest(
            String reason
    ) {}

    public record DownloadUrl(
            String downloadUrl
    ) {}
}

