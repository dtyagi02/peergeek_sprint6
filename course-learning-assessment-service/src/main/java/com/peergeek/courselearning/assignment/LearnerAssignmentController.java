package com.peergeek.courselearning.assignment;

import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.security.CurrentUser;
import com.peergeek.courselearning.security.CurrentUserProvider;
import com.peergeek.courselearning.submission.LearnerSubmissionService;
import com.peergeek.courselearning.submission.SubmissionDtos;
import com.peergeek.courselearning.submission.SubmissionFileService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/learning")
public class LearnerAssignmentController {

    private final LearnerAssignmentService learnerAssignmentService;
    private final LearnerSubmissionService learnerSubmissionService;
    private final SubmissionFileService submissionFileService;
    private final CurrentUserProvider currentUserProvider;

    public LearnerAssignmentController(
            LearnerAssignmentService learnerAssignmentService,
            LearnerSubmissionService learnerSubmissionService,
            SubmissionFileService submissionFileService,
            CurrentUserProvider currentUserProvider) {
        this.learnerAssignmentService = learnerAssignmentService;
        this.learnerSubmissionService = learnerSubmissionService;
        this.submissionFileService = submissionFileService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/courses/{courseId}/assignments")
    public ResponseEntity<List<AssignmentDtos.LearnerAssignmentSummaryDto>> getAssignmentsForCourse(
            @PathVariable("courseId") long courseId,
            @RequestParam(value = "lessonId", required = false) Long lessonId,
            @RequestParam(value = "moduleId", required = false) Long moduleId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        List<AssignmentDtos.LearnerAssignmentSummaryDto> list =
                learnerAssignmentService.getAssignmentsForCourse(courseId, lessonId, moduleId, user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(list);
    }

    @GetMapping("/assignments")
    public ResponseEntity<PageResponse<AssignmentDtos.LearnerAssignmentSummaryDto>> getAssignmentsCrossCourse(
            @RequestParam(value = "filter", required = false) String filter,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<AssignmentDtos.LearnerAssignmentSummaryDto> response =
                learnerAssignmentService.getAssignmentsCrossCourse(filter, pageable, user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    @GetMapping("/assignments/{assignmentId}")
    public ResponseEntity<AssignmentDtos.LearnerAssignmentDetailDto> getAssignmentDetail(
            @PathVariable("assignmentId") long assignmentId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        AssignmentDtos.LearnerAssignmentDetailDto detail =
                learnerAssignmentService.getAssignmentDetail(assignmentId, user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(detail);
    }

    @PostMapping("/assignments/{assignmentId}/start")
    public ResponseEntity<AssignmentDtos.LearnerAttemptDto> startAttempt(
            @PathVariable("assignmentId") long assignmentId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        AssignmentDtos.LearnerAttemptDto attempt = learnerAssignmentService.startAttempt(assignmentId, user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(attempt);
    }

    @GetMapping("/assignments/{assignmentId}/attempt")
    public ResponseEntity<AssignmentDtos.LearnerAttemptDto> getAttempt(
            @PathVariable("assignmentId") long assignmentId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        AssignmentDtos.LearnerAttemptDto attempt = learnerAssignmentService.getAttempt(assignmentId, user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(attempt);
    }

    @PutMapping("/assignments/{assignmentId}/draft")
    public ResponseEntity<AssignmentDtos.DraftSaveResult> saveDraft(
            @PathVariable("assignmentId") long assignmentId,
            @RequestBody AssignmentDtos.DraftSubmissionRequest request) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        AssignmentDtos.DraftSaveResult result = learnerSubmissionService.saveDraft(assignmentId, request, user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result);
    }

    @PostMapping("/assignments/{assignmentId}/submit")
    public ResponseEntity<AssignmentDtos.LearnerSubmissionDetailDto> submit(
            @PathVariable("assignmentId") long assignmentId,
            @RequestBody AssignmentDtos.SubmitAssignmentRequest request) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        AssignmentDtos.LearnerSubmissionDetailDto result = learnerSubmissionService.submit(assignmentId, request, user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result);
    }

    @GetMapping("/assignments/{assignmentId}/submission")
    public ResponseEntity<AssignmentDtos.LearnerSubmissionDetailDto> getSubmission(
            @PathVariable("assignmentId") long assignmentId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        AssignmentDtos.LearnerSubmissionDetailDto result = learnerSubmissionService.getLearnerSubmission(assignmentId, user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result);
    }

    @PostMapping("/assignments/{assignmentId}/files/uploads")
    public ResponseEntity<SubmissionDtos.FileUploadResponse> initiateUpload(
            @PathVariable("assignmentId") long assignmentId,
            @RequestBody SubmissionDtos.FileUploadRequest request) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        SubmissionDtos.FileUploadResponse response = submissionFileService.initiateUpload(assignmentId, request, user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    @PostMapping("/assignments/{assignmentId}/files/{fileId}/confirm")
    public ResponseEntity<SubmissionDtos.FileConfirmResponse> confirmUpload(
            @PathVariable("assignmentId") long assignmentId,
            @PathVariable("fileId") long fileId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        SubmissionDtos.FileConfirmResponse response = submissionFileService.confirmUpload(assignmentId, fileId, user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    @DeleteMapping("/assignments/{assignmentId}/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable("assignmentId") long assignmentId,
            @PathVariable("fileId") long fileId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        submissionFileService.deleteFile(assignmentId, fileId, user.id());
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @GetMapping("/assignments/{assignmentId}/files/{fileId}/download-url")
    public ResponseEntity<SubmissionDtos.FileDownloadUrlResponse> getLearnerDownloadUrl(
            @PathVariable("assignmentId") long assignmentId,
            @PathVariable("fileId") long fileId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        SubmissionDtos.FileDownloadUrlResponse response = submissionFileService.getLearnerDownloadUrl(assignmentId, fileId, user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }
}

