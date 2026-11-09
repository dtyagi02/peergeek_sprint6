package com.peergeek.module;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.peergeek.config.CourseSettings;
import com.peergeek.course.CourseContentChangePublisher;
import com.peergeek.course.CourseEntity;
import com.peergeek.course.CourseInputValidationException;
import com.peergeek.course.CourseService;
import com.peergeek.exception.NotFoundException;
import com.peergeek.lesson.LessonContentRepository;
import com.peergeek.lesson.LessonRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModuleService {
	private final CourseService courses;
	private final ModuleRepository modules;
	private final LessonRepository lessons;
	private final LessonContentRepository contents;
	private final CourseContentChangePublisher contentChanged;
	private final CourseSettings settings;
	private final EntityManager entityManager;

	public ModuleService(
			CourseService courses,
			ModuleRepository modules,
			LessonRepository lessons,
			LessonContentRepository contents,
			CourseContentChangePublisher contentChanged,
			CourseSettings settings,
			EntityManager entityManager) {
		this.courses = courses;
		this.modules = modules;
		this.lessons = lessons;
		this.contents = contents;
		this.contentChanged = contentChanged;
		this.settings = settings;
		this.entityManager = entityManager;
	}

	@Transactional
	public ModuleDto create(long courseId, String titleValue, String descriptionValue) {
		CourseEntity course = courses.requireOwnedForUpdate(courseId);
		long count = modules.countByCourse_Id(courseId);
		if (count >= settings.getMaximumModulesPerCourse()) {
			throw new CourseInputValidationException("modules", "course has reached the technical module limit");
		}
		String title = title(titleValue);
		String description = description(descriptionValue);
		List<ModuleEntity> siblings = modules.findAllByCourse_IdOrderByPositionAscIdAsc(courseId);
		int position = siblings.isEmpty() ? 0 : siblings.getLast().getPosition() + 1;
		ModuleEntity module = modules.saveAndFlush(new ModuleEntity(course, title, description, position));
		contentChanged.touch(course);
		return ModuleDto.from(module);
	}

	@Transactional
	public ModuleDto patch(long moduleId, JsonNode patch) {
		ModuleEntity module = requireModule(moduleId);
		CourseEntity course = courses.requireOwnedForUpdate(module.getCourse().getId());
		module = modules.findByIdForUpdate(moduleId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		rejectUnknownFields(patch, Set.of("title", "description"));
		String title = module.getTitle();
		String description = module.getDescription();
		if (patch.has("title")) title = title(text(patch.get("title"), "title"));
		if (patch.has("description")) description = patch.get("description").isNull()
				? null : description(text(patch.get("description"), "description"));
		module.update(title, description);
		entityManager.flush();
		contentChanged.touch(course);
		return ModuleDto.from(module);
	}

	@Transactional
	public void delete(long moduleId) {
		ModuleEntity module = requireModule(moduleId);
		CourseEntity course = courses.requireOwnedForUpdate(module.getCourse().getId());
		List<ModuleEntity> siblings = modules.findAllByCourse_IdOrderByPositionAscIdAsc(course.getId());
		List<Long> lessonIds = lessons.findAllByModule_IdOrderByPositionAscIdAsc(moduleId).stream()
				.map(com.peergeek.lesson.LessonEntity::getId).toList();
		if (!lessonIds.isEmpty()) contents.deleteAllByLessonIds(lessonIds);
		lessons.deleteAllByModule_Id(moduleId);
		modules.delete(module);
		int next = 0;
		for (ModuleEntity sibling : siblings) {
			if (!sibling.getId().equals(moduleId)) sibling.setPosition(next++);
		}
		contentChanged.touch(course);
	}

	@Transactional
	public void reorder(long courseId, List<Long> requestedIds) {
		CourseEntity course = courses.requireOwnedForUpdate(courseId);
		if (requestedIds == null) throw new CourseInputValidationException("moduleIds", "is required");
		List<ModuleEntity> existing = modules.findAllByCourse_IdOrderByPositionAscIdAsc(courseId);
		validateExactIds(requestedIds, existing.stream().map(ModuleEntity::getId).toList(), "moduleIds");
		List<ModuleEntity> locked = modules.findAllByIdForUpdate(existing.stream().map(ModuleEntity::getId).toList());
		var byId = new java.util.HashMap<Long, ModuleEntity>();
		locked.forEach(module -> byId.put(module.getId(), module));
		for (int position = 0; position < requestedIds.size(); position++) {
			ModuleEntity module = byId.get(requestedIds.get(position));
			if (module == null) throw new CourseInputValidationException("moduleIds", "must exactly match course modules");
			module.setPosition(position);
		}
		entityManager.flush();
		contentChanged.touch(course);
	}

	@Transactional(readOnly = true)
	public ModuleEntity requireModule(long moduleId) {
		ModuleEntity module = modules.findByIdAndCourse_DeletedAtIsNull(moduleId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		courses.requireOwned(module.getCourse().getId());
		return module;
	}

	private void validateExactIds(List<Long> requested, List<Long> existing, String field) {
		Set<Long> requestedSet = new HashSet<>(requested);
		if (requestedSet.size() != requested.size() || !requestedSet.equals(new HashSet<>(existing))
				|| requested.size() != existing.size()) {
			throw new CourseInputValidationException(field, "must contain each module ID exactly once");
		}
	}

	private String title(String value) {
		String normalized = value == null ? "" : value.trim();
		if (normalized.isEmpty() || normalized.length() > settings.getMaximumModuleTitleLength()) {
			throw new CourseInputValidationException("title", "must be between 1 and "
					+ settings.getMaximumModuleTitleLength() + " characters");
		}
		return normalized;
	}

	private String description(String value) {
		if (value == null) return null;
		String normalized = value.trim();
		if (normalized.length() > settings.getMaximumModuleDescriptionLength()) {
			throw new CourseInputValidationException("description", "must be no more than "
					+ settings.getMaximumModuleDescriptionLength() + " characters");
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
}
