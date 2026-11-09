package com.peergeek.assignment;

import java.util.List;
import com.peergeek.assignment.dto.DraftSaveResult;
import com.peergeek.assignment.dto.DraftSubmissionRequest;
import com.peergeek.assignment.dto.LearnerAssignmentDetailDto;
import com.peergeek.assignment.dto.LearnerAssignmentSummaryDto;
import com.peergeek.assignment.dto.LearnerAttemptDto;
import com.peergeek.assignment.dto.LearnerSubmissionDetailDto;
import com.peergeek.assignment.dto.SubmitAssignmentRequest;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import com.peergeek.common.PageResponse;
import com.peergeek.common.Pagination;
import com.peergeek.submission.LearnerSubmissionService;
import com.peergeek.submission.SubmissionFileService;
import com.peergeek.submission.dto.FileConfirmResponse;
import com.peergeek.submission.dto.FileDownloadUrlResponse;
import com.peergeek.submission.dto.FileUploadRequest;
import com.peergeek.submission.dto.FileUploadResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
	public ResponseEntity<List<LearnerAssignmentSummaryDto>> getAssignmentsForCourse(
			@PathVariable("courseId") long courseId,
			@RequestParam(required = false) Long lessonId,
			@RequestParam(required = false) Long moduleId) {
		List<LearnerAssignmentSummaryDto> list = learnerAssignmentService.getAssignmentsForCourse(courseId, lessonId, moduleId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(list);
	}

	@GetMapping("/assignments")
	public ResponseEntity<PageResponse<LearnerAssignmentSummaryDto>> getAssignmentsCrossCourse(
			@RequestParam(required = false) String filter,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Pageable pageable = Pagination.pageable(page, size, null);
		PageResponse<LearnerAssignmentSummaryDto> response =
				learnerAssignmentService.getAssignmentsCrossCourse(filter, pageable);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(response);
	}

	@GetMapping("/assignments/{assignmentId}")
	public ResponseEntity<LearnerAssignmentDetailDto> getAssignmentDetail(
			@PathVariable("assignmentId") long assignmentId) {
		LearnerAssignmentDetailDto detail = learnerAssignmentService.getAssignmentDetail(assignmentId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(detail);
	}

	@PostMapping("/assignments/{assignmentId}/start")
	public ResponseEntity<LearnerAttemptDto> startAttempt(
			@PathVariable("assignmentId") long assignmentId) {
		LearnerAttemptDto attempt = learnerAssignmentService.startAttempt(assignmentId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(attempt);
	}

	@GetMapping("/assignments/{assignmentId}/attempt")
	public ResponseEntity<LearnerAttemptDto> getAttempt(
			@PathVariable("assignmentId") long assignmentId) {
		LearnerAttemptDto attempt = learnerAssignmentService.getAttempt(assignmentId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(attempt);
	}

	@PutMapping("/assignments/{assignmentId}/draft")
	public ResponseEntity<DraftSaveResult> saveDraft(
			@PathVariable("assignmentId") long assignmentId,
			@RequestBody DraftSubmissionRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		DraftSaveResult result = learnerSubmissionService.saveDraft(assignmentId, request, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(result);
	}

	@PostMapping("/assignments/{assignmentId}/submit")
	public ResponseEntity<LearnerSubmissionDetailDto> submit(
			@PathVariable("assignmentId") long assignmentId,
			@RequestBody SubmitAssignmentRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		LearnerSubmissionDetailDto result = learnerSubmissionService.submit(assignmentId, request, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(result);
	}

	@GetMapping("/assignments/{assignmentId}/submission")
	public ResponseEntity<LearnerSubmissionDetailDto> getSubmission(
			@PathVariable("assignmentId") long assignmentId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		LearnerSubmissionDetailDto result = learnerSubmissionService.getLearnerSubmission(assignmentId, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(result);
	}

	@PostMapping("/assignments/{assignmentId}/files/uploads")
	public ResponseEntity<FileUploadResponse> initiateUpload(
			@PathVariable("assignmentId") long assignmentId,
			@RequestBody FileUploadRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		FileUploadResponse response = submissionFileService.initiateUpload(assignmentId, request, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(response);
	}

	@PostMapping("/assignments/{assignmentId}/files/{fileId}/confirm")
	public ResponseEntity<FileConfirmResponse> confirmUpload(
			@PathVariable("assignmentId") long assignmentId,
			@PathVariable("fileId") long fileId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		FileConfirmResponse response = submissionFileService.confirmUpload(assignmentId, fileId, user.id());
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
	public ResponseEntity<FileDownloadUrlResponse> getLearnerDownloadUrl(
			@PathVariable("assignmentId") long assignmentId,
			@PathVariable("fileId") long fileId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		FileDownloadUrlResponse response = submissionFileService.getLearnerDownloadUrl(assignmentId, fileId, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(response);
	}
}
