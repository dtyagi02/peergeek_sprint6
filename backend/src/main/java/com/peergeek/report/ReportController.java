package com.peergeek.report;

import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/reports")
public class ReportController {

	private final ReportService reportService;
	private final CurrentUserProvider currentUserProvider;

	public ReportController(ReportService reportService, CurrentUserProvider currentUserProvider) {
		this.reportService = reportService;
		this.currentUserProvider = currentUserProvider;
	}

	@PostMapping
	public ResponseEntity<ReportDto> createReport(
			@PathVariable("courseId") long courseId,
			@Valid @RequestBody CreateReportRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		ReportDto dto = reportService.createReport(courseId, user.id(), request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(dto);
	}
}
