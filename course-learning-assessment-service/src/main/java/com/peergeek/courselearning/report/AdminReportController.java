package com.peergeek.courselearning.report;

import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.profile.LearningUserProfileEntity;
import com.peergeek.courselearning.profile.LearningUserProfileRepository;
import com.peergeek.courselearning.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final ReportRepository reportRepository;
    private final CourseRepository courseRepository;
    private final LearningUserProfileRepository userProfileRepository;
    private final ReportService reportService;
    private final CurrentUserProvider currentUserProvider;

    public AdminReportController(
            ReportRepository reportRepository,
            CourseRepository courseRepository,
            LearningUserProfileRepository userProfileRepository,
            ReportService reportService,
            CurrentUserProvider currentUserProvider) {
        this.reportRepository = reportRepository;
        this.courseRepository = courseRepository;
        this.userProfileRepository = userProfileRepository;
        this.reportService = reportService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AdminReportDto>> getReports(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        String safeQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;

        List<ReportStatus> statuses = null;
        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            if (status.equalsIgnoreCase("OPEN")) {
                statuses = List.of(ReportStatus.PENDING, ReportStatus.IN_REVIEW);
            } else {
                try {
                    statuses = List.of(ReportStatus.valueOf(status.trim().toUpperCase()));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        boolean useStatuses = statuses != null && !statuses.isEmpty();
        Collection<ReportStatus> queryStatuses = useStatuses ? statuses : List.of(ReportStatus.PENDING);

        Page<ReportEntity> reportPage = reportRepository.findAdminReports(useStatuses, queryStatuses, safeQuery, pageable);

        Set<Long> courseIds = reportPage.getContent().stream().map(ReportEntity::getCourseId).collect(Collectors.toSet());
        Set<Long> userIds = new HashSet<>();
        reportPage.getContent().forEach(r -> {
            userIds.add(r.getReporterUserId());
            if (r.getResolvedBy() != null) userIds.add(r.getResolvedBy());
        });

        Map<Long, CourseEntity> courseMap = courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(CourseEntity::getId, c -> c));
        Map<Long, LearningUserProfileEntity> userMap = userProfileRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(LearningUserProfileEntity::getId, u -> u));

        List<AdminReportDto> dtoList = reportPage.getContent().stream().map(r -> {
            CourseEntity course = courseMap.get(r.getCourseId());
            LearningUserProfileEntity reporter = userMap.get(r.getReporterUserId());
            LearningUserProfileEntity resolver = r.getResolvedBy() != null ? userMap.get(r.getResolvedBy()) : null;
            return new AdminReportDto(
                    r.getId(),
                    r.getCourseId(),
                    course != null ? course.getTitle() : "Course #" + r.getCourseId(),
                    r.getReporterUserId(),
                    reporter != null ? reporter.getDisplayName() : "User #" + r.getReporterUserId(),
                    reporter != null ? reporter.getEmail() : null,
                    r.getCategory(),
                    r.getDetails(),
                    r.getStatus(),
                    r.getResolutionNotes(),
                    r.getResolvedBy(),
                    resolver != null ? resolver.getDisplayName() : null,
                    r.getResolvedAt(),
                    r.getCreatedAt());
        }).toList();

        return ResponseEntity.ok(new PageResponse<>(
                dtoList,
                reportPage.getNumber(),
                reportPage.getSize(),
                reportPage.getTotalElements(),
                reportPage.getTotalPages()));
    }

    @PatchMapping("/{reportId}")
    public ResponseEntity<AdminReportDto> updateReportPatch(
            @PathVariable("reportId") Long reportId,
            @Valid @RequestBody UpdateReportStatusRequest request) {
        long adminUserId = currentUserProvider.requireCurrentUser().id();
        ReportDto updated = reportService.updateReportStatus(reportId, adminUserId, true, request);
        return ResponseEntity.ok(toAdminDto(updated));
    }

    @PutMapping("/{reportId}/status")
    public ResponseEntity<AdminReportDto> updateReportPut(
            @PathVariable("reportId") Long reportId,
            @Valid @RequestBody UpdateReportStatusRequest request) {
        return updateReportPatch(reportId, request);
    }

    private AdminReportDto toAdminDto(ReportDto dto) {
        CourseEntity course = courseRepository.findById(dto.courseId()).orElse(null);
        LearningUserProfileEntity reporter = userProfileRepository.findById(dto.reporterUserId()).orElse(null);
        return new AdminReportDto(
                dto.id(),
                dto.courseId(),
                course != null ? course.getTitle() : "Course #" + dto.courseId(),
                dto.reporterUserId(),
                dto.reporterName(),
                reporter != null ? reporter.getEmail() : null,
                dto.category(),
                dto.details(),
                dto.status(),
                dto.resolutionNotes(),
                dto.resolvedBy(),
                dto.resolverName(),
                dto.resolvedAt(),
                dto.createdAt());
    }
}
