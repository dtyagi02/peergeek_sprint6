package com.peergeek.admin;

import com.peergeek.admin.dto.AdminActivityDto;
import com.peergeek.admin.dto.AdminCourseDto;
import com.peergeek.admin.dto.AdminDashboardSummaryDto;
import com.peergeek.admin.dto.AdminReportDto;
import com.peergeek.admin.dto.AdminUserDto;
import com.peergeek.admin.dto.UpdateAdminReportRequest;
import com.peergeek.admin.dto.UpdateCourseStatusRequest;
import com.peergeek.admin.dto.UpdateUserStatusRequest;
import com.peergeek.common.PageResponse;
import com.peergeek.course.CourseStatus;
import com.peergeek.report.ReportStatus;
import com.peergeek.security.CurrentUserProvider;
import com.peergeek.user.UserMode;
import com.peergeek.user.UserRole;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

	private final AdminDashboardService adminDashboardService;
	private final CurrentUserProvider currentUserProvider;

	public AdminDashboardController(
			AdminDashboardService adminDashboardService,
			CurrentUserProvider currentUserProvider) {
		this.adminDashboardService = adminDashboardService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/dashboard/summary")
	public ResponseEntity<AdminDashboardSummaryDto> getSummary() {
		return ResponseEntity.ok(adminDashboardService.getSummary());
	}

	@GetMapping("/dashboard/activities")
	public ResponseEntity<PageResponse<AdminActivityDto>> getActivities(
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size) {
		return ResponseEntity.ok(adminDashboardService.getActivities(page, size));
	}

	@GetMapping("/reports")
	public ResponseEntity<PageResponse<AdminReportDto>> getReports(
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "query", required = false) String query,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size) {
		return ResponseEntity.ok(adminDashboardService.getReports(status, query, page, size));
	}

	@PatchMapping("/reports/{reportId}")
	public ResponseEntity<AdminReportDto> updateReportPatch(
			@PathVariable("reportId") Long reportId,
			@Valid @RequestBody UpdateAdminReportRequest request) {
		long adminUserId = currentUserProvider.requireCurrentUser().id();
		return ResponseEntity.ok(adminDashboardService.updateReport(reportId, request, adminUserId));
	}

	@PutMapping("/reports/{reportId}/status")
	public ResponseEntity<AdminReportDto> updateReportPut(
			@PathVariable("reportId") Long reportId,
			@Valid @RequestBody UpdateAdminReportRequest request) {
		long adminUserId = currentUserProvider.requireCurrentUser().id();
		return ResponseEntity.ok(adminDashboardService.updateReport(reportId, request, adminUserId));
	}

	@GetMapping("/users")
	public ResponseEntity<PageResponse<AdminUserDto>> getUsers(
			@RequestParam(value = "role", required = false) UserRole role,
			@RequestParam(value = "mode", required = false) UserMode mode,
			@RequestParam(value = "enabled", required = false) Boolean enabled,
			@RequestParam(value = "query", required = false) String query,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size) {
		return ResponseEntity.ok(adminDashboardService.getUsers(role, mode, enabled, query, page, size));
	}

	@PatchMapping("/users/{userId}/status")
	public ResponseEntity<AdminUserDto> updateUserStatus(
			@PathVariable("userId") Long userId,
			@Valid @RequestBody UpdateUserStatusRequest request) {
		long adminUserId = currentUserProvider.requireCurrentUser().id();
		return ResponseEntity.ok(adminDashboardService.updateUserStatus(userId, request, adminUserId));
	}

	@GetMapping("/courses")
	public ResponseEntity<PageResponse<AdminCourseDto>> getCourses(
			@RequestParam(value = "status", required = false) CourseStatus status,
			@RequestParam(value = "query", required = false) String query,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size) {
		return ResponseEntity.ok(adminDashboardService.getCourses(status, query, page, size));
	}

	@GetMapping("/courses/{courseId}")
	public ResponseEntity<AdminCourseDto> getCourse(
			@PathVariable("courseId") Long courseId) {
		return ResponseEntity.ok(adminDashboardService.getCourseById(courseId));
	}

	@PatchMapping("/courses/{courseId}/status")
	public ResponseEntity<AdminCourseDto> updateCourseStatusPatch(
			@PathVariable("courseId") Long courseId,
			@Valid @RequestBody UpdateCourseStatusRequest request) {
		long adminUserId = currentUserProvider.requireCurrentUser().id();
		return ResponseEntity.ok(adminDashboardService.updateCourseStatus(courseId, request, adminUserId));
	}

	@PutMapping("/courses/{courseId}/status")
	public ResponseEntity<AdminCourseDto> updateCourseStatusPut(
			@PathVariable("courseId") Long courseId,
			@Valid @RequestBody UpdateCourseStatusRequest request) {
		long adminUserId = currentUserProvider.requireCurrentUser().id();
		return ResponseEntity.ok(adminDashboardService.updateCourseStatus(courseId, request, adminUserId));
	}
}
