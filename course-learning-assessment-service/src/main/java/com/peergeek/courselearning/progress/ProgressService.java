package com.peergeek.courselearning.progress;

import com.peergeek.courselearning.common.CourseStatus;
import com.peergeek.courselearning.common.EnrollmentStatus;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.enrollment.*;
import com.peergeek.courselearning.exception.EnrollmentRequiredException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.lesson.LessonEntity;
import com.peergeek.courselearning.lesson.LessonRepository;
import com.peergeek.courselearning.module.CourseModuleEntity;
import com.peergeek.courselearning.module.CourseModuleRepository;
import com.peergeek.courselearning.outbox.OutboxEventWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProgressService {

    private final CourseProgressRepository courseProgressRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventWriter outboxEventWriter;

    public ProgressService(
            CourseProgressRepository courseProgressRepository,
            LessonProgressRepository lessonProgressRepository,
            EnrollmentRepository enrollmentRepository,
            CourseRepository courseRepository,
            CourseModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            ApplicationEventPublisher eventPublisher,
            OutboxEventWriter outboxEventWriter) {
        this.courseProgressRepository = courseProgressRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.eventPublisher = eventPublisher;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public CourseProgressDto getOrRecomputeProgress(EnrollmentEntity enrollment) {
        Optional<CourseProgressEntity> optional = courseProgressRepository.findById(enrollment.getId());
        if (optional.isPresent() && !optional.get().isStale()) {
            return toDto(optional.get());
        }
        return upsertAndComputeProgress(enrollment);
    }

    @Transactional
    public CourseProgressDto recomputeProgress(EnrollmentEntity enrollment) {
        return upsertAndComputeProgress(enrollment);
    }

    @Transactional(readOnly = true)
    public ContinueLessonDto getContinueLesson(EnrollmentEntity enrollment) {
        List<Long> orderedLessonIds = getOrderedLessonIds(enrollment.getCourseId());
        if (orderedLessonIds.isEmpty()) {
            return new ContinueLessonDto(null, ContinueReason.COMPLETED);
        }

        Set<Long> completedIds = lessonProgressRepository.findByEnrollmentIdAndLessonIdIn(
                enrollment.getId(), orderedLessonIds).stream()
                .map(LessonProgressEntity::getLessonId)
                .collect(Collectors.toSet());

        return resolveContinueLesson(orderedLessonIds, completedIds, enrollment.getLastLessonId());
    }

    @Transactional
    public CourseProgressDto setLessonCompletion(long courseId, long lessonId, long userId, boolean completed) {
        LessonEntity lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson not found: " + lessonId));
        CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId())
                .orElseThrow(() -> new NotFoundException("Module not found: " + lesson.getModuleId()));

        if (!module.getCourseId().equals(courseId)) {
            throw new NotFoundException("Lesson " + lessonId + " does not belong to course " + courseId);
        }

        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));

        EnrollmentEntity enrollment = enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE)
                .orElseThrow(() -> {
                    if (course.getStatus() != CourseStatus.PUBLISHED) {
                        return new NotFoundException("Course not found: " + courseId);
                    }
                    return new EnrollmentRequiredException();
                });

        Optional<LessonProgressEntity> existingOpt =
                lessonProgressRepository.findByEnrollmentIdAndLessonId(enrollment.getId(), lessonId);

        Instant now = Instant.now();
        if (completed) {
            if (existingOpt.isEmpty()) {
                LessonProgressEntity lp = new LessonProgressEntity(enrollment.getId(), lessonId);
                lp.setCompletedAt(now);
                lessonProgressRepository.save(lp);

                enrollment.setLastLessonId(lessonId);
                enrollment.setLastAccessedAt(now);
                enrollmentRepository.save(enrollment);

                CourseProgressDto progress = upsertAndComputeProgress(enrollment);

                eventPublisher.publishEvent(new LessonCompletedEvent(
                        courseId, userId, lessonId, progress.completedLessons(), progress.totalLessons()));

                if (progress.percent() >= 100) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("courseId", courseId);
                    payload.put("userId", userId);
                    payload.put("completedAt", now.toString());
                    outboxEventWriter.writeEvent(
                            "peergeek.learning.course-completed.v1",
                            "CourseCompleted",
                            payload
                    );
                }

                return progress;
            }
        } else {
            if (existingOpt.isPresent()) {
                lessonProgressRepository.deleteByEnrollmentIdAndLessonId(enrollment.getId(), lessonId);
                enrollment.setLastAccessedAt(now);
                enrollmentRepository.save(enrollment);

                return upsertAndComputeProgress(enrollment);
            }
        }

        return getOrRecomputeProgress(enrollment);
    }

    private CourseProgressDto upsertAndComputeProgress(EnrollmentEntity enrollment) {
        List<Long> currentLessonIds = getOrderedLessonIds(enrollment.getCourseId());
        int totalLessons = currentLessonIds.size();

        List<LessonProgressEntity> completedList = currentLessonIds.isEmpty()
                ? List.of()
                : lessonProgressRepository.findByEnrollmentIdAndLessonIdIn(enrollment.getId(), currentLessonIds);
        int completedLessons = completedList.size();
        int percent = totalLessons == 0 ? 0 : (int) Math.floor((double) completedLessons * 100.0 / totalLessons);
        Instant completedAt = (totalLessons > 0 && percent == 100) ? Instant.now() : null;

        CourseProgressEntity cp = courseProgressRepository.findById(enrollment.getId()).orElse(null);
        if (cp == null) {
            cp = new CourseProgressEntity(enrollment.getId(), enrollment.getCourseId(), totalLessons, completedLessons, percent, completedAt);
        } else {
            cp.setTotalLessons(totalLessons);
            cp.setCompletedLessons(completedLessons);
            cp.setPercent(percent);
            if (cp.getCompletedAt() == null && completedAt != null) {
                cp.setCompletedAt(completedAt);
            } else if (percent < 100) {
                cp.setCompletedAt(null);
            }
            cp.setStale(false);
            cp.setComputedAt(Instant.now());
            cp.setUpdatedAt(Instant.now());
        }
        cp = courseProgressRepository.save(cp);

        return toDto(cp);
    }

    private List<Long> getOrderedLessonIds(Long courseId) {
        List<CourseModuleEntity> modules = moduleRepository.findByCourseIdOrderByPositionAsc(courseId);
        List<Long> result = new ArrayList<>();
        for (CourseModuleEntity m : modules) {
            List<LessonEntity> lessons = lessonRepository.findByModuleIdOrderByPositionAsc(m.getId());
            lessons.forEach(l -> result.add(l.getId()));
        }
        return result;
    }

    private ContinueLessonDto resolveContinueLesson(List<Long> orderedLessonIds, Set<Long> completedIds, Long last) {
        if (orderedLessonIds.isEmpty()) {
            return new ContinueLessonDto(null, ContinueReason.COMPLETED);
        }

        // Rule 1: RESUME
        if (last != null && orderedLessonIds.contains(last) && !completedIds.contains(last)) {
            return new ContinueLessonDto(last, ContinueReason.RESUME);
        }

        // Rule 2: NEXT
        if (last != null && orderedLessonIds.contains(last)) {
            int lastIdx = orderedLessonIds.indexOf(last);
            for (int i = lastIdx + 1; i < orderedLessonIds.size(); i++) {
                Long candidate = orderedLessonIds.get(i);
                if (!completedIds.contains(candidate)) {
                    return new ContinueLessonDto(candidate, ContinueReason.NEXT);
                }
            }
        }

        // Rule 3: FIRST
        for (Long candidate : orderedLessonIds) {
            if (!completedIds.contains(candidate)) {
                return new ContinueLessonDto(candidate, ContinueReason.FIRST);
            }
        }

        // Rule 4: COMPLETED
        return new ContinueLessonDto(null, ContinueReason.COMPLETED);
    }

    private CourseProgressDto toDto(CourseProgressEntity entity) {
        return new CourseProgressDto(
                entity.getCompletedLessons(),
                entity.getTotalLessons(),
                entity.getPercent(),
                entity.getCompletedAt());
    }
}

