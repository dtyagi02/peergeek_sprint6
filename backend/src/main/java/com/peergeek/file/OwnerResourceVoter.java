package com.peergeek.file;

import com.peergeek.course.CourseStructureQueryService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class OwnerResourceVoter implements ResourceAccessVoter {
	private final CourseStructureQueryService courses;

	public OwnerResourceVoter(CourseStructureQueryService courses) {
		this.courses = courses;
	}

	@Override
	public ResourceVote vote(ResourceAccessContext context) {
		if (context.userId() == null || context.resource().courseId() == null) {
			return ResourceVote.ABSTAIN;
		}
		return courses.getCourseMetadata(context.resource().courseId())
				.filter(metadata -> !metadata.deleted())
				.map(metadata -> context.userId().equals(metadata.ownerId())
						? ResourceVote.GRANT : ResourceVote.ABSTAIN)
				.orElse(ResourceVote.ABSTAIN);
	}
}
