package com.peergeek.certificate;

import java.util.List;
import java.util.Optional;
import com.peergeek.assignment.AssignmentEntity;
import com.peergeek.assignment.AssignmentRepository;
import com.peergeek.assignment.AssignmentStatus;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStatus;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.enrollment.CourseProgressDto;
import com.peergeek.enrollment.EnrollmentEntity;
import com.peergeek.enrollment.EnrollmentRepository;
import com.peergeek.enrollment.EnrollmentStatus;
import com.peergeek.enrollment.ProgressService;
import com.peergeek.submission.SubmissionEntity;
import com.peergeek.submission.SubmissionRepository;
import com.peergeek.submission.SubmissionStatus;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CertificateEligibilityService {
	private static final Logger log = LoggerFactory.getLogger(CertificateEligibilityService.class);

	private final EnrollmentRepository enrollmentRepository;
	private final ProgressService progressService;
	private final CourseStructureQueryService courseStructureQueryService;
	private final UserRepository userRepository;
	private final AssignmentRepository assignmentRepository;
	private final SubmissionRepository submissionRepository;

	public CertificateEligibilityService(
			EnrollmentRepository enrollmentRepository,
			ProgressService progressService,
			CourseStructureQueryService courseStructureQueryService,
			UserRepository userRepository,
			AssignmentRepository assignmentRepository,
			SubmissionRepository submissionRepository) {
		this.enrollmentRepository = enrollmentRepository;
		this.progressService = progressService;
		this.courseStructureQueryService = courseStructureQueryService;
		this.userRepository = userRepository;
		this.assignmentRepository = assignmentRepository;
		this.submissionRepository = submissionRepository;
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

		Optional<CourseMetadataSnapshot> courseOpt = courseStructureQueryService.getCourseMetadata(courseId);
		if (courseOpt.isEmpty() || courseOpt.get().deleted()) {
			return CertificateEligibilityResult.ineligible("Course " + courseId + " does not exist or has been deleted");
		}
		CourseMetadataSnapshot course = courseOpt.get();

		if (course.status() != CourseStatus.PUBLISHED) {
			return CertificateEligibilityResult.ineligible("Course " + courseId + " is not PUBLISHED (current status: " + course.status() + ")");
		}

		CourseStructureQueryService.CourseStructure structure = courseStructureQueryService.getStructure(courseId);
		long totalLessonsInStructure = structure.modules().stream()
				.flatMap(m -> m.lessons().stream())
				.count();
		if (totalLessonsInStructure == 0) {
			return CertificateEligibilityResult.ineligible("Course contains no lessons");
		}

		CourseProgressDto progressDto = progressService.getOrRecomputeProgress(enrollment);
		if (progressDto == null || progressDto.completedLessons() < totalLessonsInStructure || progressDto.percent() < 100 || progressDto.completedAt() == null) {
			// Recompute progress from actual lesson progress records to resolve stale cache or missing completed_at
			CourseProgressDto recomputed = progressService.recomputeProgress(enrollment);
			if (recomputed != null) {
				progressDto = recomputed;
			}
		}

		if (progressDto == null || progressDto.completedLessons() < totalLessonsInStructure || progressDto.percent() < 100) {
			int completed = progressDto != null ? progressDto.completedLessons() : 0;
			int percent = progressDto != null ? progressDto.percent() : 0;
			return CertificateEligibilityResult.ineligible("Lessons incomplete (" + completed + "/" + totalLessonsInStructure + ", " + percent + "%)");
		}

		// Course completion in PeerGeek is authoritative upon 100% lesson completion.
		// Published assignments are optional coursework and do not block certificate issuance.

		UserEntity student = userRepository.findById(userId).orElse(null);
		if (student == null) {
			return CertificateEligibilityResult.ineligible("Student record not found for user " + userId);
		}

		UserEntity instructor = userRepository.findById(course.ownerId()).orElse(null);
		if (instructor == null) {
			return CertificateEligibilityResult.ineligible("Instructor record not found for course owner " + course.ownerId());
		}

		return CertificateEligibilityResult.eligible(enrollment, course, student, instructor);
	}
}

