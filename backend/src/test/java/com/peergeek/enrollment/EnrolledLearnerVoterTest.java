package com.peergeek.enrollment;

import java.util.List;
import java.util.Optional;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStatus;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.file.OwnerResourceVoter;
import com.peergeek.file.ResourceAccessContext;
import com.peergeek.file.ResourceAccessService;
import com.peergeek.file.ResourceAction;
import com.peergeek.file.ResourcePurpose;
import com.peergeek.file.ResourceStatus;
import com.peergeek.file.ResourceSummary;
import com.peergeek.file.ResourceType;
import com.peergeek.file.ResourceVote;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrolledLearnerVoterTest {

	@Mock private EnrollmentRepository enrollmentRepository;
	@Mock private CourseStructureQueryService courseStructure;

	private EnrolledLearnerVoter voter;

	@BeforeEach
	void setUp() {
		voter = new EnrolledLearnerVoter(enrollmentRepository, courseStructure);
	}

	@AfterEach
	void tearDown() {
		RequestContextHolder.resetRequestAttributes();
	}

	private ResourceSummary sampleResource(long courseId, ResourcePurpose purpose, ResourceStatus status) {
		return new ResourceSummary(100L, courseId, 200L, ResourceType.DOCUMENT, purpose, status);
	}

	private CourseMetadataSnapshot sampleMetadata(long courseId, long ownerId, CourseStatus status, boolean deleted) {
		return new CourseMetadataSnapshot(
				courseId, ownerId, status, deleted, 1L, "Course Title", "Course Desc",
				1L, "Category", true, com.peergeek.course.CourseDifficulty.BEGINNER, List.of(), null);
	}

	@Test
	@DisplayName("Anonymous user (null userId) returns ABSTAIN")
	void anonymousUserAbstains() {
		ResourceSummary res = sampleResource(1L, ResourcePurpose.LESSON_RESOURCE, ResourceStatus.READY);
		ResourceVote vote = voter.vote(new ResourceAccessContext(null, res, ResourceAction.VIEW));
		assertThat(vote).isEqualTo(ResourceVote.ABSTAIN);
	}

	@Test
	@DisplayName("Action MANAGE returns ABSTAIN")
	void manageActionAbstains() {
		ResourceSummary res = sampleResource(1L, ResourcePurpose.LESSON_RESOURCE, ResourceStatus.READY);
		ResourceVote vote = voter.vote(new ResourceAccessContext(42L, res, ResourceAction.MANAGE));
		assertThat(vote).isEqualTo(ResourceVote.ABSTAIN);
	}

	@Test
	@DisplayName("COURSE_THUMBNAIL purpose returns ABSTAIN")
	void thumbnailPurposeAbstains() {
		ResourceSummary res = sampleResource(1L, ResourcePurpose.COURSE_THUMBNAIL, ResourceStatus.READY);
		ResourceVote vote = voter.vote(new ResourceAccessContext(42L, res, ResourceAction.VIEW));
		assertThat(vote).isEqualTo(ResourceVote.ABSTAIN);
	}

	@Test
	@DisplayName("PENDING or FAILED resource status returns ABSTAIN")
	void nonReadyStatusAbstains() {
		ResourceSummary pending = sampleResource(1L, ResourcePurpose.LESSON_RESOURCE, ResourceStatus.PENDING);
		assertThat(voter.vote(new ResourceAccessContext(42L, pending, ResourceAction.VIEW))).isEqualTo(ResourceVote.ABSTAIN);

		ResourceSummary failed = sampleResource(1L, ResourcePurpose.LESSON_RESOURCE, ResourceStatus.FAILED);
		assertThat(voter.vote(new ResourceAccessContext(42L, failed, ResourceAction.VIEW))).isEqualTo(ResourceVote.ABSTAIN);
	}

	@Test
	@DisplayName("Deleted course returns ABSTAIN")
	void deletedCourseAbstains() {
		long courseId = 1L;
		ResourceSummary res = sampleResource(courseId, ResourcePurpose.LESSON_RESOURCE, ResourceStatus.READY);
		when(courseStructure.getCourseMetadata(courseId)).thenReturn(Optional.of(
				sampleMetadata(courseId, 999L, CourseStatus.PUBLISHED, true)));

		ResourceVote vote = voter.vote(new ResourceAccessContext(42L, res, ResourceAction.VIEW));
		assertThat(vote).isEqualTo(ResourceVote.ABSTAIN);
	}

	@Test
	@DisplayName("Not actively enrolled user returns ABSTAIN")
	void notEnrolledAbstains() {
		long courseId = 1L;
		long userId = 42L;
		ResourceSummary res = sampleResource(courseId, ResourcePurpose.LESSON_RESOURCE, ResourceStatus.READY);
		when(courseStructure.getCourseMetadata(courseId)).thenReturn(Optional.of(
				sampleMetadata(courseId, 999L, CourseStatus.PUBLISHED, false)));
		when(enrollmentRepository.existsByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE)).thenReturn(false);

		ResourceVote vote = voter.vote(new ResourceAccessContext(userId, res, ResourceAction.VIEW));
		assertThat(vote).isEqualTo(ResourceVote.ABSTAIN);
	}

	@Test
	@DisplayName("Active enrolled user returns GRANT for VIEW and DOWNLOAD")
	void activeEnrolledUserGranted() {
		long courseId = 1L;
		long userId = 42L;
		ResourceSummary res = sampleResource(courseId, ResourcePurpose.LESSON_RESOURCE, ResourceStatus.READY);
		when(courseStructure.getCourseMetadata(courseId)).thenReturn(Optional.of(
				sampleMetadata(courseId, 999L, CourseStatus.PUBLISHED, false)));
		when(enrollmentRepository.existsByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE)).thenReturn(true);

		assertThat(voter.vote(new ResourceAccessContext(userId, res, ResourceAction.VIEW))).isEqualTo(ResourceVote.GRANT);
		assertThat(voter.vote(new ResourceAccessContext(userId, res, ResourceAction.DOWNLOAD))).isEqualTo(ResourceVote.GRANT);
	}

	@Test
	@DisplayName("Request-scoped memoization ensures at most one query per request")
	void requestScopedMemoization() {
		long courseId = 1L;
		long userId = 42L;
		ResourceSummary res = sampleResource(courseId, ResourcePurpose.LESSON_RESOURCE, ResourceStatus.READY);
		when(courseStructure.getCourseMetadata(courseId)).thenReturn(Optional.of(
				sampleMetadata(courseId, 999L, CourseStatus.PUBLISHED, false)));
		when(enrollmentRepository.existsByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE)).thenReturn(true);

		MockHttpServletRequest request = new MockHttpServletRequest();
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		// Vote 10 times in same request
		for (int i = 0; i < 10; i++) {
			ResourceVote vote = voter.vote(new ResourceAccessContext(userId, res, ResourceAction.VIEW));
			assertThat(vote).isEqualTo(ResourceVote.GRANT);
		}

		// Repository exists check called exactly once
		verify(enrollmentRepository, times(1)).existsByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE);
	}

	@Test
	@DisplayName("Voter chain with OwnerResourceVoter: owner granted, stranger denied by default")
	void voterChainIntegration() {
		long courseId = 1L;
		long ownerId = 999L;
		long strangerId = 888L;
		ResourceSummary res = sampleResource(courseId, ResourcePurpose.LESSON_RESOURCE, ResourceStatus.READY);

		when(courseStructure.getCourseMetadata(courseId)).thenReturn(Optional.of(
				sampleMetadata(courseId, ownerId, CourseStatus.PUBLISHED, false)));

		OwnerResourceVoter ownerVoter = new OwnerResourceVoter(courseStructure);
		ResourceAccessService service = new ResourceAccessService(List.of(ownerVoter, voter));

		// 1. Owner can access even if not enrolled (granted by ownerVoter)
		boolean ownerAllowed = service.isAllowed(new ResourceAccessContext(ownerId, res, ResourceAction.VIEW));
		assertThat(ownerAllowed).isTrue();

		// 2. Stranger not enrolled is denied by default (both voters abstain)
		when(enrollmentRepository.existsByUserIdAndCourseIdAndStatus(strangerId, courseId, EnrollmentStatus.ACTIVE)).thenReturn(false);
		boolean strangerAllowed = service.isAllowed(new ResourceAccessContext(strangerId, res, ResourceAction.VIEW));
		assertThat(strangerAllowed).isFalse();
	}
}
