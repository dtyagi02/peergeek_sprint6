package com.peergeek.assignment;

import java.util.List;
import com.peergeek.assignment.dto.CreateAssignmentRequest;
import com.peergeek.assignment.dto.InstructorAssignmentDetailDto;
import com.peergeek.assignment.dto.InstructorAssignmentSummaryDto;
import com.peergeek.assignment.dto.QuestionUpsertRequest;
import com.peergeek.assignment.dto.UpdateAssignmentRequest;
import com.peergeek.common.PageResponse;
import com.peergeek.common.Pagination;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import com.peergeek.submission.InstructorGradingService;
import com.peergeek.submission.SubmissionFileService;
import com.peergeek.submission.SubmissionStatus;
import com.peergeek.submission.dto.FileDownloadUrlResponse;
import com.peergeek.submission.dto.GradeSubmissionRequest;
import com.peergeek.submission.dto.InstructorSubmissionDetailDto;
import com.peergeek.submission.dto.InstructorSubmissionSummaryDto;

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
	public ResponseEntity<InstructorAssignmentDetailDto> createAssignment(
			@PathVariable long courseId,
			@RequestBody CreateAssignmentRequest request) {
		InstructorAssignmentDetailDto created = assignmentService.createAssignment(courseId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@GetMapping("/courses/{courseId}/assignments")
	public PageResponse<InstructorAssignmentSummaryDto> listAssignments(
			@PathVariable long courseId,
			@RequestParam(required = false) AssignmentStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt,desc") String sort) {
		Pageable pageable = Pagination.pageable(page, size, sort);
		return assignmentService.listAssignments(courseId, status, pageable);
	}

	@GetMapping("/assignments/{assignmentId}")
	public InstructorAssignmentDetailDto getAssignment(@PathVariable long assignmentId) {
		return assignmentService.getAssignment(assignmentId);
	}

	@PatchMapping("/assignments/{assignmentId}")
	public InstructorAssignmentDetailDto updateAssignment(
			@PathVariable long assignmentId,
			@RequestBody UpdateAssignmentRequest request) {
		return assignmentService.updateAssignment(assignmentId, request);
	}

	@PutMapping("/assignments/{assignmentId}/questions")
	public InstructorAssignmentDetailDto replaceQuestions(
			@PathVariable long assignmentId,
			@RequestBody List<QuestionUpsertRequest> questions) {
		return assignmentService.replaceQuestions(assignmentId, questions);
	}

	@PostMapping("/assignments/{assignmentId}/publish")
	public InstructorAssignmentDetailDto publishAssignment(@PathVariable long assignmentId) {
		return assignmentService.publishAssignment(assignmentId);
	}

	@PostMapping("/assignments/{assignmentId}/unpublish")
	public InstructorAssignmentDetailDto unpublishAssignment(@PathVariable long assignmentId) {
		return assignmentService.unpublishAssignment(assignmentId);
	}

	@DeleteMapping("/assignments/{assignmentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteAssignment(@PathVariable long assignmentId) {
		assignmentService.deleteAssignment(assignmentId);
	}

	@GetMapping("/courses/{courseId}/submissions")
	public PageResponse<InstructorSubmissionSummaryDto> listSubmissions(
			@PathVariable long courseId,
			@RequestParam(required = false) Long assignmentId,
			@RequestParam(required = false) SubmissionStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "submittedAt,asc") String sort) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		return gradingService.listSubmissions(courseId, assignmentId, status, page, size, sort, user.id());
	}

	@GetMapping("/submissions/{submissionId}")
	public InstructorSubmissionDetailDto getSubmission(@PathVariable long submissionId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		return gradingService.getSubmission(submissionId, user.id());
	}

	@PutMapping("/submissions/{submissionId}/grade")
	public InstructorSubmissionDetailDto gradeSubmission(
			@PathVariable long submissionId,
			@RequestBody GradeSubmissionRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		return gradingService.gradeSubmission(submissionId, request, user.id());
	}

	@GetMapping("/submissions/{submissionId}/files/{fileId}/download-url")
	public FileDownloadUrlResponse getInstructorDownloadUrl(
			@PathVariable long submissionId,
			@PathVariable long fileId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		return submissionFileService.getInstructorDownloadUrl(submissionId, fileId, user.id());
	}
}
