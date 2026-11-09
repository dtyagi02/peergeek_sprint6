package com.peergeek.file;

import java.util.List;
import java.util.Optional;

public interface LessonResourceQueryService {
	List<ResourceSnapshot> listResources(long lessonId);
	List<ExpectedResourceSnapshot> listExpected(long lessonId);
	List<CourseLessonResourceState> getCourseResourceState(long courseId);
	Optional<ResourceSummary> getThumbnail(long courseId);

	record ResourceSnapshot(
			Long id,
			ResourceType type,
			String title,
			String originalFilename,
			String mimeType,
			Long sizeBytes,
			ResourceStatus status,
			String failureReason,
			String externalUrl,
			int position,
			java.time.Instant createdAt) {
	}

	record ExpectedResourceSnapshot(ResourceType resourceType, int minCount, String label) {
	}

	record CourseLessonResourceState(
			long lessonId,
			List<ExpectedResourceSnapshot> expected,
			List<CourseResourceSnapshot> resources) {
		public CourseLessonResourceState {
			expected = List.copyOf(expected);
			resources = List.copyOf(resources);
		}
	}

	record CourseResourceSnapshot(
			Long id,
			ResourceType type,
			String title,
			ResourceStatus status,
			String failureReason,
			String mimeType,
			Long sizeBytes,
			String checksumSha256,
			String externalUrl,
			int position) {
	}
}
