package com.peergeek.courselearning.enrollment;

import com.peergeek.courselearning.common.CourseStatus;
import com.peergeek.courselearning.common.EnrollmentStatus;
import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.course.CategoryDto;
import com.peergeek.courselearning.course.CategoryEntity;
import com.peergeek.courselearning.course.CategoryRepository;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.exception.CannotEnrollOwnCourseException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.file.ObjectStorageService;
import com.peergeek.courselearning.outbox.OutboxEventWriter;
import com.peergeek.courselearning.profile.LearningUserProfileEntity;
import com.peergeek.courselearning.profile.LearningUserProfileRepository;
import com.peergeek.courselearning.progress.ProgressService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final LearningUserProfileRepository userProfileRepository;
    private final ProgressService progressService;
    private final ObjectStorageService storageService;
    private final OutboxEventWriter outboxEventWriter;

    public EnrollmentService(
            EnrollmentRepository enrollmentRepository,
            CourseRepository courseRepository,
            CategoryRepository categoryRepository,
            LearningUserProfileRepository userProfileRepository,
            ProgressService progressService,
            ObjectStorageService storageService,
            OutboxEventWriter outboxEventWriter) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.userProfileRepository = userProfileRepository;
        this.progressService = progressService;
        this.storageService = storageService;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public EnrollmentDto enroll(long courseId, long userId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new NotFoundException("Course not published: " + courseId);
        }

        if (course.getOwnerId().equals(userId)) {
            throw new CannotEnrollOwnCourseException();
        }

        EnrollmentEntity enrollment = enrollmentRepository.findByUserIdAndCourseId(userId, courseId).orElse(null);
        Instant now = Instant.now();

        if (enrollment != null) {
            if (enrollment.getStatus() == EnrollmentStatus.ACTIVE) {
                CourseProgressDto progress = progressService.getOrRecomputeProgress(enrollment);
                return new EnrollmentDto(courseId, enrollment.getStatus(), enrollment.getEnrolledAt(), progress);
            }
            // Reactivate
            enrollment.setStatus(EnrollmentStatus.ACTIVE);
            enrollment.setEnrolledAt(now);
            enrollment.setWithdrawnAt(null);
            enrollment.setLastAccessedAt(now);
            enrollment = enrollmentRepository.save(enrollment);
        } else {
            enrollment = new EnrollmentEntity(userId, courseId);
            enrollment = enrollmentRepository.save(enrollment);
        }

        CourseProgressDto progress = progressService.getOrRecomputeProgress(enrollment);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", courseId);
        payload.put("userId", userId);
        payload.put("enrollmentId", enrollment.getId());
        payload.put("enrolledAt", now.toString());
        outboxEventWriter.writeEvent(
                "peergeek.learning.learner-enrolled.v1",
                "LearnerEnrolled",
                payload
        );

        return new EnrollmentDto(courseId, enrollment.getStatus(), enrollment.getEnrolledAt(), progress);
    }

    @Transactional
    public void withdraw(long courseId, long userId) {
        EnrollmentEntity enrollment = enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active enrollment not found for course: " + courseId));

        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setWithdrawnAt(Instant.now());
        enrollmentRepository.save(enrollment);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("courseId", courseId);
        payload.put("userId", userId);
        payload.put("enrollmentId", enrollment.getId());
        payload.put("withdrawnAt", enrollment.getWithdrawnAt().toString());
        outboxEventWriter.writeEvent(
                "peergeek.learning.learner-withdrawn.v1",
                "LearnerWithdrawn",
                payload
        );
    }

    @Transactional(readOnly = true)
    public EnrollmentStatusDto getEnrollmentStatus(long courseId, long userId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId).orElse(null);
        boolean isOwner = course != null && course.getOwnerId().equals(userId);
        boolean canEnroll = course != null && course.getStatus() == CourseStatus.PUBLISHED && !isOwner;

        Optional<EnrollmentEntity> enrollmentOpt = enrollmentRepository.findByUserIdAndCourseId(userId, courseId);
        if (enrollmentOpt.isEmpty() || enrollmentOpt.get().getStatus() != EnrollmentStatus.ACTIVE) {
            return new EnrollmentStatusDto(
                    false,
                    enrollmentOpt.map(EnrollmentEntity::getStatus).orElse(null),
                    isOwner,
                    canEnroll,
                    null
            );
        }

        EnrollmentEntity enrollment = enrollmentOpt.get();
        CourseProgressDto progress = progressService.getOrRecomputeProgress(enrollment);
        return new EnrollmentStatusDto(
                true,
                enrollment.getStatus(),
                isOwner,
                false,
                progress
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<MyCourseCardDto> getMyCourses(
            long userId, String status, String q, int page, int size, String sort) {
        List<EnrollmentEntity> allActive = enrollmentRepository.findByUserIdAndStatusOrderByEnrolledAtDesc(userId, EnrollmentStatus.ACTIVE);

        List<MyCourseCardDto> cards = new ArrayList<>();
        for (EnrollmentEntity enrollment : allActive) {
            CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(enrollment.getCourseId()).orElse(null);
            if (course == null) continue;

            if (q != null && !q.isBlank()) {
                if (!course.getTitle().toLowerCase().contains(q.trim().toLowerCase())) {
                    continue;
                }
            }

            CourseProgressDto progress = progressService.getOrRecomputeProgress(enrollment);

            if ("completed".equalsIgnoreCase(status) && progress.percent() < 100) {
                continue;
            }
            if ("in_progress".equalsIgnoreCase(status) && progress.percent() >= 100) {
                continue;
            }

            String thumbnailUrl = null;
            if (course.getThumbnailKey() != null) {
                thumbnailUrl = storageService.presignGet(course.getThumbnailKey(), Duration.ofHours(1));
            }

            String instructorName = "Instructor";
            Optional<LearningUserProfileEntity> profileOpt = userProfileRepository.findById(course.getOwnerId());
            if (profileOpt.isPresent()) {
                instructorName = profileOpt.get().getDisplayName() != null ? profileOpt.get().getDisplayName() : profileOpt.get().getUsername();
            }
            InstructorSummaryDto instructor = new InstructorSummaryDto(course.getOwnerId(), instructorName);

            CategoryDto catDto = null;
            if (course.getCategoryId() != null) {
                CategoryEntity cat = categoryRepository.findById(course.getCategoryId()).orElse(null);
                if (cat != null) {
                    catDto = CategoryDto.fromEntity(cat);
                }
            }

            ContinueLessonDto continueDto = progressService.getContinueLesson(enrollment);
            Long continueLessonId = continueDto != null ? continueDto.lessonId() : null;

            cards.add(new MyCourseCardDto(
                    course.getId(),
                    course.getTitle(),
                    thumbnailUrl,
                    instructor,
                    course.getDifficulty(),
                    course.getStatus().name(),
                    progress,
                    enrollment.getLastAccessedAt(),
                    continueLessonId,
                    catDto
            ));
        }

        // Sorting
        if (sort != null && sort.contains("lastAccessedAt")) {
            cards.sort((a, b) -> {
                if (a.lastAccessedAt() == null && b.lastAccessedAt() == null) return 0;
                if (a.lastAccessedAt() == null) return 1;
                if (b.lastAccessedAt() == null) return -1;
                return sort.contains("asc") ? a.lastAccessedAt().compareTo(b.lastAccessedAt()) : b.lastAccessedAt().compareTo(a.lastAccessedAt());
            });
        }

        int totalElements = cards.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<MyCourseCardDto> pageContent = cards.subList(fromIndex, toIndex);

        return PageResponse.of(pageContent, page, size, totalElements);
    }
}

