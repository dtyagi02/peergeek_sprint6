package com.peergeek.certificate;

import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.enrollment.EnrollmentEntity;
import com.peergeek.user.UserEntity;

public record CertificateEligibilityResult(
		boolean eligible,
		String reason,
		EnrollmentEntity enrollment,
		CourseMetadataSnapshot course,
		UserEntity student,
		UserEntity instructor
) {
	public static CertificateEligibilityResult ineligible(String reason) {
		return new CertificateEligibilityResult(false, reason, null, null, null, null);
	}

	public static CertificateEligibilityResult eligible(
			EnrollmentEntity enrollment,
			CourseMetadataSnapshot course,
			UserEntity student,
			UserEntity instructor) {
		return new CertificateEligibilityResult(true, "Eligible", enrollment, course, student, instructor);
	}
}

