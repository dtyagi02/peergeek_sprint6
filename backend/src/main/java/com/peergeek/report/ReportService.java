package com.peergeek.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.peergeek.common.PageResponse;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.exception.ForbiddenException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.user.PublicProfileDto;
import com.peergeek.user.UserService;
import com.peergeek.audit.AuditAction;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ReportService {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReportService.class);

	private final ReportRepository reportRepository;
	private final CourseStructureQueryService courseStructureQueryService;
	private final UserService userService;
	private final ApplicationEventPublisher eventPublisher;
	private final AuditService auditService;

	public ReportService(
			ReportRepository reportRepository,
			CourseStructureQueryService courseStructureQueryService,
			UserService userService,
			ApplicationEventPublisher eventPublisher,
			AuditService auditService) {
		this.reportRepository = reportRepository;
		this.courseStructureQueryService = courseStructureQueryService;
		this.userService = userService;
		this.eventPublisher = eventPublisher;
		this.auditService = auditService;
	}

	@Transactional
	public ReportDto createReport(long courseId, long reporterUserId, CreateReportRequest request) {
		log.info("Attempting to create course issue report: courseId={}, reporterUserId={}, category={}",
				courseId, reporterUserId, request.category());

		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId)
				.orElseThrow(() -> {
					log.warn("Report creation failed: Course {} not found", courseId);
					return new NotFoundException("Course not found");
				});

		if (metadata.deleted()) {
			log.warn("Report creation failed: Course {} has been deleted", courseId);
			throw new NotFoundException("Course not found");
		}

		ReportEntity report = new ReportEntity(
				courseId,
				reporterUserId,
				request.category(),
				request.details());

		report = reportRepository.save(report);
		log.info("Course issue report created successfully: reportId={}, courseId={}, reporterUserId={}, category={}, status={}",
				report.getId(), courseId, reporterUserId, report.getCategory(), report.getStatus());

		ReportCreatedEvent event = new ReportCreatedEvent(
				report.getId(),
				courseId,
				reporterUserId,
				metadata.ownerId(),
				report.getCategory(),
				report.getDetails(),
				report.getCreatedAt());

		publishAfterCommit(event);

		auditService.record(new AuditEvent(
				reporterUserId,
				AuditAction.REPORT_CREATED,
				"REPORT",
				String.valueOf(report.getId()),
				Map.of(
						"courseId", courseId,
						"category", request.category().name(),
						"details", request.details())));

		PublicProfileDto profile = userService.getPublicProfiles(List.of(reporterUserId)).get(reporterUserId);
		String reporterName = profile != null ? profile.displayName() : "User";

		return toDto(report, reporterName);
	}

	@Transactional(readOnly = true)
	public PageResponse<ReportDto> getCourseReportsForInstructor(
			long courseId, long actorUserId, boolean isAdmin, ReportStatus statusFilter, int page, int size) {
		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("Course not found"));

		if (!isAdmin && !metadata.ownerId().equals(actorUserId)) {
			throw new ForbiddenException("Only the course instructor or an administrator can view course reports");
		}

		int safePage = Math.max(0, page);
		int safeSize = Math.clamp(size, 1, 50);

		PageRequest pageRequest = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<ReportEntity> pageResult = statusFilter != null
				? reportRepository.findByCourseIdAndStatus(courseId, statusFilter, pageRequest)
				: reportRepository.findByCourseId(courseId, pageRequest);

		Set<Long> userIds = pageResult.getContent().stream()
				.map(ReportEntity::getReporterUserId)
				.collect(Collectors.toSet());

		Map<Long, PublicProfileDto> profiles = userService.getPublicProfiles(userIds);

		return PageResponse.from(pageResult.map(report -> {
			PublicProfileDto profile = profiles.get(report.getReporterUserId());
			String reporterName = profile != null ? profile.displayName() : "User";
			return toDto(report, reporterName);
		}));
	}

	@Transactional
	public ReportDto updateReportStatus(
			long reportId, long actorUserId, boolean isAdmin, UpdateReportStatusRequest request) {
		ReportEntity report = reportRepository.findById(reportId)
				.orElseThrow(() -> new NotFoundException("Report not found"));

		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(report.getCourseId())
				.orElseThrow(() -> new NotFoundException("Course not found"));

		if (!isAdmin && !metadata.ownerId().equals(actorUserId)) {
			throw new ForbiddenException("Only the course instructor or an administrator can resolve course reports");
		}

		report.setStatus(request.status());
		report.setResolutionNotes(request.resolutionNotes());
		report.setResolvedBy(actorUserId);
		report.setResolvedAt(Instant.now());

		report = reportRepository.save(report);

		auditService.record(new AuditEvent(
				actorUserId,
				AuditAction.REPORT_STATUS_UPDATED,
				"REPORT",
				String.valueOf(report.getId()),
				Map.of(
						"courseId", report.getCourseId(),
						"status", request.status().name(),
						"resolutionNotes", request.resolutionNotes() != null ? request.resolutionNotes() : "")));

		PublicProfileDto profile = userService.getPublicProfiles(List.of(report.getReporterUserId())).get(report.getReporterUserId());
		String reporterName = profile != null ? profile.displayName() : "User";

		return toDto(report, reporterName);
	}

	private ReportDto toDto(ReportEntity report, String reporterName) {
		return new ReportDto(
				report.getId(),
				report.getCourseId(),
				report.getReporterUserId(),
				reporterName,
				report.getCategory(),
				report.getDetails(),
				report.getStatus(),
				report.getResolutionNotes(),
				report.getResolvedBy(),
				report.getResolvedAt(),
				report.getCreatedAt());
	}

	private void publishAfterCommit(Object event) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					eventPublisher.publishEvent(event);
				}
			});
		} else {
			eventPublisher.publishEvent(event);
		}
	}
}
