package com.peergeek.enrollment;

import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.file.ResourceAccessContext;
import com.peergeek.file.ResourceAccessVoter;
import com.peergeek.file.ResourceAction;
import com.peergeek.file.ResourcePurpose;
import com.peergeek.file.ResourceStatus;
import com.peergeek.file.ResourceVote;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class EnrolledLearnerVoter implements ResourceAccessVoter {

	private final EnrollmentRepository enrollmentRepository;
	private final CourseStructureQueryService courseStructureQueryService;

	public EnrolledLearnerVoter(
			EnrollmentRepository enrollmentRepository,
			CourseStructureQueryService courseStructureQueryService) {
		this.enrollmentRepository = enrollmentRepository;
		this.courseStructureQueryService = courseStructureQueryService;
	}

	@Override
	public ResourceVote vote(ResourceAccessContext context) {
		if (context.userId() == null || context.resource() == null || context.resource().courseId() == null) {
			return ResourceVote.ABSTAIN;
		}

		if (context.action() != ResourceAction.VIEW && context.action() != ResourceAction.DOWNLOAD) {
			return ResourceVote.ABSTAIN;
		}

		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			HttpServletRequest request = attributes.getRequest();
			String uri = request.getRequestURI();
			if (uri != null && uri.contains("/instructor/")) {
				return ResourceVote.ABSTAIN;
			}
		}

		if (context.resource().purpose() != ResourcePurpose.LESSON_RESOURCE) {
			return ResourceVote.ABSTAIN;
		}

		if (context.resource().status() != ResourceStatus.READY) {
			return ResourceVote.ABSTAIN;
		}

		long courseId = context.resource().courseId();
		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId).orElse(null);
		if (metadata == null || metadata.deleted()) {
			return ResourceVote.ABSTAIN;
		}

		boolean isEnrolled = checkEnrolled(context.userId(), courseId);
		return isEnrolled ? ResourceVote.GRANT : ResourceVote.ABSTAIN;
	}

	private boolean checkEnrolled(long userId, long courseId) {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			HttpServletRequest request = attributes.getRequest();
			String key = "enrolled_voter_" + userId + "_" + courseId;
			Boolean cached = (Boolean) request.getAttribute(key);
			if (cached != null) {
				return cached;
			}
			boolean enrolled = enrollmentRepository.existsByUserIdAndCourseIdAndStatus(
					userId, courseId, EnrollmentStatus.ACTIVE);
			request.setAttribute(key, enrolled);
			return enrolled;
		}
		return enrollmentRepository.existsByUserIdAndCourseIdAndStatus(
				userId, courseId, EnrollmentStatus.ACTIVE);
	}
}
