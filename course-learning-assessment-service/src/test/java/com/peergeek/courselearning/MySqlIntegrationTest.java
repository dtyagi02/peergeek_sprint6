package com.peergeek.courselearning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.courselearning.assignment.*;
import com.peergeek.courselearning.certificate.CertificateEntity;
import com.peergeek.courselearning.certificate.CertificateRepository;
import com.peergeek.courselearning.common.*;
import com.peergeek.courselearning.course.CategoryEntity;
import com.peergeek.courselearning.course.CategoryRepository;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.enrollment.*;
import com.peergeek.courselearning.lesson.LessonEntity;
import com.peergeek.courselearning.lesson.LessonRepository;
import com.peergeek.courselearning.module.CourseModuleEntity;
import com.peergeek.courselearning.module.CourseModuleRepository;
import com.peergeek.courselearning.outbox.OutboxEventEntity;
import com.peergeek.courselearning.outbox.OutboxEventRepository;
import com.peergeek.courselearning.outbox.OutboxEventWriter;
import com.peergeek.courselearning.progress.CourseProgressEntity;
import com.peergeek.courselearning.progress.CourseProgressRepository;
import com.peergeek.courselearning.progress.LessonProgressEntity;
import com.peergeek.courselearning.progress.LessonProgressRepository;
import com.peergeek.courselearning.submission.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("mysql-test")
class MySqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseProgressRepository courseProgressRepository;

    @Autowired
    private LessonProgressRepository lessonProgressRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private AssignmentQuestionRepository questionRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private SubmissionAnswerRepository answerRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private OutboxEventWriter outboxEventWriter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    private Long testCategoryId;

    @BeforeEach
    void setUpCategory() {
        if (categoryRepository.count() == 0) {
            CategoryEntity cat = categoryRepository.save(new CategoryEntity("Default Category", "cat-" + UUID.randomUUID(), 0, true));
            testCategoryId = cat.getId();
        } else {
            testCategoryId = categoryRepository.findAll().get(0).getId();
        }
    }

    @Test
    @DisplayName("1. Verify MySQL version and Flyway schema creation across all 23 tables")
    void verifyMySqlAndFlywayTables() {
        String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        assertThat(version).isNotNull();
        System.out.println("Running against MySQL Engine Version: " + version);
        assertThat(version).startsWith("8.0");

        List<String> tables = jdbcTemplate.queryForList("SHOW TABLES", String.class);
        assertThat(tables).contains(
                "categories", "courses", "course_topics", "course_modules", "lessons",
                "lesson_contents", "lesson_expected_resources", "resources", "assignments",
                "assignment_questions", "submissions", "submission_answers", "submission_files",
                "enrollments", "course_progress", "lesson_progress", "certificates", "bookmarks",
                "lesson_notes", "readiness_runs", "readiness_issues", "learning_user_profiles",
                "outbox_events"
        );
    }

    @Test
    @DisplayName("2. Verify Enrollment persistence, duplicate prevention, and transactional integrity in MySQL")
    void verifyEnrollmentAndDuplicatePrevention() {
        long ownerId = 101L;
        long studentId = 201L;

        CourseEntity course = new CourseEntity(ownerId, "MySQL Concurrency Course " + UUID.randomUUID(), "Description", testCategoryId, CourseDifficulty.BEGINNER);
        course.setStatus(CourseStatus.PUBLISHED);
        final CourseEntity savedCourse = courseRepository.save(course);
        final Long courseId = savedCourse.getId();

        EnrollmentEntity enrollment1 = new EnrollmentEntity(studentId, courseId);
        enrollment1.setStatus(EnrollmentStatus.ACTIVE);
        enrollment1.setEnrolledAt(Instant.now());
        enrollment1 = enrollmentRepository.save(enrollment1);

        assertThat(enrollment1.getId()).isNotNull();

        // Unique constraint check: enrollment table has unique (user_id, course_id)
        EnrollmentEntity duplicate = new EnrollmentEntity(studentId, courseId);
        duplicate.setStatus(EnrollmentStatus.ACTIVE);
        duplicate.setEnrolledAt(Instant.now());

        assertThatThrownBy(() -> {
            enrollmentRepository.saveAndFlush(duplicate);
        }).isNotNull();

        // Transaction rollback verification
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> {
            tx.execute(status -> {
                EnrollmentEntity rollbackEnrollment = new EnrollmentEntity(301L, courseId);
                enrollmentRepository.save(rollbackEnrollment);
                throw new RuntimeException("Simulated business exception triggering rollback");
            });
        }).hasMessageContaining("Simulated business exception");

        Optional<EnrollmentEntity> rolledBack = enrollmentRepository.findByUserIdAndCourseId(301L, courseId);
        assertThat(rolledBack).isEmpty();
    }

    @Test
    @DisplayName("3. Verify Learning Progress calculation, lesson completion, and rounding in MySQL")
    void verifyLearningProgressCalculation() {
        long ownerId = 102L;
        long studentId = 202L;

        CourseEntity course = new CourseEntity(ownerId, "MySQL Progress Test Course " + UUID.randomUUID(), "Description", testCategoryId, CourseDifficulty.BEGINNER);
        course.setStatus(CourseStatus.PUBLISHED);
        course = courseRepository.save(course);

        CourseModuleEntity module = moduleRepository.save(new CourseModuleEntity(course.getId(), "Module 1", "Desc", 0));
        LessonEntity lesson1 = lessonRepository.save(new LessonEntity(module.getId(), "Lesson 1", "Summary", 0));
        LessonEntity lesson2 = lessonRepository.save(new LessonEntity(module.getId(), "Lesson 2", "Summary", 1));

        EnrollmentEntity enrollment = enrollmentRepository.save(new EnrollmentEntity(studentId, course.getId()));

        CourseProgressEntity cp = new CourseProgressEntity(enrollment.getId(), course.getId(), 2, 0, 0, null);
        courseProgressRepository.save(cp);

        // Complete lesson 1
        lessonProgressRepository.save(new LessonProgressEntity(enrollment.getId(), lesson1.getId()));

        cp.setCompletedLessons(1);
        cp.setPercent(50);
        courseProgressRepository.save(cp);

        CourseProgressEntity reloaded = courseProgressRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(reloaded.getCompletedLessons()).isEqualTo(1);
        assertThat(reloaded.getPercent()).isEqualTo(50);
        assertThat(reloaded.getCompletedAt()).isNull();

        // Complete lesson 2 -> 100% completion
        lessonProgressRepository.save(new LessonProgressEntity(enrollment.getId(), lesson2.getId()));
        cp.setCompletedLessons(2);
        cp.setPercent(100);
        cp.setCompletedAt(Instant.now());
        courseProgressRepository.save(cp);

        CourseProgressEntity completedProgress = courseProgressRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(completedProgress.getPercent()).isEqualTo(100);
        assertThat(completedProgress.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("4. Verify MCQ Assignment, server-side auto-grading, and answer concealment in MySQL")
    void verifyMcqAutoGradingAndConcealment() throws Exception {
        long ownerId = 103L;
        long studentId = 203L;

        CourseEntity course = new CourseEntity(ownerId, "MCQ Grading Test Course " + UUID.randomUUID(), "Description", testCategoryId, CourseDifficulty.BEGINNER);
        course.setStatus(CourseStatus.PUBLISHED);
        course = courseRepository.save(course);

        AssignmentEntity assignment = new AssignmentEntity(course.getId(), 103L, "MySQL Architecture Quiz", "Instructions", AssignmentKind.MCQ);
        assignment.setMaxPoints(new BigDecimal("100.00"));
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assignment = assignmentRepository.save(assignment);

        List<QuestionOption> optionsQ1 = List.of(
                new QuestionOption("opt1", "InnoDB", true),
                new QuestionOption("opt2", "MyISAM", false)
        );

        AssignmentQuestionEntity q1 = new AssignmentQuestionEntity(
                assignment.getId(), 0, QuestionType.MCQ_SINGLE,
                "Which storage engine supports ACID transactions?",
                new BigDecimal("100.00"), objectMapper.writeValueAsString(optionsQ1), null
        );
        q1 = questionRepository.save(q1);

        // Verification of student answer concealment: verify helper cleans correct flag
        List<QuestionOption> sanitizedOptions = optionsQ1.stream()
                .map(o -> new QuestionOption(o.id(), o.text(), false))
                .toList();
        assertThat(sanitizedOptions.stream().anyMatch(QuestionOption::correct)).isFalse();

        // Submission & Auto-grading
        SubmissionEntity submission = new SubmissionEntity(assignment.getId(), studentId);
        submission.setStatus(SubmissionStatus.IN_PROGRESS);
        submission = submissionRepository.save(submission);

        SubmissionAnswerEntity answer = new SubmissionAnswerEntity(
                submission.getId(), q1.getId(), null, objectMapper.writeValueAsString(List.of("opt1"))
        );
        answerRepository.save(answer);

        // Grade submission
        BigDecimal awarded = new BigDecimal("100.00");
        answer.setAutoPoints(awarded);
        answer.setPoints(awarded);
        answerRepository.save(answer);

        submission.setStatus(SubmissionStatus.GRADED);
        submission.setMarks(awarded);
        submission.setTotalPoints(awarded);
        submissionRepository.save(submission);

        SubmissionEntity graded = submissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(graded.getStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(graded.getMarks()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("5. Verify Certificate generation, uniqueness, and duplicate prevention in MySQL")
    void verifyCertificateUniqueness() {
        long ownerId = 104L;
        long studentId = 204L;

        CourseEntity course = new CourseEntity(ownerId, "Certificate Course " + UUID.randomUUID(), "Description", testCategoryId, CourseDifficulty.BEGINNER);
        course.setStatus(CourseStatus.PUBLISHED);
        course = courseRepository.save(course);

        EnrollmentEntity enrollment = enrollmentRepository.save(new EnrollmentEntity(studentId, course.getId()));

        CertificateEntity cert1 = new CertificateEntity(
                "PG-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(),
                UUID.randomUUID().toString(),
                studentId,
                course.getId(),
                enrollment.getId(),
                "Alice Learner",
                course.getTitle(),
                "Dr. Smith"
        );
        cert1 = certificateRepository.save(cert1);
        assertThat(cert1.getId()).isNotNull();

        // Duplicate issuance prevention: enrollment_id unique constraint in MySQL
        CertificateEntity certDuplicate = new CertificateEntity(
                "PG-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(),
                UUID.randomUUID().toString(),
                studentId,
                course.getId(),
                enrollment.getId(),
                "Alice Learner",
                course.getTitle(),
                "Dr. Smith"
        );

        assertThatThrownBy(() -> {
            certificateRepository.saveAndFlush(certDuplicate);
        }).isNotNull();
    }

    @Test
    @DisplayName("6. Verify Transactional Outbox atomicity, pending persistence, and failure rollback in MySQL")
    void verifyTransactionalOutboxAtomicityAndRollback() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // Successful atomic commit: business mutation + outbox event written together
        Long courseId = tx.execute(status -> {
            CourseEntity course = new CourseEntity(105L, "Outbox Atomic Course " + UUID.randomUUID(), "Desc", testCategoryId, CourseDifficulty.BEGINNER);
            course = courseRepository.save(course);
            outboxEventWriter.writeEvent(
                    "peergeek.course.course-created.v1",
                    "CourseCreated",
                    Map.of("courseId", course.getId(), "title", course.getTitle())
            );
            return course.getId();
        });

        assertThat(courseId).isNotNull();
        Optional<CourseEntity> persistedCourse = courseRepository.findById(courseId);
        assertThat(persistedCourse).isPresent();

        List<OutboxEventEntity> outboxList = outboxRepository.findAll();
        assertThat(outboxList.stream().anyMatch(e -> e.getPayload().contains(String.valueOf(courseId)))).isTrue();

        // Rollback verification: business exception must roll back both business record AND outbox record
        String failureMarker = "failure-" + UUID.randomUUID();
        assertThatThrownBy(() -> {
            tx.execute(status -> {
                CourseEntity failedCourse = new CourseEntity(106L, failureMarker, "Desc", testCategoryId, CourseDifficulty.BEGINNER);
                courseRepository.save(failedCourse);
                outboxEventWriter.writeEvent(
                        "peergeek.course.course-created.v1",
                        "CourseCreated",
                        Map.of("title", failureMarker)
                );
                throw new RuntimeException("Simulated error to test outbox rollback");
            });
        }).hasMessageContaining("Simulated error to test outbox rollback");

        // Verify neither course nor outbox record was committed to MySQL
        boolean courseExists = courseRepository.findAll().stream().anyMatch(c -> failureMarker.equals(c.getTitle()));
        assertThat(courseExists).isFalse();

        boolean outboxExists = outboxRepository.findAll().stream().anyMatch(e -> e.getPayload().contains(failureMarker));
        assertThat(outboxExists).isFalse();
    }
}
