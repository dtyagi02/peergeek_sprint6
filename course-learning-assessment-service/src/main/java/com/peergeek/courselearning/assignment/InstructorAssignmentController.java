package com.peergeek.courselearning.assignment;

import com.peergeek.courselearning.common.AssignmentStatus;
import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.common.SubmissionStatus;
import com.peergeek.courselearning.security.CurrentUserProvider;
import com.peergeek.courselearning.submission.InstructorGradingService;
import com.peergeek.courselearning.submission.SubmissionDtos;
import com.peergeek.courselearning.submission.SubmissionFileService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/instructor")
public class InstructorAssignmentController {

    private final InstructorAssignmentService assignmentService;
    private final InstructorGradingService gradingService;
    private final SubmissionFileService submissionFileService;
    private final CurrentUserProvider currentUserProvider;

    public InstructorAssignmentController(
            InstructorAssignmentService assignmentService,
            InstructorGradingService gradingService,
            SubmissionFileService submissionFileService,
            CurrentUserProvider currentUserProvider) {
        this.assignmentService = assignmentService;
        this.gradingService = gradingService;
        this.submissionFileService = submissionFileService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/courses/{courseId}/assignments")
    public ResponseEntity<AssignmentDtos.InstructorAssignmentDetailDto> createAssignment(
            @PathVariable("courseId") long courseId,
            @RequestBody AssignmentDtos.CreateAssignmentRequest request) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentService.createAssignment(courseId, ownerId, request));
    }

    @GetMapping("/courses/{courseId}/assignments")
    public ResponseEntity<PageResponse<AssignmentDtos.InstructorAssignmentSummaryDto>> listAssignments(
            @PathVariable("courseId") long courseId,
            @RequestParam(value = "status", required = false) AssignmentStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(assignmentService.listAssignments(courseId, ownerId, status, pageable));
    }

    @GetMapping("/assignments/{assignmentId}")
    public ResponseEntity<AssignmentDtos.InstructorAssignmentDetailDto> getAssignment(
            @PathVariable("assignmentId") long assignmentId) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(assignmentService.getAssignment(assignmentId, ownerId));
    }

    @PatchMapping("/assignments/{assignmentId}")
    public ResponseEntity<AssignmentDtos.InstructorAssignmentDetailDto> updateAssignment(
            @PathVariable("assignmentId") long assignmentId,
            @RequestBody AssignmentDtos.UpdateAssignmentRequest request) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(assignmentService.updateAssignment(assignmentId, ownerId, request));
    }

    @PutMapping("/assignments/{assignmentId}/questions")
    public ResponseEntity<AssignmentDtos.InstructorAssignmentDetailDto> replaceQuestions(
            @PathVariable("assignmentId") long assignmentId,
            @RequestBody List<AssignmentDtos.QuestionUpsertRequest> questions) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(assignmentService.replaceQuestions(assignmentId, ownerId, questions));
    }

    @PostMapping("/assignments/{assignmentId}/publish")
    public ResponseEntity<AssignmentDtos.InstructorAssignmentDetailDto> publishAssignment(
            @PathVariable("assignmentId") long assignmentId) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(assignmentService.publishAssignment(assignmentId, ownerId));
    }

    @PostMapping("/assignments/{assignmentId}/unpublish")
    public ResponseEntity<AssignmentDtos.InstructorAssignmentDetailDto> unpublishAssignment(
            @PathVariable("assignmentId") long assignmentId) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(assignmentService.unpublishAssignment(assignmentId, ownerId));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAssignment(@PathVariable("assignmentId") long assignmentId) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        assignmentService.deleteAssignment(assignmentId, ownerId);
    }

    @GetMapping("/courses/{courseId}/submissions")
    public ResponseEntity<PageResponse<SubmissionDtos.InstructorSubmissionSummaryDto>> listSubmissions(
            @PathVariable("courseId") long courseId,
            @RequestParam(value = "assignmentId", required = false) Long assignmentId,
            @RequestParam(value = "status", required = false) SubmissionStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "submittedAt,asc") String sort) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(gradingService.listSubmissions(courseId, assignmentId, status, page, size, sort, ownerId));
    }

    @GetMapping("/submissions/{submissionId}")
    public ResponseEntity<SubmissionDtos.InstructorSubmissionDetailDto> getSubmission(
            @PathVariable("submissionId") long submissionId) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(gradingService.getSubmission(submissionId, ownerId));
    }

    @PutMapping("/submissions/{submissionId}/grade")
    public ResponseEntity<SubmissionDtos.InstructorSubmissionDetailDto> gradeSubmission(
            @PathVariable("submissionId") long submissionId,
            @RequestBody SubmissionDtos.GradeSubmissionRequest request) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(gradingService.gradeSubmission(submissionId, request, ownerId));
    }

    @GetMapping("/submissions/{submissionId}/files/{fileId}/download-url")
    public ResponseEntity<SubmissionDtos.FileDownloadUrlResponse> getInstructorDownloadUrl(
            @PathVariable("submissionId") long submissionId,
            @PathVariable("fileId") long fileId) {
        Long ownerId = currentUserProvider.requireCurrentUser().id();
        return ResponseEntity.ok(submissionFileService.getInstructorDownloadUrl(submissionId, fileId, ownerId));
    }
}

