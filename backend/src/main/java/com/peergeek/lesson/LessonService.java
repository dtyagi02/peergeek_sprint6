package com.peergeek.lesson;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peergeek.config.CourseSettings;
import com.peergeek.course.CourseContentChangePublisher;
import com.peergeek.course.CourseEntity;
import com.peergeek.course.CourseInputValidationException;
import com.peergeek.course.CourseService;
import com.peergeek.exception.ConflictException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.module.ModuleEntity;
import com.peergeek.module.ModuleRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LessonService {
	private final CourseService courses;
	private final ModuleRepository modules;
	private final LessonRepository lessons;
	private final LessonContentRepository contents;
	private final LessonContentValidator contentValidator;
	private final CourseContentChangePublisher contentChanged;
	private final CourseSettings settings;
	private final EntityManager entityManager;

	public LessonService(
			CourseService courses,
			ModuleRepository modules,
			LessonRepository lessons,
			LessonContentRepository contents,
			LessonContentValidator contentValidator,
			CourseContentChangePublisher contentChanged,
			CourseSettings settings,
			EntityManager entityManager) {
		this.courses = courses;
		this.modules = modules;
		this.lessons = lessons;
		this.contents = contents;
		this.contentValidator = contentValidator;
		this.contentChanged = contentChanged;
		this.settings = settings;
		this.entityManager = entityManager;
	}

	@Transactional
	public LessonDto create(long moduleId, String titleValue, String summaryValue) {
		ModuleEntity initial = requireModule(moduleId);
		CourseEntity course = courses.requireOwnedForUpdate(initial.getCourse().getId());
		ModuleEntity module = modules.findByIdForUpdate(moduleId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		List<LessonEntity> siblings = lessons.findAllByModule_IdOrderByPositionAscIdAsc(moduleId);
		if (siblings.size() >= settings.getMaximumLessonsPerModule()) {
			throw new CourseInputValidationException("lessons", "module has reached the technical lesson limit");
		}
		LessonEntity lesson = lessons.saveAndFlush(new LessonEntity(
				module, title(titleValue), summary(summaryValue), siblings.size()));
		contentChanged.touch(course);
		return LessonDto.from(lesson);
	}

	@Transactional
	public LessonDto patch(long lessonId, JsonNode patch) {
		LessonEntity lesson = requireLesson(lessonId);
		CourseEntity course = courses.requireOwnedForUpdate(lesson.getModule().getCourse().getId());
		ModuleEntity module = modules.findByIdForUpdate(lesson.getModule().getId())
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		lesson = lessons.findByIdForUpdate(lessonId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		rejectUnknownFields(patch, Set.of("title", "summary"));
		String title = lesson.getTitle();
		String summary = lesson.getSummary();
		if (patch.has("title")) title = title(text(patch.get("title"), "title"));
		if (patch.has("summary")) summary = patch.get("summary").isNull()
				? null : summary(text(patch.get("summary"), "summary"));
		lesson.update(title, summary);
		entityManager.flush();
		contentChanged.touch(course);
		return LessonDto.from(lesson);
	}

	@Transactional
	public void delete(long lessonId) {
		LessonEntity lesson = requireLesson(lessonId);
		CourseEntity course = courses.requireOwnedForUpdate(lesson.getModule().getCourse().getId());
		Long moduleId = lesson.getModule().getId();
		ModuleEntity module = modules.findByIdForUpdate(moduleId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		List<LessonEntity> siblings = lessons.findAllByModule_IdOrderByPositionAscIdAsc(moduleId);
		contents.deleteById(lessonId);
		lessons.delete(lesson);
		int next = 0;
		for (LessonEntity sibling : siblings) {
			if (!sibling.getId().equals(lessonId)) sibling.setPosition(next++);
		}
		contentChanged.touch(course);
	}

	@Transactional
	public void reorder(long moduleId, List<Long> requestedIds) {
		ModuleEntity initial = requireModule(moduleId);
		CourseEntity course = courses.requireOwnedForUpdate(initial.getCourse().getId());
		ModuleEntity module = modules.findByIdForUpdate(moduleId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		if (requestedIds == null) throw new CourseInputValidationException("lessonIds", "is required");
		List<LessonEntity> existing = lessons.findAllByModule_IdOrderByPositionAscIdAsc(moduleId);
		validateExactIds(requestedIds, existing.stream().map(LessonEntity::getId).toList());
		var byId = new HashMap<Long, LessonEntity>();
		existing.forEach(lesson -> byId.put(lesson.getId(), lesson));
		for (int position = 0; position < requestedIds.size(); position++) {
			LessonEntity lesson = byId.get(requestedIds.get(position));
			if (lesson == null) throw new CourseInputValidationException("lessonIds", "must exactly match module lessons");
			lesson.setPosition(position);
		}
		entityManager.flush();
		contentChanged.touch(course);
	}

	@Transactional
	public LessonDto move(long lessonId, long targetModuleId, int position) {
		LessonEntity initialLesson = requireLesson(lessonId);
		ModuleEntity initialTarget = requireModule(targetModuleId);
		Long sourceModuleId = initialLesson.getModule().getId();
		Long sourceCourseId = initialLesson.getModule().getCourse().getId();
		Long targetCourseId = initialTarget.getCourse().getId();
		CourseEntity course = courses.requireOwnedForUpdate(sourceCourseId);
		if (!sourceCourseId.equals(targetCourseId)) {
			courses.requireOwned(targetCourseId);
			throw new CourseInputValidationException("targetModuleId", "must belong to the same course as the lesson");
		}
		List<Long> moduleIds = java.util.stream.Stream.of(sourceModuleId, targetModuleId).distinct().sorted().toList();
		List<ModuleEntity> lockedModules = modules.findAllByIdForUpdate(moduleIds);
		var byId = new HashMap<Long, ModuleEntity>();
		lockedModules.forEach(module -> byId.put(module.getId(), module));
		ModuleEntity source = byId.get(sourceModuleId);
		ModuleEntity target = byId.get(targetModuleId);
		if (source == null || target == null) throw new NotFoundException("The requested resource was not found");
		LessonEntity lesson = lessons.findByIdForUpdate(lessonId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		List<LessonEntity> sourceLessons = new ArrayList<>(
				lessons.findAllByModule_IdOrderByPositionAscIdAsc(sourceModuleId));
		List<LessonEntity> targetLessons = sourceModuleId.equals(targetModuleId)
				? sourceLessons : new ArrayList<>(lessons.findAllByModule_IdOrderByPositionAscIdAsc(targetModuleId));
		sourceLessons.removeIf(item -> item.getId().equals(lessonId));
		if (!sourceModuleId.equals(targetModuleId)) {
			if (targetLessons.size() >= settings.getMaximumLessonsPerModule()) {
				throw new CourseInputValidationException("targetModuleId", "target module has reached the technical lesson limit");
			}
		} else {
			targetLessons = sourceLessons;
		}
		if (position < 0 || position > targetLessons.size()) {
			throw new CourseInputValidationException("position", "must be between 0 and " + targetLessons.size());
		}
		targetLessons.add(position, lesson);
		if (sourceModuleId.equals(targetModuleId)) {
			for (int index = 0; index < targetLessons.size(); index++) targetLessons.get(index).setPosition(index);
		} else {
			for (int index = 0; index < sourceLessons.size(); index++) sourceLessons.get(index).setPosition(index);
			for (int index = 0; index < targetLessons.size(); index++) {
				targetLessons.get(index).moveTo(target, index);
			}
		}
		entityManager.flush();
		contentChanged.touch(course);
		return LessonDto.from(lesson);
	}

	@Transactional(readOnly = true)
	public LessonContentDto getContent(long lessonId) {
		LessonEntity lesson = requireLesson(lessonId);
		return contents.findByLesson_Id(lessonId)
				.map(content -> new LessonContentDto("tiptap-json-v1", content.getDocument(),
						content.getWordCount(), content.getVersion(), content.getUpdatedAt()))
				.orElseGet(() -> new LessonContentDto("tiptap-json-v1", emptyDocument(), 0, 0, null));
	}

	@Transactional
	public LessonContentDto saveContent(long lessonId, LessonContentSaveRequest request) {
		LessonEntity initial = requireLesson(lessonId);
		CourseEntity course = courses.requireOwnedForUpdate(initial.getModule().getCourse().getId());
		ModuleEntity module = modules.findByIdForUpdate(initial.getModule().getId())
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		LessonEntity lesson = lessons.findByIdForUpdate(lessonId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		if (request == null || request.baseVersion() == null || request.baseVersion() < 0) {
			throw new CourseInputValidationException("baseVersion", "is required and must be zero or greater");
		}
		LessonContentEntity content = contents.findByLesson_Id(lessonId).orElse(null);
		long actualVersion = content == null ? 0 : content.getVersion();
		if (request.baseVersion() != actualVersion) {
			throw new ConflictException("Lesson content changed elsewhere. Reload it before saving.");
		}
		int wordCount = contentValidator.validateAndCount(request.document());
		Instant now = Instant.now();
		if (content == null) {
			content = contents.saveAndFlush(new LessonContentEntity(lesson, request.document(), wordCount, now));
		} else {
			content.update(request.document(), wordCount, now);
			entityManager.flush();
		}
		contentChanged.touch(course);
		return new LessonContentDto("tiptap-json-v1", content.getDocument(), content.getWordCount(),
				content.getVersion(), content.getUpdatedAt());
	}

	@Transactional(readOnly = true)
	public LessonEntity requireLesson(long lessonId) {
		LessonEntity lesson = lessons.findByIdAndModule_Course_DeletedAtIsNull(lessonId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		courses.requireOwned(lesson.getModule().getCourse().getId());
		return lesson;
	}

	private ModuleEntity requireModule(long moduleId) {
		ModuleEntity module = modules.findByIdAndCourse_DeletedAtIsNull(moduleId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		courses.requireOwned(module.getCourse().getId());
		return module;
	}

	private void validateExactIds(List<Long> requested, List<Long> existing) {
		Set<Long> requestedSet = new HashSet<>(requested);
		if (requestedSet.size() != requested.size() || !requestedSet.equals(new HashSet<>(existing))
				|| requested.size() != existing.size()) {
			throw new CourseInputValidationException("lessonIds", "must contain each lesson ID exactly once");
		}
	}

	private String title(String value) {
		String normalized = value == null ? "" : value.trim();
		if (normalized.isEmpty() || normalized.length() > settings.getMaximumLessonTitleLength()) {
			throw new CourseInputValidationException("title", "must be between 1 and "
					+ settings.getMaximumLessonTitleLength() + " characters");
		}
		return normalized;
	}

	private String summary(String value) {
		if (value == null) return null;
		String normalized = value.trim();
		if (normalized.length() > settings.getMaximumLessonSummaryLength()) {
			throw new CourseInputValidationException("summary", "must be no more than "
					+ settings.getMaximumLessonSummaryLength() + " characters");
		}
		return normalized;
	}

	private String text(JsonNode node, String field) {
		if (node == null || !node.isTextual()) throw new CourseInputValidationException(field, "must be a string");
		return node.textValue();
	}

	private void rejectUnknownFields(JsonNode patch, Set<String> allowed) {
		if (patch == null || !patch.isObject()) throw new CourseInputValidationException("request", "must be an object");
		patch.fieldNames().forEachRemaining(field -> {
			if (!allowed.contains(field)) throw new CourseInputValidationException(field, "is not supported");
		});
	}

	private JsonNode emptyDocument() {
		ObjectNode doc = JsonNodeFactory.instance.objectNode();
		doc.put("type", "doc");
		ObjectNode paragraph = JsonNodeFactory.instance.objectNode();
		paragraph.put("type", "paragraph");
		doc.putArray("content").add(paragraph);
		return doc;
	}
}
