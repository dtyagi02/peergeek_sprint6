package com.peergeek.certificate.dto;

import java.time.Instant;
import com.peergeek.certificate.CertificateStatus;

public record CertificateVerificationDto(
		boolean valid,
		CertificateStatus status,
		String certificateNumber,
		String studentName,
		String courseTitle,
		String instructorName,
		Instant issuedAt,
		Instant revokedAt,
		String revocationReason
) {
	public CertificateVerificationDto(boolean valid, CertificateStatus status, String certificateNumber,
			String studentName, String courseTitle, String instructorName, Instant issuedAt) {
		this(valid, status, certificateNumber, studentName, courseTitle, instructorName, issuedAt, null, null);
	}
}
