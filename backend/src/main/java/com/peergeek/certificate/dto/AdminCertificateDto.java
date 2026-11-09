package com.peergeek.certificate.dto;

import java.time.Instant;
import com.peergeek.certificate.CertificateEntity;
import com.peergeek.certificate.CertificateStatus;

public record AdminCertificateDto(
		Long id,
		String certificateNumber,
		String verificationCode,
		Long userId,
		String studentName,
		Long courseId,
		String courseTitle,
		String instructorName,
		CertificateStatus status,
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

