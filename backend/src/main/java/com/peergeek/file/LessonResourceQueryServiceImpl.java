package com.peergeek.file;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.course.CourseThumbnailAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LessonResourceQueryServiceImpl implements LessonResourceQueryService {
	private final ResourceRepository resources;
	private final LessonExpectedResourceRepository expected;
	private final CourseStructureQueryService structure;
	private final CourseThumbnailAccessor thumbnails;

	public LessonResourceQueryServiceImpl(
			ResourceRepository resources,
			LessonExpectedResourceRepository expected,
			CourseStructureQueryService structure,
			CourseThumbnailAccessor thumbnails) {
		this.resources = resources;
		this.expected = expected;
		this.structure = structure;
		this.thumbnails = thumbnails;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ResourceSnapshot> listResources(long lessonId) {
		return resources.findByLessonIdAndDeletedAtIsNullAndPurposeOrderByPositionAsc(
						lessonId, ResourcePurpose.LESSON_RESOURCE)
				.stream().map(LessonResourceQueryServiceImpl::resourceSnapshot).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ExpectedResourceSnapshot> listExpected(long lessonId) {
		return expected.findByLessonIdOrderByResourceTypeAsc(lessonId).stream()
				.map(row -> new ExpectedResourceSnapshot(row.getResourceType(), row.getMinCount(), row.getLabel()))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<CourseLessonResourceState> getCourseResourceState(long courseId) {
		CourseStructureQueryService.CourseStructure course = structure.getStructure(courseId);
		List<Long> lessonIds = course.modules().stream()
				.flatMap(module -> module.lessons().stream())
				.map(CourseStructureQueryService.LessonSnapshot::id)
				.toList();
		Map<Long, List<CourseResourceSnapshot>> byLesson = new HashMap<>();
		for (ResourceEntity resource : resources
				.findByCourseIdAndLessonIdIsNotNullAndDeletedAtIsNullOrderByLessonIdAscPositionAsc(courseId)) {
			byLesson.computeIfAbsent(resource.getLessonId(), ignored -> new ArrayList<>())
					.add(new CourseResourceSnapshot(resource.getId(), resource.getType(), resource.getTitle(),
							resource.getStatus(), resource.getFailureReason(), resource.getMimeType(), resource.getSizeBytes(),
							resource.getChecksumSha256(), resource.getExternalUrl(), resource.getPosition()));
		}
		Map<Long, List<ExpectedResourceSnapshot>> expectedByLesson = new HashMap<>();
		if (!lessonIds.isEmpty()) {
			for (LessonExpectedResourceEntity row : expected.findByLessonIdIn(lessonIds)) {
				expectedByLesson.computeIfAbsent(row.getLessonId(), ignored -> new ArrayList<>())
						.add(new ExpectedResourceSnapshot(
								row.getResourceType(), row.getMinCount(), row.getLabel()));
			}
		}
		return lessonIds.stream()
				.filter(lessonId -> byLesson.containsKey(lessonId) || expectedByLesson.containsKey(lessonId))
				.map(lessonId -> new CourseLessonResourceState(lessonId,
						expectedByLesson.getOrDefault(lessonId, List.of()),
						byLesson.getOrDefault(lessonId, List.of())))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ResourceSummary> getThumbnail(long courseId) {
		return thumbnails.getThumbnailResourceId(courseId)
				.flatMap(resources::findByIdAndDeletedAtIsNull)
				.map(ResourceSummary::from);
	}

	private static ResourceSnapshot resourceSnapshot(ResourceEntity resource) {
		return new ResourceSnapshot(resource.getId(), resource.getType(), resource.getTitle(),
				resource.getOriginalFilename(), resource.getMimeType(), resource.getSizeBytes(),
				resource.getStatus(), resource.getFailureReason(), resource.getExternalUrl(),
				resource.getPosition(), resource.getCreatedAt());
	}
}
