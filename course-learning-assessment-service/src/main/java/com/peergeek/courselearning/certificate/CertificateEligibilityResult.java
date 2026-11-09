package com.peergeek.courselearning.certificate;

import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.enrollment.EnrollmentEntity;
import com.peergeek.courselearning.profile.LearningUserProfileEntity;

public record CertificateEligibilityResult(
        boolean eligible,
        String reason,
        EnrollmentEntity enrollment,
        CourseEntity course,
        LearningUserProfileEntity student,
        LearningUserProfileEntity instructor
) {
    public static CertificateEligibilityResult eligible(
            EnrollmentEntity enrollment,
            CourseEntity course,
            LearningUserProfileEntity student,
            LearningUserProfileEntity instructor) {
        return new CertificateEligibilityResult(true, null, enrollment, course, student, instructor);
    }

    public static CertificateEligibilityResult ineligible(String reason) {
        return new CertificateEligibilityResult(false, reason, null, null, null, null);
    }
}

