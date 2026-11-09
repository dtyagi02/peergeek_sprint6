package com.peergeek.certificate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.peergeek.assignment.AssignmentEntity;
import com.peergeek.assignment.AssignmentKind;
import com.peergeek.assignment.AssignmentRepository;
import com.peergeek.assignment.AssignmentStatus;
import com.peergeek.course.CourseDifficulty;
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
import com.peergeek.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateEligibilityServiceTest {

	@Mock
	private EnrollmentRepository enrollmentRepository;

	@Mock
	private ProgressService progressService;

	@Mock
	private CourseStructureQueryService courseStructureQueryService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private AssignmentRepository assignmentRepository;

	@Mock
	private SubmissionRepository submissionRepository;

	@InjectMocks
	private CertificateEligibilityService eligibilityService;

	private final Long userId = 10L;
	private final Long courseId = 100L;
	private final Long ownerId = 50L;
	private EnrollmentEntity enrollment;
	private UserEntity student;
	private UserEntity instructor;
	private CourseMetadataSnapshot courseMetadata;
	private CourseStructureQueryService.CourseSummary courseSummary;

	@BeforeEach
	void setUp() {
		enrollment = new EnrollmentEntity(userId, courseId, Instant.now());
		ReflectionTestUtils.setField(enrollment, "id", 1L);

		student = new UserEntity("student@example.com", "hash", "Student User");
		ReflectionTestUtils.setField(student, "id", userId);

		instructor = new UserEntity("instructor@example.com", "hash", "Instructor User");
		ReflectionTestUtils.setField(instructor, "id", ownerId);

		courseMetadata = new CourseMetadataSnapshot(
				courseId, ownerId, CourseStatus.PUBLISHED, false, 1L,
				"Full-Stack Development", "summary", 1L, "Web Dev", true,
				CourseDifficulty.BEGINNER, List.of(), null
		);

		courseSummary = new CourseStructureQueryService.CourseSummary(
				courseId, ownerId, "Full-Stack Development", "summary",
				CourseDifficulty.BEGINNER, CourseStatus.PUBLISHED, 1L
		);
	}

	@Test
	@DisplayName("Ineligible when enrollment does not exist or is not active")
	void ineligibleWhenNoActiveEnrollment() {
		when(enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE))
				.thenReturn(Optional.empty());

		CertificateEligibilityResult result = eligibilityService.checkEligibility(userId, courseId);
		assertThat(result.eligible()).isFalse();
		assertThat(result.reason()).contains("Active enrollment not found");
	}

	@Test
	@DisplayName("Ineligible when course is not published")
	void ineligibleWhenCourseNotPublished() {
		when(enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE))
				.thenReturn(Optional.of(enrollment));

		CourseMetadataSnapshot draftCourse = new CourseMetadataSnapshot(
				courseId, ownerId, CourseStatus.DRAFT, false, 1L,
				"Title", "summary", 1L, "Web Dev", true,
				CourseDifficulty.BEGINNER, List.of(), null
		);
		when(courseStructureQueryService.getCourseMetadata(courseId)).thenReturn(Optional.of(draftCourse));

		CertificateEligibilityResult result = eligibilityService.checkEligibility(userId, courseId);
		assertThat(result.eligible()).isFalse();
		assertThat(result.reason()).contains("not PUBLISHED");
	}

	@Test
	@DisplayName("Ineligible when course has 0 lessons")
	void ineligibleWhenCourseHasNoLessons() {
		when(enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE))
				.thenReturn(Optional.of(enrollment));
		when(courseStructureQueryService.getCourseMetadata(courseId)).thenReturn(Optional.of(courseMetadata));

		CourseStructureQueryService.CourseStructure structure = new CourseStructureQueryService.CourseStructure(courseSummary, List.of());
		when(courseStructureQueryService.getStructure(courseId)).thenReturn(structure);

		CertificateEligibilityResult result = eligibilityService.checkEligibility(userId, courseId);
		assertThat(result.eligible()).isFalse();
		assertThat(result.reason()).contains("contains no lessons");
	}

	@Test
	@DisplayName("Ineligible when course progress is below 100%")
	void ineligibleWhenProgressBelow100() {
		when(enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE))
				.thenReturn(Optional.of(enrollment));
		when(courseStructureQueryService.getCourseMetadata(courseId)).thenReturn(Optional.of(courseMetadata));

		CourseStructureQueryService.LessonSnapshot lesson1 = new CourseStructureQueryService.LessonSnapshot(1L, "Lesson 1", "sum", 1, 100, true);
		CourseStructureQueryService.LessonSnapshot lesson2 = new CourseStructureQueryService.LessonSnapshot(2L, "Lesson 2", "sum", 2, 100, true);
		CourseStructureQueryService.ModuleSnapshot module = new CourseStructureQueryService.ModuleSnapshot(1L, "Module 1", "desc", 1, List.of(lesson1, lesson2));
		CourseStructureQueryService.CourseStructure structure = new CourseStructureQueryService.CourseStructure(courseSummary, List.of(module));
		when(courseStructureQueryService.getStructure(courseId)).thenReturn(structure);

		CourseProgressDto progress = new CourseProgressDto(1, 2, 50, null);
		when(progressService.getOrRecomputeProgress(enrollment)).thenReturn(progress);

		CertificateEligibilityResult result = eligibilityService.checkEligibility(userId, courseId);
		assertThat(result.eligible()).isFalse();
		assertThat(result.reason()).contains("Lessons incomplete");
	}

	@Test
	@DisplayName("Eligible when course has optional published assignments that are not submitted")
	void optionalPublishedAssignmentsDoNotBlockEligibility() {
		when(enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE))
				.thenReturn(Optional.of(enrollment));
		when(courseStructureQueryService.getCourseMetadata(courseId)).thenReturn(Optional.of(courseMetadata));

		CourseStructureQueryService.LessonSnapshot lesson = new CourseStructureQueryService.LessonSnapshot(1L, "Lesson 1", "sum", 1, 100, true);
		CourseStructureQueryService.ModuleSnapshot module = new CourseStructureQueryService.ModuleSnapshot(1L, "Mod 1", "desc", 1, List.of(lesson));
		when(courseStructureQueryService.getStructure(courseId)).thenReturn(new CourseStructureQueryService.CourseStructure(courseSummary, List.of(module)));

		when(progressService.getOrRecomputeProgress(enrollment)).thenReturn(new CourseProgressDto(1, 1, 100, Instant.now()));
		when(userRepository.findById(userId)).thenReturn(Optional.of(student));
		when(userRepository.findById(ownerId)).thenReturn(Optional.of(instructor));

		CertificateEligibilityResult result = eligibilityService.checkEligibility(userId, courseId);
		assertThat(result.eligible()).isTrue();
		assertThat(result.student()).isEqualTo(student);
		assertThat(result.instructor()).isEqualTo(instructor);
	}

	@Test
	@DisplayName("Recomputes progress when completedAt timestamp is missing or progress appears incomplete")
	void recomputesProgressWhenTimestampMissing() {
		when(enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE))
				.thenReturn(Optional.of(enrollment));
		when(courseStructureQueryService.getCourseMetadata(courseId)).thenReturn(Optional.of(courseMetadata));

		CourseStructureQueryService.LessonSnapshot lesson = new CourseStructureQueryService.LessonSnapshot(1L, "Lesson 1", "sum", 1, 100, true);
		CourseStructureQueryService.ModuleSnapshot module = new CourseStructureQueryService.ModuleSnapshot(1L, "Mod 1", "desc", 1, List.of(lesson));
		when(courseStructureQueryService.getStructure(courseId)).thenReturn(new CourseStructureQueryService.CourseStructure(courseSummary, List.of(module)));

		// First returns stale cached progress without completedAt
		when(progressService.getOrRecomputeProgress(enrollment)).thenReturn(new CourseProgressDto(1, 1, 100, null));
		// Recomputation recovers fresh progress with completedAt
		when(progressService.recomputeProgress(enrollment)).thenReturn(new CourseProgressDto(1, 1, 100, Instant.now()));

		when(userRepository.findById(userId)).thenReturn(Optional.of(student));
		when(userRepository.findById(ownerId)).thenReturn(Optional.of(instructor));

		CertificateEligibilityResult result = eligibilityService.checkEligibility(userId, courseId);
		assertThat(result.eligible()).isTrue();
		verify(progressService).recomputeProgress(enrollment);
	}

	@Test
	@DisplayName("Eligible when 100% lessons completed and all published assignments submitted")
	void eligibleWhenAllCriteriaMet() {
		when(enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE))
				.thenReturn(Optional.of(enrollment));
		when(courseStructureQueryService.getCourseMetadata(courseId)).thenReturn(Optional.of(courseMetadata));

		CourseStructureQueryService.LessonSnapshot lesson = new CourseStructureQueryService.LessonSnapshot(1L, "Lesson 1", "sum", 1, 100, true);
		CourseStructureQueryService.ModuleSnapshot module = new CourseStructureQueryService.ModuleSnapshot(1L, "Mod 1", "desc", 1, List.of(lesson));
		when(courseStructureQueryService.getStructure(courseId)).thenReturn(new CourseStructureQueryService.CourseStructure(courseSummary, List.of(module)));

		when(progressService.getOrRecomputeProgress(enrollment)).thenReturn(new CourseProgressDto(1, 1, 100, Instant.now()));

		when(userRepository.findById(userId)).thenReturn(Optional.of(student));
		when(userRepository.findById(ownerId)).thenReturn(Optional.of(instructor));

		CertificateEligibilityResult result = eligibilityService.checkEligibility(userId, courseId);
		assertThat(result.eligible()).isTrue();
		assertThat(result.student()).isEqualTo(student);
		assertThat(result.instructor()).isEqualTo(instructor);
	}
}
