package com.peergeek.courselearning.instructor;

import com.peergeek.courselearning.assignment.AssignmentEntity;
import com.peergeek.courselearning.assignment.AssignmentRepository;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.enrollment.EnrollmentEntity;
import com.peergeek.courselearning.enrollment.EnrollmentRepository;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.profile.LearningUserProfileEntity;
import com.peergeek.courselearning.profile.LearningUserProfileRepository;
import com.peergeek.courselearning.progress.CourseProgressEntity;
import com.peergeek.courselearning.progress.CourseProgressRepository;
import com.peergeek.courselearning.submission.SubmissionEntity;
import com.peergeek.courselearning.submission.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InstructorStudentService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseProgressRepository progressRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final LearningUserProfileRepository userProfileRepository;

    public InstructorStudentService(
            CourseRepository courseRepository,
            EnrollmentRepository enrollmentRepository,
            CourseProgressRepository progressRepository,
            AssignmentRepository assignmentRepository,
            SubmissionRepository submissionRepository,
            LearningUserProfileRepository userProfileRepository) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.progressRepository = progressRepository;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public CourseStudentDetailDto getCourseStudentDetail(Long courseId, Long studentId, Long instructorId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found"));

        if (!course.getOwnerId().equals(instructorId)) {
            throw new ForbiddenException("You are not authorized to view student records for this course");
        }

        LearningUserProfileEntity student = userProfileRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found"));

        EnrollmentEntity enrollment = enrollmentRepository.findByUserIdAndCourseId(studentId, courseId)
                .orElseThrow(() -> new NotFoundException("The student is not enrolled in this course"));

        CourseProgressEntity progress = progressRepository.findById(enrollment.getId()).orElse(null);
        int totalLessons = progress != null ? progress.getTotalLessons() : 0;
        int completedLessons = progress != null ? progress.getCompletedLessons() : 0;
        int progressPercent = progress != null ? progress.getPercent() : 0;
        java.time.Instant completedAt = progress != null ? progress.getCompletedAt() : null;

        List<AssignmentEntity> assignments = assignmentRepository.findByCourseIdOrderByCreatedAtDesc(courseId);
        Map<Long, String> assignmentTitles = assignments.stream()
                .collect(Collectors.toMap(AssignmentEntity::getId, AssignmentEntity::getTitle));
        List<Long> assignmentIds = assignments.stream().map(AssignmentEntity::getId).toList();

        List<CourseStudentDetailDto.StudentSubmissionSummary> submissions = new ArrayList<>();
        if (!assignmentIds.isEmpty()) {
            List<SubmissionEntity> userSubmissions = submissionRepository.findByUserIdAndAssignmentIdIn(studentId, assignmentIds);
            submissions = userSubmissions.stream().map(s -> new CourseStudentDetailDto.StudentSubmissionSummary(
                    s.getAssignmentId(),
                    assignmentTitles.getOrDefault(s.getAssignmentId(), "Assignment #" + s.getAssignmentId()),
                    s.getStatus() != null ? s.getStatus().name() : "SUBMITTED",
                    s.getTotalPoints(),
                    BigDecimal.valueOf(100),
                    s.getCreatedAt(),
                    false
            )).toList();
        }

        CourseStudentDetailDto.StudentSummary studentSummary = new CourseStudentDetailDto.StudentSummary(
                student.getId(),
                student.getUsername(),
                student.getDisplayName(),
                null,
                null,
                student.getAvatarUrl()
        );

        return new CourseStudentDetailDto(
                studentSummary,
                courseId,
                course.getTitle(),
                enrollment.getStatus().name(),
                enrollment.getCreatedAt(),
                enrollment.getUpdatedAt(),
                totalLessons,
                completedLessons,
                progressPercent,
                completedAt,
                submissions
        );
    }
}
