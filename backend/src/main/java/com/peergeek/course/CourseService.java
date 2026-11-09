package com.peergeek.course;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.common.PageResponse;
import com.peergeek.common.Pagination;
import com.peergeek.config.CourseSettings;
import com.peergeek.exception.ConflictException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.lesson.LessonOutlineRow;
import com.peergeek.lesson.LessonRepository;
import com.peergeek.module.ModuleEntity;
import com.peergeek.module.ModuleRepository;
import com.peergeek.security.CurrentUserProvider;
import com.peergeek.security.OwnershipGuard;
import com.peergeek.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {
	private final CourseRepository courses;
	private final CategoryRepository categories;
	private final ModuleRepository modules;
	private final LessonRepository lessons;
	private final UserRepository users;
	private final CurrentUserProvider currentUser;
	private final OwnershipGuard ownership;
	private final AuditService audit;
	private final CourseSettings settings;
	private final EntityManager entityManager;

	public CourseService(
			CourseRepository courses,
			CategoryRepository categories,
			ModuleRepository modules,
			LessonRepository lessons,
			UserRepository users,
			CurrentUserProvider currentUser,
			OwnershipGuard ownership,
			AuditService audit,
			CourseSettings settings,
			EntityManager entityManager) {
		this.courses = courses;
		this.categories = categories;
		this.modules = modules;
		this.lessons = lessons;
		this.users = users;
		this.currentUser = currentUser;
		this.ownership = ownership;
		this.audit = audit;
		this.settings = settings;
		this.entityManager = entityManager;
	}

	@Transactional
	public CourseDto create(CourseCreateRequest request) {
		String title = validateTitle(request.title());
		String description = validateOptionalText(request.description(), settings.getMaximumDescriptionLength(),
				"description");
		CategoryEntity category = request.categoryId() == null ? null : activeCategory(request.categoryId());
		List<String> topics = validateTopics(request.topics());
		var actor = currentUser.requireCurrentUser();
		var user = users.findById(actor.id()).orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		Instant now = Instant.now();
		CourseEntity course = new CourseEntity(user, title, description, category, request.difficulty(), now);
		course.replaceTopics(topics);
		course = courses.saveAndFlush(course);
		audit.record(new AuditEvent(actor.id(), CourseAuditActions.COURSE_CREATED,
				"COURSE", Long.toString(course.getId()), Map.of()));
		return toDto(course);
	}

	@Transactional(readOnly = true)
	public PageResponse<CourseDto> list(Integer page, Integer size, String sort, CourseStatus status, String query) {
		var actor = currentUser.requireCurrentUser();
		Pageable pageable = coursePageable(page, size, sort);
		String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
		Page<CourseEntity> found = courses.findOwnedCourses(actor.id(), status, normalizedQuery, pageable);
		Map<Long, CourseCountRow> counts = counts(found.getContent());
		return PageResponse.from(found.map(course -> toDto(course, counts.get(course.getId()))));
	}

	@Transactional(readOnly = true)
	public CourseDto get(long courseId) {
		return toDto(requireOwned(courseId));
	}

	@Transactional
	public CourseDto patch(long courseId, JsonNode patch) {
		CourseEntity course = requireOwnedForUpdate(courseId);
		long version = requiredVersion(patch);
		if (version != course.getVersion()) {
			throw new ConflictException("The course was changed elsewhere. Reload it before saving.");
		}
		rejectUnknownFields(patch, Set.of("version", "title", "description", "categoryId", "difficulty", "topics"));
		String title = course.getTitle();
		String description = course.getDescription();
		CategoryEntity category = course.getCategory();
		CourseDifficulty difficulty = course.getDifficulty();
		List<String> topics = course.getTopics().stream().map(CourseTopicEntity::getName).toList();
		List<String> changed = new ArrayList<>();
		if (patch.has("title")) {
			title = validateTitle(textValue(patch.get("title"), "title"));
			if (!title.equals(course.getTitle())) changed.add("title");
		}
		if (patch.has("description")) {
			description = patch.get("description").isNull() ? null
					: validateOptionalText(textValue(patch.get("description"), "description"),
							settings.getMaximumDescriptionLength(), "description");
			if (!java.util.Objects.equals(description, course.getDescription())) changed.add("description");
		}
		if (patch.has("categoryId")) {
			JsonNode value = patch.get("categoryId");
			category = value.isNull() ? null : activeCategory(longValue(value, "categoryId"));
			if (!java.util.Objects.equals(category == null ? null : category.getId(),
					course.getCategory() == null ? null : course.getCategory().getId())) changed.add("categoryId");
		}
		if (patch.has("difficulty")) {
			JsonNode value = patch.get("difficulty");
			difficulty = value.isNull() ? null : difficultyValue(textValue(value, "difficulty"));
			if (difficulty != course.getDifficulty()) changed.add("difficulty");
		}
		if (patch.has("topics")) {
			topics = validateTopics(readTopics(patch.get("topics")));
			if (!topics.equals(course.getTopics().stream().map(CourseTopicEntity::getName).toList())) {
				changed.add("topics");
			}
		}
		course.updateDetails(title, description, category, difficulty);
		if (patch.has("topics")) course.replaceTopics(topics);
		if (!changed.isEmpty()) {
			entityManager.flush();
			audit.record(new AuditEvent(currentUser.requireCurrentUser().id(), CourseAuditActions.COURSE_UPDATED,
					"COURSE", Long.toString(courseId), Map.of("changedFields", List.copyOf(changed))));
		}
		return toDto(course);
	}

	@Transactional
	public void delete(long courseId) {
		CourseEntity course = requireOwnedForUpdate(courseId);
		if (course.getStatus() != CourseStatus.DRAFT) {
			throw new CourseStateConflictException("COURSE_NOT_DRAFT",
					"Only draft courses can be deleted.");
		}
		course.softDelete(Instant.now());
		audit.record(new AuditEvent(currentUser.requireCurrentUser().id(), CourseAuditActions.COURSE_DELETED,
				"COURSE", Long.toString(courseId), Map.of()));
	}

	@Transactional(readOnly = true)
	public CourseOutline outline(long courseId) {
		CourseEntity course = requireOwned(courseId);
		return toOutline(course);
	}

	@Transactional(readOnly = true)
	public CourseEntity requireOwned(long courseId) {
		CourseEntity course = courses.findByIdAndDeletedAtIsNull(courseId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		ownership.requireOwnerOrNotFound(course.getOwner().getId(), currentUser.requireCurrentUser().id());
		return course;
	}

	@Transactional
	public CourseEntity requireOwnedForUpdate(long courseId) {
		CourseEntity course = courses.findByIdAndDeletedAtIsNullForUpdate(courseId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		ownership.requireOwnerOrNotFound(course.getOwner().getId(), currentUser.requireCurrentUser().id());
		return course;
	}

	public CourseOutline toOutline(CourseEntity course) {
		List<ModuleEntity> moduleRows = modules.findAllByCourse_IdOrderByPositionAscIdAsc(course.getId());
		if (moduleRows.isEmpty()) return new CourseOutline(course.getId(), List.of());
		List<Long> moduleIds = moduleRows.stream().map(ModuleEntity::getId).toList();
		Map<Long, List<CourseOutline.LessonOutline>> lessonsByModule = new HashMap<>();
		for (LessonOutlineRow row : lessons.findOutlineRowsByModuleIds(moduleIds)) {
			lessonsByModule.computeIfAbsent(row.moduleId(), ignored -> new ArrayList<>()).add(
					new CourseOutline.LessonOutline(row.id(), row.title(), row.summary(), row.position(),
							row.hasContent(), row.wordCount()));
		}
		List<CourseOutline.ModuleOutline> moduleOutlines = moduleRows.stream()
				.map(module -> new CourseOutline.ModuleOutline(module.getId(), module.getTitle(),
						module.getDescription(), module.getPosition(),
						lessonsByModule.getOrDefault(module.getId(), List.of())))
				.toList();
		return new CourseOutline(course.getId(), moduleOutlines);
	}

	private CourseDto toDto(CourseEntity course) {
		return toDto(course, counts(List.of(course)).get(course.getId()));
	}

	private CourseDto toDto(CourseEntity course, CourseCountRow counts) {
		CourseCountRow safeCounts = counts == null ? new CourseCountRow(course.getId(), 0, 0, 0) : counts;
		return new CourseDto(
				course.getId(), course.getOwner().getId(), course.getTitle(), course.getDescription(),
				course.getCategory() == null ? null : CategoryDto.from(course.getCategory()),
				course.getDifficulty(), course.getStatus(),
				course.getTopics().stream().map(CourseTopicEntity::getName).toList(),
				safeCounts.moduleCount(), safeCounts.lessonCount(), safeCounts.totalWordCount(),
				course.getContentUpdatedAt(), course.getCreatedAt(), course.getUpdatedAt(), course.getVersion());
	}

	private Map<Long, CourseCountRow> counts(List<CourseEntity> courseRows) {
		if (courseRows.isEmpty()) return Map.of();
		Map<Long, CourseCountRow> result = new HashMap<>();
		for (CourseCountRow count : courses.countsByCourseIds(courseRows.stream().map(CourseEntity::getId).toList())) {
			result.put(count.courseId(), count);
		}
		return result;
	}

	private Pageable coursePageable(Integer page, Integer size, String sort) {
		if (sort == null || sort.isBlank()) {
			return Pagination.pageable(
					page == null ? Pagination.DEFAULT_PAGE : page, size, "updatedAt", "desc");
		}
		String[] parts = sort.split(",", -1);
		if (parts.length != 2 || !Set.of("updatedAt", "createdAt", "title").contains(parts[0].trim())) {
			throw new CourseInputValidationException("sort", "must be one of updatedAt, createdAt, title with asc or desc");
		}
		return Pagination.pageable(page, size, parts[0].trim(), parts[1].trim());
	}

	private String validateTitle(String value) {
		String title = value == null ? "" : value.trim();
		if (title.length() < settings.getMinimumTitleLength() || title.length() > settings.getMaximumTitleLength()) {
			throw new CourseInputValidationException("title", "must be between "
					+ settings.getMinimumTitleLength() + " and " + settings.getMaximumTitleLength() + " characters");
		}
		return title;
	}

	private String validateOptionalText(String value, int max, String field) {
		if (value == null) return null;
		String normalized = value.trim();
		if (normalized.length() > max) {
			throw new CourseInputValidationException(field, "must be no more than " + max + " characters");
		}
		return normalized;
	}

	private List<String> validateTopics(List<String> values) {
		if (values == null || values.isEmpty()) return List.of();
		if (values.size() > settings.getMaximumTopics()) {
			throw new CourseInputValidationException("topics", "must contain no more than "
					+ settings.getMaximumTopics() + " topics");
		}
		Set<String> seen = new HashSet<>();
		List<String> topics = new ArrayList<>();
		for (int index = 0; index < values.size(); index++) {
			String topic = values.get(index) == null ? "" : values.get(index).trim();
			if (topic.length() < settings.getMinimumTopicLength()
					|| topic.length() > settings.getMaximumTopicLength()) {
				throw new CourseInputValidationException("topics[" + index + "]", "must be between "
						+ settings.getMinimumTopicLength() + " and " + settings.getMaximumTopicLength() + " characters");
			}
			if (!seen.add(topic.toLowerCase(Locale.ROOT))) {
				throw new CourseInputValidationException("topics[" + index + "]", "must be unique ignoring case");
			}
			topics.add(topic);
		}
		return List.copyOf(topics);
	}

	private CategoryEntity activeCategory(long id) {
		return categories.findByIdAndActiveTrue(id)
				.orElseThrow(() -> new CourseInputValidationException("categoryId", "must reference an active category"));
	}

	private List<String> readTopics(JsonNode node) {
		if (!node.isArray()) throw new CourseInputValidationException("topics", "must be an array of strings");
		List<String> result = new ArrayList<>();
		for (JsonNode item : node) {
			if (!item.isTextual()) throw new CourseInputValidationException("topics", "must contain only strings");
			result.add(item.textValue());
		}
		return result;
	}

	private long requiredVersion(JsonNode patch) {
		if (patch == null || !patch.isObject() || !patch.has("version") || !patch.get("version").canConvertToLong()) {
			throw new CourseInputValidationException("version", "is required and must be an integer");
		}
		return patch.get("version").longValue();
	}

	private String textValue(JsonNode node, String field) {
		if (node == null || !node.isTextual()) throw new CourseInputValidationException(field, "must be a string");
		return node.textValue();
	}

	private long longValue(JsonNode node, String field) {
		if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
			throw new CourseInputValidationException(field, "must be an integer");
		}
		return node.longValue();
	}

	private CourseDifficulty difficultyValue(String value) {
		try {
			return CourseDifficulty.valueOf(value);
		} catch (IllegalArgumentException exception) {
			throw new CourseInputValidationException("difficulty", "must be BEGINNER, INTERMEDIATE, or ADVANCED");
		}
	}

	private void rejectUnknownFields(JsonNode patch, Set<String> allowed) {
		if (patch == null || !patch.isObject()) throw new CourseInputValidationException("request", "must be an object");
		patch.fieldNames().forEachRemaining(field -> {
			if (!allowed.contains(field)) throw new CourseInputValidationException(field, "is not supported");
		});
	}
}
