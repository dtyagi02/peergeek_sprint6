package com.peergeek.certificate.dto;

import java.time.Instant;
import com.peergeek.certificate.CertificateEntity;
import com.peergeek.certificate.CertificateStatus;

public record CertificateDto(Long id, Long courseId, String certificateNumber, String verificationCode, String studentName,
		String courseTitle, String instructorName, Instant issuedAt, CertificateStatus status, String downloadUrl) {
	public static CertificateDto from(CertificateEntity c, String downloadUrl) {
	return new CertificateDto(c.getId(), c.getCourseId(), c.getCertificateNumber(), c.getVerificationCode(), c.getStudentName(),
				c.getCourseTitle(), c.getInstructorName(), c.getIssuedAt(), c.getStatus(), downloadUrl);
	}
}
