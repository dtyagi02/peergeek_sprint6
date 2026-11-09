package com.peergeek.courselearning.submission;

import com.peergeek.courselearning.assignment.AssignmentEntity;
import com.peergeek.courselearning.assignment.AssignmentRepository;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.file.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class SubmissionFileService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionFileRepository fileRepository;
    private final AssignmentRepository assignmentRepository;
    private final CourseRepository courseRepository;
    private final ObjectStorageService storageService;

    public SubmissionFileService(
            SubmissionRepository submissionRepository,
            SubmissionFileRepository fileRepository,
            AssignmentRepository assignmentRepository,
            CourseRepository courseRepository,
            ObjectStorageService storageService) {
        this.submissionRepository = submissionRepository;
        this.fileRepository = fileRepository;
        this.assignmentRepository = assignmentRepository;
        this.courseRepository = courseRepository;
        this.storageService = storageService;
    }

    @Transactional
    public SubmissionDtos.FileUploadResponse initiateUpload(
            long assignmentId, SubmissionDtos.FileUploadRequest request, Long userId) {
        SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId)
                .orElseThrow(() -> new NotFoundException("Submission attempt not found for assignment: " + assignmentId));

        String key = "assignments/" + assignmentId + "/submissions/" + submission.getId() + "/" + UUID.randomUUID() + "-" + sanitize(request.filename());
        SubmissionFileEntity file = new SubmissionFileEntity(
                submission.getId(),
                request.filename(),
                key,
                request.mimeType(),
                request.sizeBytes()
        );
        file.setStatus("PENDING");
        file = fileRepository.save(file);

        Duration ttl = Duration.ofMinutes(15);
        String presignedPut = storageService.presignPut(key, request.mimeType(), ttl);
        var details = new SubmissionDtos.FileUploadResponse.UploadDetails(
                URI.create(presignedPut),
                "PUT",
                Map.of("Content-Type", request.mimeType()),
                Instant.now().plus(ttl)
        );

        return new SubmissionDtos.FileUploadResponse(file.getId(), details);
    }

    @Transactional
    public SubmissionDtos.FileConfirmResponse confirmUpload(long assignmentId, long fileId, Long userId) {
        SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId)
                .orElseThrow(() -> new NotFoundException("Submission attempt not found for assignment: " + assignmentId));

        SubmissionFileEntity file = fileRepository.findByIdAndSubmissionId(fileId, submission.getId())
                .orElseThrow(() -> new NotFoundException("Submission file not found: " + fileId));

        file.setStatus("READY");
        file = fileRepository.save(file);

        return new SubmissionDtos.FileConfirmResponse(file.getId(), file.getStatus(), file.getOriginalFilename());
    }

    @Transactional
    public void deleteFile(long assignmentId, long fileId, Long userId) {
        SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId)
                .orElseThrow(() -> new NotFoundException("Submission attempt not found for assignment: " + assignmentId));

        SubmissionFileEntity file = fileRepository.findByIdAndSubmissionId(fileId, submission.getId())
                .orElseThrow(() -> new NotFoundException("Submission file not found: " + fileId));

        try {
            storageService.deleteObject(file.getObjectKey());
        } catch (Exception ignored) {}

        fileRepository.delete(file);
    }

    @Transactional(readOnly = true)
    public SubmissionDtos.FileDownloadUrlResponse getLearnerDownloadUrl(long assignmentId, long fileId, Long userId) {
        SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId)
                .orElseThrow(() -> new NotFoundException("Submission attempt not found for assignment: " + assignmentId));

        SubmissionFileEntity file = fileRepository.findByIdAndSubmissionId(fileId, submission.getId())
                .orElseThrow(() -> new NotFoundException("Submission file not found: " + fileId));

        Duration ttl = Duration.ofMinutes(30);
        String presigned = storageService.presignGet(file.getObjectKey(), ttl);
        return new SubmissionDtos.FileDownloadUrlResponse(URI.create(presigned), Instant.now().plus(ttl));
    }

    @Transactional(readOnly = true)
    public SubmissionDtos.FileDownloadUrlResponse getInstructorDownloadUrl(long submissionId, long fileId, Long ownerId) {
        SubmissionEntity submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found: " + submissionId));

        AssignmentEntity assignment = assignmentRepository.findById(submission.getAssignmentId())
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + submission.getAssignmentId()));

        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(assignment.getCourseId())
                .orElseThrow(() -> new NotFoundException("Course not found: " + assignment.getCourseId()));

        if (ownerId != null && !course.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("You do not own this course");
        }

        SubmissionFileEntity file = fileRepository.findByIdAndSubmissionId(fileId, submissionId)
                .orElseThrow(() -> new NotFoundException("Submission file not found: " + fileId));

        Duration ttl = Duration.ofMinutes(30);
        String presigned = storageService.presignGet(file.getObjectKey(), ttl);
        return new SubmissionDtos.FileDownloadUrlResponse(URI.create(presigned), Instant.now().plus(ttl));
    }

    private String sanitize(String filename) {
        if (filename == null) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

