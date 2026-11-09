package com.peergeek.courselearning;

import com.peergeek.courselearning.common.*;
import com.peergeek.courselearning.course.CategoryEntity;
import com.peergeek.courselearning.course.CategoryRepository;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.enrollment.*;
import com.peergeek.courselearning.exception.BadRequestException;
import com.peergeek.courselearning.exception.EnrollmentRequiredException;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.file.ObjectStorageService;
import com.peergeek.courselearning.lesson.LessonEntity;
import com.peergeek.courselearning.lesson.LessonRepository;
import com.peergeek.courselearning.module.CourseModuleEntity;
import com.peergeek.courselearning.module.CourseModuleRepository;
import com.peergeek.courselearning.resource.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ResourceStorageIntegrationTest {

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
    private ResourceRepository resourceRepository;

    @Autowired
    private ResourceUploadService uploadService;

    @Autowired
    private LearningContentService learningContentService;

    @Autowired
    private ObjectStorageService storageService;

    private Long instructorId;
    private Long otherInstructorId;
    private Long enrolledStudentId;
    private Long unenrolledStudentId;
    private CourseEntity testCourse;
    private CourseModuleEntity testModule;
    private LessonEntity testLesson;

    @BeforeEach
    void setUp() {
        instructorId = 501L;
        otherInstructorId = 502L;
        enrolledStudentId = 601L;
        unenrolledStudentId = 602L;

        CategoryEntity cat = categoryRepository.save(new CategoryEntity("Storage Category " + UUID.randomUUID(), "storage-" + UUID.randomUUID(), 0, true));

        testCourse = new CourseEntity(instructorId, "Storage Verification Course " + UUID.randomUUID(), "Description", cat.getId(), CourseDifficulty.INTERMEDIATE);
        testCourse.setStatus(CourseStatus.PUBLISHED);
        testCourse = courseRepository.save(testCourse);

        testModule = moduleRepository.save(new CourseModuleEntity(testCourse.getId(), "Module 1", "Desc", 0));
        testLesson = lessonRepository.save(new LessonEntity(testModule.getId(), "Lesson 1", "Desc", 0));

        EnrollmentEntity enrollment = new EnrollmentEntity(enrolledStudentId, testCourse.getId());
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollmentRepository.save(enrollment);
    }

    @Test
    @DisplayName("1. End-to-end resource upload, MinIO persistence, confirmation, and learner retrieval")
    void verifyEndToEndResourceUploadAndRetrieval() throws Exception {
        byte[] pdfBytes = "%PDF-1.4 Mock Spec PDF File For Phase 5 Audit Verification\n%%EOF".getBytes(StandardCharsets.UTF_8);
        String sha256Hex = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdfBytes));

        ResourceRequests.LessonUpload request = new ResourceRequests.LessonUpload(
                "phase5_audit_spec.pdf",
                "application/pdf",
                (long) pdfBytes.length,
                "PDF",
                "Phase 5 Audit Specification"
        );

        // 1. Instructor initiates upload
        ResourceRequests.UploadCreated created = uploadService.createLessonUpload(testLesson.getId(), instructorId, request);
        assertThat(created.resourceId()).isNotNull();
        assertThat(created.upload().url()).contains("http");

        ResourceEntity pending = resourceRepository.findById(created.resourceId()).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(ResourceStatus.PENDING.name());
        assertThat(pending.getObjectKey()).contains("courses/" + testCourse.getId() + "/lessons/" + testLesson.getId());

        // 2. Put bytes directly to MinIO at the assigned objectKey
        storageService.putObject(pending.getObjectKey(), pdfBytes, "application/pdf");

        // 3. Instructor confirms upload
        ResourceDto confirmed = uploadService.confirm(created.resourceId(), instructorId);
        assertThat(confirmed.status()).isEqualTo(ResourceStatus.READY.name());

        ResourceEntity ready = resourceRepository.findById(created.resourceId()).orElseThrow();
        assertThat(ready.getStatus()).isEqualTo(ResourceStatus.READY.name());

        // 4. Enrolled student retrieves fresh presigned URL
        PresignedUrlDto learnerUrl = learningContentService.getFreshResourceUrl(created.resourceId(), "inline", enrolledStudentId);
        assertThat(learnerUrl.url()).isNotNull();
        assertThat(learnerUrl.url()).contains(pending.getObjectKey());

        // 5. Verify byte content and SHA256 integrity from storage
        byte[] retrievedBytes = storageService.getObject(pending.getObjectKey()).readAllBytes();
        String retrievedSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(retrievedBytes));
        assertThat(retrievedSha256).isEqualTo(sha256Hex);
    }

    @Test
    @DisplayName("2. Access control: non-owning instructor and unenrolled learner are rejected with ForbiddenException")
    void verifyAccessControlEnforcement() {
        ResourceRequests.LessonUpload request = new ResourceRequests.LessonUpload(
                "secret_notes.pdf", "application/pdf", 1024L, "PDF", "Secret Notes"
        );

        // Non-owning instructor cannot initiate upload on this course
        assertThatThrownBy(() -> uploadService.createLessonUpload(testLesson.getId(), otherInstructorId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("You do not own this course");

        // Create a valid resource by owner
        ResourceRequests.UploadCreated created = uploadService.createLessonUpload(testLesson.getId(), instructorId, request);

        // Non-owning instructor cannot confirm
        assertThatThrownBy(() -> uploadService.confirm(created.resourceId(), otherInstructorId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("You do not own this course");

        // Unenrolled learner cannot retrieve resource download URL
        assertThatThrownBy(() -> learningContentService.getFreshResourceUrl(created.resourceId(), "download", unenrolledStudentId))
                .isInstanceOf(EnrollmentRequiredException.class)
                .hasMessageContaining("Enrollment is required");
    }

    @Test
    @DisplayName("3. File validation: unsupported file types and oversized payloads are rejected with BadRequestException")
    void verifyFileValidation() {
        // Executable rejected
        ResourceRequests.LessonUpload maliciousExe = new ResourceRequests.LessonUpload(
                "payload.exe", "application/octet-stream", 1024L, "DOCUMENT", "Hack"
        );
        assertThatThrownBy(() -> uploadService.createLessonUpload(testLesson.getId(), instructorId, maliciousExe))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Executable and script files are not allowed");

        // Script rejected
        ResourceRequests.LessonUpload maliciousScript = new ResourceRequests.LessonUpload(
                "script.sh", "application/x-sh", 1024L, "DOCUMENT", "Hack"
        );
        assertThatThrownBy(() -> uploadService.createLessonUpload(testLesson.getId(), instructorId, maliciousScript))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Executable and script files are not allowed");

        // Oversized PDF (> 100MB) rejected
        ResourceRequests.LessonUpload oversizedPdf = new ResourceRequests.LessonUpload(
                "huge_book.pdf", "application/pdf", 105 * 1024 * 1024L, "PDF", "Huge Book"
        );
        assertThatThrownBy(() -> uploadService.createLessonUpload(testLesson.getId(), instructorId, oversizedPdf))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PDF file exceeds maximum limit of 100MB");

        // Invalid mime type for PDF
        ResourceRequests.LessonUpload badMimePdf = new ResourceRequests.LessonUpload(
                "test.pdf", "text/plain", 1024L, "PDF", "Bad Mime"
        );
        assertThatThrownBy(() -> uploadService.createLessonUpload(testLesson.getId(), instructorId, badMimePdf))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mime type must be application/pdf");

        // Negative size rejected
        ResourceRequests.LessonUpload negativeSize = new ResourceRequests.LessonUpload(
                "empty.pdf", "application/pdf", -5L, "PDF", "Empty"
        );
        assertThatThrownBy(() -> uploadService.createLessonUpload(testLesson.getId(), instructorId, negativeSize))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("File size must be positive");
    }

    @Test
    @DisplayName("4. Historical MinIO storage objects physical existence verification")
    void verifyHistoricalStorageObjectsExist() {
        List<String> historicalKeys = List.of(
                "courses/1/thumbnail/b81b1961-f197-4241-aa6c-0b5cd7a3431a",
                "courses/31/lessons/452/aae79ea0-59e4-4bd3-a20a-b67fb42802f7",
                "courses/31/lessons/452/17e0926f-c396-4d81-b0f3-240b4b28b235",
                "courses/31/lessons/452/fe4353b9-d8b2-4dd6-acc3-360cf5557fb6",
                "courses/32/lessons/454/4c15c252-ac72-408c-83b9-6f006a122886",
                "courses/32/thumbnail/f73c8abe-da8b-4e01-92cd-c6ad7c463fe8",
                "courses/31/lessons/452/75e8bea7-0713-43cd-b293-fb2057057695",
                "courses/33/lessons/455/07ea1bf2-05c3-4537-b55c-208ff6d46654",
                "certificates/1/PG-A864308065F9.pdf"
        );

        System.out.println("Auditing 9 historical READY resources in MinIO storage bucket: " + storageService.getBucket());

        for (String key : historicalKeys) {
            boolean exists = storageService.exists(key);
            assertThat(exists)
                    .as("Historical storage object %s must exist in MinIO bucket %s", key, storageService.getBucket())
                    .isTrue();
        }
    }
}

