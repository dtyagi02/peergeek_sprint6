package com.peergeek.courselearning.certificate;

import com.peergeek.courselearning.common.CourseStatus;
import com.peergeek.courselearning.common.EnrollmentStatus;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.enrollment.CourseProgressDto;
import com.peergeek.courselearning.enrollment.EnrollmentEntity;
import com.peergeek.courselearning.enrollment.EnrollmentRepository;
import com.peergeek.courselearning.lesson.LessonRepository;
import com.peergeek.courselearning.profile.LearningUserProfileEntity;
import com.peergeek.courselearning.profile.LearningUserProfileRepository;
import com.peergeek.courselearning.progress.ProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CertificateEligibilityService {

    private static final Logger log = LoggerFactory.getLogger(CertificateEligibilityService.class);

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final ProgressService progressService;
    private final LearningUserProfileRepository userProfileRepository;

    public CertificateEligibilityService(
            EnrollmentRepository enrollmentRepository,
            CourseRepository courseRepository,
            LessonRepository lessonRepository,
            ProgressService progressService,
            LearningUserProfileRepository userProfileRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.progressService = progressService;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional
    public CertificateEligibilityResult checkEligibility(Long userId, Long courseId) {
        EnrollmentEntity enrollment = enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE)
                .orElse(null);
        if (enrollment == null) {
            return CertificateEligibilityResult.ineligible("Active enrollment not found for user " + userId + " in course " + courseId);
        }
        return checkEligibility(enrollment);
    }

    @Transactional
    public CertificateEligibilityResult checkEligibility(EnrollmentEntity enrollment) {
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE) {
            return CertificateEligibilityResult.ineligible("Enrollment status is " + enrollment.getStatus() + ", ACTIVE required");
        }

        Long courseId = enrollment.getCourseId();
        Long userId = enrollment.getUserId();

        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId).orElse(null);
        if (course == null) {
            return CertificateEligibilityResult.ineligible("Course " + courseId + " does not exist or has been deleted");
        }

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            return CertificateEligibilityResult.ineligible("Course " + courseId + " is not PUBLISHED");
        }

        int totalLessons = lessonRepository.countByCourseId(courseId);
        if (totalLessons == 0) {
            return CertificateEligibilityResult.ineligible("Course contains no lessons");
        }

        CourseProgressDto progressDto = progressService.getOrRecomputeProgress(enrollment);
        if (progressDto == null || progressDto.completedLessons() < totalLessons || progressDto.percent() < 100) {
            CourseProgressDto recomputed = progressService.recomputeProgress(enrollment);
            if (recomputed != null) {
                progressDto = recomputed;
            }
        }

        if (progressDto == null || progressDto.completedLessons() < totalLessons || progressDto.percent() < 100) {
            int completed = progressDto != null ? progressDto.completedLessons() : 0;
            int percent = progressDto != null ? progressDto.percent() : 0;
            return CertificateEligibilityResult.ineligible("Lessons incomplete (" + completed + "/" + totalLessons + ", " + percent + "%)");
        }

        LearningUserProfileEntity student = userProfileRepository.findById(userId).orElse(null);
        LearningUserProfileEntity instructor = userProfileRepository.findById(course.getOwnerId()).orElse(null);

        return CertificateEligibilityResult.eligible(enrollment, course, student, instructor);
    }
}

