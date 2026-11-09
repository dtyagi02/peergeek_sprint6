package com.peergeek.courselearning.lesson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.course.CourseSettings;
import com.peergeek.courselearning.exception.ConflictException;
import com.peergeek.courselearning.exception.CourseInputValidationException;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.module.CourseModuleEntity;
import com.peergeek.courselearning.module.CourseModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class LessonService {

    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final LessonContentRepository lessonContentRepository;
    private final LessonContentValidator contentValidator;
    private final CourseSettings settings;
    private final ObjectMapper objectMapper;

    public LessonService(
            CourseRepository courseRepository,
            CourseModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            LessonContentRepository lessonContentRepository,
            LessonContentValidator contentValidator,
            CourseSettings settings,
            ObjectMapper objectMapper) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.lessonContentRepository = lessonContentRepository;
        this.contentValidator = contentValidator;
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LessonDto create(long moduleId, Long ownerId, String titleValue, String summaryValue) {
        CourseModuleEntity module = requireModule(moduleId);
        CourseEntity course = findOwnedCourse(module.getCourseId(), ownerId);

        List<LessonEntity> siblings = lessonRepository.findByModuleIdOrderByPositionAsc(moduleId);
        if (siblings.size() >= settings.getMaximumLessonsPerModule()) {
            throw new CourseInputValidationException("lessons", "module has reached the technical lesson limit");
        }

        String title = validateTitle(titleValue);
        String summary = validateSummary(summaryValue);

        LessonEntity lesson = new LessonEntity(moduleId, title, summary, siblings.size());
        lesson = lessonRepository.save(lesson);

        course.setContentUpdatedAt(Instant.now());
        courseRepository.save(course);

        return LessonDto.from(lesson);
    }

    @Transactional
    public LessonDto patch(long lessonId, Long ownerId, JsonNode patch) {
        LessonEntity lesson = requireLesson(lessonId);
        CourseModuleEntity module = requireModule(lesson.getModuleId());
        CourseEntity course = findOwnedCourse(module.getCourseId(), ownerId);

        rejectUnknownFields(patch, Set.of("title", "summary"));

        if (patch.has("title")) {
            lesson.setTitle(validateTitle(text(patch.get("title"), "title")));
        }
        if (patch.has("summary")) {
            lesson.setSummary(patch.get("summary").isNull() ? null : validateSummary(text(patch.get("summary"), "summary")));
        }
        lesson.setUpdatedAt(Instant.now());
        lesson = lessonRepository.save(lesson);

        course.setContentUpdatedAt(Instant.now());
        courseRepository.save(course);

        return LessonDto.from(lesson);
    }

    @Transactional
    public void delete(long lessonId, Long ownerId) {
        LessonEntity lesson = requireLesson(lessonId);
        CourseModuleEntity module = requireModule(lesson.getModuleId());
        CourseEntity course = findOwnedCourse(module.getCourseId(), ownerId);

        lessonContentRepository.deleteById(lessonId);
        lessonRepository.delete(lesson);

        List<LessonEntity> remaining = lessonRepository.findByModuleIdOrderByPositionAsc(module.getId());
        for (int i = 0; i < remaining.size(); i++) {
            LessonEntity sib = remaining.get(i);
            sib.setPosition(i);
            lessonRepository.save(sib);
        }

        course.setContentUpdatedAt(Instant.now());
        courseRepository.save(course);
    }

    @Transactional
    public void reorder(long moduleId, Long ownerId, List<Long> requestedIds) {
        CourseModuleEntity module = requireModule(moduleId);
        CourseEntity course = findOwnedCourse(module.getCourseId(), ownerId);

        if (requestedIds == null) {
            throw new CourseInputValidationException("lessonIds", "is required");
        }

        List<LessonEntity> existing = lessonRepository.findByModuleIdOrderByPositionAsc(moduleId);
        validateExactIds(requestedIds, existing.stream().map(LessonEntity::getId).toList());

        Map<Long, LessonEntity> byId = new HashMap<>();
        existing.forEach(l -> byId.put(l.getId(), l));

        for (int pos = 0; pos < requestedIds.size(); pos++) {
            LessonEntity lesson = byId.get(requestedIds.get(pos));
            if (lesson == null) {
                throw new CourseInputValidationException("lessonIds", "must exactly match module lessons");
            }
            lesson.setPosition(pos);
            lessonRepository.save(lesson);
        }

        course.setContentUpdatedAt(Instant.now());
        courseRepository.save(course);
    }

    @Transactional
    public LessonDto move(long lessonId, Long ownerId, Long targetModuleId, Integer position) {
        LessonEntity lesson = requireLesson(lessonId);
        CourseModuleEntity sourceModule = requireModule(lesson.getModuleId());
        CourseModuleEntity targetModule = requireModule(targetModuleId);

        if (!sourceModule.getCourseId().equals(targetModule.getCourseId())) {
            throw new CourseInputValidationException("targetModuleId", "must belong to the same course as the lesson");
        }

        CourseEntity course = findOwnedCourse(sourceModule.getCourseId(), ownerId);

        List<LessonEntity> sourceLessons = new ArrayList<>(lessonRepository.findByModuleIdOrderByPositionAsc(sourceModule.getId()));
        List<LessonEntity> targetLessons = sourceModule.getId().equals(targetModuleId)
                ? sourceLessons
                : new ArrayList<>(lessonRepository.findByModuleIdOrderByPositionAsc(targetModuleId));

        sourceLessons.removeIf(item -> item.getId().equals(lessonId));

        if (!sourceModule.getId().equals(targetModuleId)) {
            if (targetLessons.size() >= settings.getMaximumLessonsPerModule()) {
                throw new CourseInputValidationException("targetModuleId", "target module has reached the technical lesson limit");
            }
        } else {
            targetLessons = sourceLessons;
        }

        if (position < 0 || position > targetLessons.size()) {
            throw new CourseInputValidationException("position", "must be between 0 and " + targetLessons.size());
        }

        lesson.setModuleId(targetModuleId);
        targetLessons.add(position, lesson);

        if (sourceModule.getId().equals(targetModuleId)) {
            for (int i = 0; i < targetLessons.size(); i++) {
                targetLessons.get(i).setPosition(i);
                lessonRepository.save(targetLessons.get(i));
            }
        } else {
            for (int i = 0; i < sourceLessons.size(); i++) {
                sourceLessons.get(i).setPosition(i);
                lessonRepository.save(sourceLessons.get(i));
            }
            for (int i = 0; i < targetLessons.size(); i++) {
                targetLessons.get(i).setPosition(i);
                lessonRepository.save(targetLessons.get(i));
            }
        }

        course.setContentUpdatedAt(Instant.now());
        courseRepository.save(course);

        return LessonDto.from(lesson);
    }

    @Transactional(readOnly = true)
    public LessonContentDto getContent(long lessonId) {
        requireLesson(lessonId);
        return lessonContentRepository.findById(lessonId)
                .map(content -> {
                    try {
                        JsonNode doc = objectMapper.readTree(content.getContentDocument());
                        return new LessonContentDto("tiptap-json-v1", doc, content.getWordCount(), content.getVersion(), content.getUpdatedAt());
                    } catch (JsonProcessingException e) {
                        return new LessonContentDto("tiptap-json-v1", emptyDocument(), content.getWordCount(), content.getVersion(), content.getUpdatedAt());
                    }
                })
                .orElseGet(() -> new LessonContentDto("tiptap-json-v1", emptyDocument(), 0, 0, null));
    }

    @Transactional
    public LessonContentDto saveContent(long lessonId, Long ownerId, LessonContentSaveRequest request) {
        LessonEntity lesson = requireLesson(lessonId);
        CourseModuleEntity module = requireModule(lesson.getModuleId());
        CourseEntity course = findOwnedCourse(module.getCourseId(), ownerId);

        if (request == null || request.baseVersion() == null || request.baseVersion() < 0) {
            throw new CourseInputValidationException("baseVersion", "is required and must be zero or greater");
        }

        LessonContentEntity content = lessonContentRepository.findById(lessonId).orElse(null);
        long actualVersion = content == null ? 0 : content.getVersion();
        if (request.baseVersion() != actualVersion) {
            throw new ConflictException("Lesson content changed elsewhere. Reload it before saving.");
        }

        int wordCount = contentValidator.validateAndCount(request.document());
        Instant now = Instant.now();
        String jsonDoc;
        try {
            jsonDoc = objectMapper.writeValueAsString(request.document());
        } catch (JsonProcessingException e) {
            throw new CourseInputValidationException("document", "failed to serialize document");
        }

        if (content == null) {
            content = new LessonContentEntity(lessonId, "tiptap-json-v1", jsonDoc, wordCount);
            content = lessonContentRepository.save(content);
        } else {
            content.setContentDocument(jsonDoc);
            content.setWordCount(wordCount);
            content.setUpdatedAt(now);
            content = lessonContentRepository.save(content);
        }

        course.setContentUpdatedAt(now);
        courseRepository.save(course);

        try {
            JsonNode doc = objectMapper.readTree(content.getContentDocument());
            return new LessonContentDto("tiptap-json-v1", doc, content.getWordCount(), content.getVersion(), content.getUpdatedAt());
        } catch (JsonProcessingException e) {
            return new LessonContentDto("tiptap-json-v1", emptyDocument(), content.getWordCount(), content.getVersion(), content.getUpdatedAt());
        }
    }

    public LessonEntity requireLesson(long lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson not found: " + lessonId));
    }

    public CourseModuleEntity requireModule(long moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("Module not found: " + moduleId));
    }

    private CourseEntity findOwnedCourse(Long courseId, Long ownerId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        if (!course.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("You are not the owner of this course");
        }
        return course;
    }

    private void validateExactIds(List<Long> requested, List<Long> existing) {
        Set<Long> requestedSet = new HashSet<>(requested);
        if (requestedSet.size() != requested.size() || !requestedSet.equals(new HashSet<>(existing))
                || requested.size() != existing.size()) {
            throw new CourseInputValidationException("lessonIds", "must contain each lesson ID exactly once");
        }
    }

    private String validateTitle(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > settings.getMaximumLessonTitleLength()) {
            throw new CourseInputValidationException("title", "must be between 1 and "
                    + settings.getMaximumLessonTitleLength() + " characters");
        }
        return normalized;
    }

    private String validateSummary(String value) {
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

