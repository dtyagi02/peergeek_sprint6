package com.peergeek.report;

import com.peergeek.common.PageResponse;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instructor")
public class InstructorReportController {

	private final ReportService reportService;
	private final CurrentUserProvider currentUserProvider;

	public InstructorReportController(ReportService reportService, CurrentUserProvider currentUserProvider) {
		this.reportService = reportService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/courses/{courseId}/reports")
	public ResponseEntity<PageResponse<ReportDto>> getCourseReports(
			@PathVariable("courseId") long courseId,
			@RequestParam(value = "status", required = false) ReportStatus status,
			@RequestParam(value = "page", required = false, defaultValue = "0") int page,
			@RequestParam(value = "size", required = false, defaultValue = "20") int size) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		boolean isAdmin = user.role() == com.peergeek.user.UserRole.ADMIN;
		PageResponse<ReportDto> response = reportService.getCourseReportsForInstructor(
				courseId, user.id(), isAdmin, status, page, size);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(response);
	}

	@PutMapping("/reports/{reportId}/status")
	public ResponseEntity<ReportDto> updateReportStatus(
			@PathVariable("reportId") long reportId,
			@Valid @RequestBody UpdateReportStatusRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		boolean isAdmin = user.role() == com.peergeek.user.UserRole.ADMIN;
		ReportDto dto = reportService.updateReportStatus(reportId, user.id(), isAdmin, request);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(dto);
	}
}
