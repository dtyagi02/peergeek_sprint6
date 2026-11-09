package com.peergeek.course;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peergeek.exception.NotFoundException;
import com.peergeek.lesson.LessonContentRepository;
import com.peergeek.lesson.LessonEntity;
import com.peergeek.lesson.LessonOutlineRow;
import com.peergeek.lesson.LessonRepository;
import com.peergeek.module.ModuleEntity;
import com.peergeek.module.ModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseStructureQueryServiceImpl implements CourseStructureQueryService {
	private final CourseRepository courses;
	private final ModuleRepository modules;
	private final LessonRepository lessons;
	private final LessonContentRepository contents;

	public CourseStructureQueryServiceImpl(
			CourseRepository courses,
			ModuleRepository modules,
			LessonRepository lessons,
			LessonContentRepository contents) {
		this.courses = courses;
		this.modules = modules;
		this.lessons = lessons;
		this.contents = contents;
	}

	@Override
	@Transactional(readOnly = true)
	public CourseStructure getStructure(long courseId) {
		CourseEntity course = courses.findByIdAndDeletedAtIsNull(courseId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		List<ModuleEntity> moduleRows = modules.findAllByCourse_IdOrderByPositionAscIdAsc(courseId);
		List<Long> moduleIds = moduleRows.stream().map(ModuleEntity::getId).toList();
		Map<Long, List<LessonSnapshot>> byModule = new HashMap<>();
		if (!moduleIds.isEmpty()) {
			for (LessonOutlineRow row : lessons.findOutlineRowsByModuleIds(moduleIds)) {
				byModule.computeIfAbsent(row.moduleId(), ignored -> new ArrayList<>()).add(
						new LessonSnapshot(row.id(), row.title(), row.summary(), row.position(),
								row.wordCount(), row.hasContent()));
			}
		}
		List<ModuleSnapshot> snapshots = moduleRows.stream()
				.map(module -> new ModuleSnapshot(module.getId(), module.getTitle(), module.getDescription(), module.getPosition(),
						byModule.getOrDefault(module.getId(), List.of())))
				.toList();
		CourseSummary summary = new CourseSummary(course.getId(), course.getOwner().getId(),
				course.getTitle(), course.getDescription(), course.getDifficulty(), course.getStatus(), course.getVersion());
		return new CourseStructure(summary, snapshots);
	}

	@Override
	@Transactional(readOnly = true)
	public LessonContentSnapshot getLessonContent(long lessonId) {
		LessonEntity lesson = lessons.findByIdAndModule_Course_DeletedAtIsNull(lessonId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		return contents.findByLesson_Id(lesson.getId())
				.map(content -> new LessonContentSnapshot("tiptap-json-v1", content.getDocument(),
						content.getWordCount(), content.getVersion(), content.getUpdatedAt()))
				.orElseGet(() -> new LessonContentSnapshot("tiptap-json-v1", emptyDocument(), 0, 0, null));
	}

	@Override
	@Transactional(readOnly = true)
	public Long getCourseOwnerId(long courseId) {
		return courses.findByIdAndDeletedAtIsNull(courseId)
				.map(course -> course.getOwner().getId())
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<LessonLocation> getLessonLocation(long lessonId) {
		return courses.findLessonLocation(lessonId);
	}

	@Override
	@Transactional(readOnly = true)
	public Set<Long> findExistingLessonIds(Set<Long> lessonIds) {
		if (lessonIds.isEmpty()) return Set.of();
		return Set.copyOf(courses.findExistingLessonIds(List.copyOf(lessonIds)));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<CourseMetadataSnapshot> getCourseMetadata(long courseId) {
		if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes attributes) {
			jakarta.servlet.http.HttpServletRequest request = attributes.getRequest();
			String key = "course_metadata_req_" + courseId;
			@SuppressWarnings("unchecked")
			Optional<CourseMetadataSnapshot> cached = (Optional<CourseMetadataSnapshot>) request.getAttribute(key);
			if (cached != null) {
				return cached;
			}
			Optional<CourseMetadataSnapshot> fetched = fetchMetadata(courseId);
			request.setAttribute(key, fetched);
			return fetched;
		}
		return fetchMetadata(courseId);
	}

	private Optional<CourseMetadataSnapshot> fetchMetadata(long courseId) {
		List<CourseMetadataRow> rows = courses.findCourseMetadataRows(courseId);
		if (rows.isEmpty()) return Optional.empty();
		CourseMetadataRow first = rows.getFirst();
		List<String> topics = rows.stream()
				.map(CourseMetadataRow::topicName)
				.filter(java.util.Objects::nonNull)
				.collect(Collectors.toUnmodifiableList());
		return Optional.of(new CourseMetadataSnapshot(first.courseId(), first.ownerId(), first.status(),
				first.deleted(), first.version(), first.title(), first.description(), first.categoryId(),
				first.categoryName(), first.categoryActive(), first.difficulty(), topics, first.thumbnailResourceId()));
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, CourseMetadataSnapshot> getCourseMetadataBulk(Collection<Long> courseIds) {
		if (courseIds == null || courseIds.isEmpty()) {
			return Map.of();
		}
		List<CourseMetadataRow> rows = courses.findCourseMetadataRowsBulk(courseIds);
		Map<Long, List<CourseMetadataRow>> grouped = rows.stream()
				.collect(Collectors.groupingBy(CourseMetadataRow::courseId));
		Map<Long, CourseMetadataSnapshot> result = new HashMap<>();
		for (Map.Entry<Long, List<CourseMetadataRow>> entry : grouped.entrySet()) {
			List<CourseMetadataRow> courseRows = entry.getValue();
			if (courseRows.isEmpty()) continue;
			CourseMetadataRow first = courseRows.getFirst();
			List<String> topics = courseRows.stream()
					.map(CourseMetadataRow::topicName)
					.filter(java.util.Objects::nonNull)
					.collect(Collectors.toUnmodifiableList());
			result.put(entry.getKey(), new CourseMetadataSnapshot(first.courseId(), first.ownerId(), first.status(),
					first.deleted(), first.version(), first.title(), first.description(), first.categoryId(),
					first.categoryName(), first.categoryActive(), first.difficulty(), topics, first.thumbnailResourceId()));
		}
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<PublishedCourseSnapshot> getPublishedCourse(long courseId) {
		Optional<CourseEntity> optionalCourse = courses.findByIdAndDeletedAtIsNull(courseId);
		if (optionalCourse.isEmpty()) {
			return Optional.empty();
		}
		CourseEntity course = optionalCourse.get();
		if (course.getStatus() != CourseStatus.PUBLISHED) {
			return Optional.empty();
		}
		CategoryEntity category = course.getCategory();
		List<String> topics = course.getTopics().stream()
				.map(CourseTopicEntity::getName)
				.toList();
		List<ModuleEntity> moduleRows = modules.findAllByCourse_IdOrderByPositionAscIdAsc(courseId);
		List<Long> moduleIds = moduleRows.stream().map(ModuleEntity::getId).toList();
		Map<Long, List<PublishedLessonOutline>> lessonsByModule = new HashMap<>();
		int totalLessons = 0;
		if (!moduleIds.isEmpty()) {
			List<LessonOutlineRow> lessonRows = lessons.findOutlineRowsByModuleIds(moduleIds);
			totalLessons = lessonRows.size();
			for (LessonOutlineRow row : lessonRows) {
				lessonsByModule.computeIfAbsent(row.moduleId(), ignored -> new ArrayList<>())
						.add(new PublishedLessonOutline(row.title()));
			}
		}
		List<PublishedModuleOutline> outline = moduleRows.stream()
				.map(m -> new PublishedModuleOutline(m.getTitle(), lessonsByModule.getOrDefault(m.getId(), List.of())))
				.toList();

		return Optional.of(new PublishedCourseSnapshot(
				course.getId(),
				course.getTitle(),
				course.getDescription(),
				category == null ? null : category.getId(),
				category == null ? null : category.getName(),
				category == null ? null : category.getSlug(),
				course.getDifficulty(),
				topics,
				course.getOwner().getId(),
				course.getThumbnailResourceId(),
				course.getPublishedAt(),
				moduleRows.size(),
				totalLessons,
				outline));
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isPublished(long courseId) {
		return courses.existsByIdAndStatusAndDeletedAtIsNull(courseId, CourseStatus.PUBLISHED);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Long> listPublishedCourseIds(Long afterId, int limit) {
		int safeLimit = Math.max(1, limit);
		return courses.findPublishedCourseIdsAfter(
				afterId, org.springframework.data.domain.PageRequest.of(0, safeLimit));
	}

	private JsonNode emptyDocument() {
		ObjectNode doc = JsonNodeFactory.instance.objectNode();
		doc.put("type", "doc");
		doc.putArray("content").add(JsonNodeFactory.instance.objectNode().put("type", "paragraph"));
		return doc;
	}
}
