package com.peergeek.courselearning.report;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.course.CourseStructureQueryService;
import com.peergeek.courselearning.course.CourseStructureQueryService.CourseMetadataSnapshot;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.profile.LearningUserProfileEntity;
import com.peergeek.courselearning.profile.LearningUserProfileRepository;
import com.peergeek.courselearning.audit.AuditAction;
import com.peergeek.courselearning.audit.AuditEvent;
import com.peergeek.courselearning.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;
    private final CourseStructureQueryService courseStructureQueryService;
    private final LearningUserProfileRepository userProfileRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;

    public ReportService(
            ReportRepository reportRepository,
            CourseStructureQueryService courseStructureQueryService,
            LearningUserProfileRepository userProfileRepository,
            ApplicationEventPublisher eventPublisher,
            AuditService auditService) {
        this.reportRepository = reportRepository;
        this.courseStructureQueryService = courseStructureQueryService;
        this.userProfileRepository = userProfileRepository;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
    }

    @Transactional
    public ReportDto createReport(long courseId, long reporterUserId, CreateReportRequest request) {
        CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found"));

        if (metadata.deleted()) {
            throw new NotFoundException("Course not found");
        }

        ReportEntity report = new ReportEntity(
                courseId,
                reporterUserId,
                request.category(),
                request.details());

        report = reportRepository.save(report);

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

        LearningUserProfileEntity profile = userProfileRepository.findById(reporterUserId).orElse(null);
        String reporterName = profile != null ? profile.getDisplayName() : "User";

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
                null,
                report.getResolvedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportDto> getCourseReportsForInstructor(
            long courseId, long actorUserId, boolean isAdmin, ReportStatus status, int page, int size) {
        CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found"));

        if (!isAdmin && !metadata.ownerId().equals(actorUserId)) {
            throw new ForbiddenException("You are not authorized to view reports for this course");
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 50);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ReportEntity> reportPage = status != null
                ? reportRepository.findByCourseIdAndStatus(courseId, status, pageRequest)
                : reportRepository.findByCourseId(courseId, pageRequest);

        Set<Long> userIds = new HashSet<>();
        reportPage.getContent().forEach(r -> {
            userIds.add(r.getReporterUserId());
            if (r.getResolvedBy() != null) userIds.add(r.getResolvedBy());
        });

        Map<Long, LearningUserProfileEntity> userMap = userProfileRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(LearningUserProfileEntity::getId, u -> u));

        List<ReportDto> dtoList = reportPage.getContent().stream().map(r -> {
            LearningUserProfileEntity reporter = userMap.get(r.getReporterUserId());
            LearningUserProfileEntity resolver = r.getResolvedBy() != null ? userMap.get(r.getResolvedBy()) : null;
            return new ReportDto(
                    r.getId(),
                    r.getCourseId(),
                    r.getReporterUserId(),
                    reporter != null ? reporter.getDisplayName() : "User",
                    r.getCategory(),
                    r.getDetails(),
                    r.getStatus(),
                    r.getResolutionNotes(),
                    r.getResolvedBy(),
                    resolver != null ? resolver.getDisplayName() : null,
                    r.getResolvedAt(),
                    r.getCreatedAt(),
                    r.getUpdatedAt());
        }).toList();

        return new PageResponse<>(
                dtoList,
                reportPage.getNumber(),
                reportPage.getSize(),
                reportPage.getTotalElements(),
                reportPage.getTotalPages());
    }

    @Transactional
    public ReportDto updateReportStatus(long reportId, long actorUserId, boolean isAdmin, UpdateReportStatusRequest request) {
        ReportEntity report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found"));

        CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(report.getCourseId())
                .orElseThrow(() -> new NotFoundException("Course not found"));

        if (!isAdmin && !metadata.ownerId().equals(actorUserId)) {
            throw new ForbiddenException("You are not authorized to update this report");
        }

        report.setStatus(request.status());
        report.setResolutionNotes(request.resolutionNotes());
        if (request.status() == ReportStatus.RESOLVED || request.status() == ReportStatus.DISMISSED) {
            report.setResolvedBy(actorUserId);
            report.setResolvedAt(Instant.now());
        }

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

        LearningUserProfileEntity reporter = userProfileRepository.findById(report.getReporterUserId()).orElse(null);
        LearningUserProfileEntity resolver = userProfileRepository.findById(actorUserId).orElse(null);

        return new ReportDto(
                report.getId(),
                report.getCourseId(),
                report.getReporterUserId(),
                reporter != null ? reporter.getDisplayName() : "User",
                report.getCategory(),
                report.getDetails(),
                report.getStatus(),
                report.getResolutionNotes(),
                report.getResolvedBy(),
                resolver != null ? resolver.getDisplayName() : null,
                report.getResolvedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt());
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
