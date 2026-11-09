package com.peergeek.courselearning.admin;

import com.peergeek.courselearning.audit.AuditLogEntity;
import com.peergeek.courselearning.audit.AuditLogRepository;
import com.peergeek.courselearning.common.CourseStatus;
import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.common.UserMode;
import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.profile.LearningUserProfileEntity;
import com.peergeek.courselearning.profile.LearningUserProfileRepository;
import com.peergeek.courselearning.report.ReportRepository;
import com.peergeek.courselearning.report.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {

    private final CourseRepository courseRepository;
    private final ReportRepository reportRepository;
    private final AuditLogRepository auditLogRepository;
    private final LearningUserProfileRepository userProfileRepository;

    public AdminDashboardService(
            CourseRepository courseRepository,
            ReportRepository reportRepository,
            AuditLogRepository auditLogRepository,
            LearningUserProfileRepository userProfileRepository) {
        this.courseRepository = courseRepository;
        this.reportRepository = reportRepository;
        this.auditLogRepository = auditLogRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardSummaryDto getSummary() {
        long totalUsers = userProfileRepository.count();
        long totalStudents = userProfileRepository.findAll().stream().filter(u -> u.getMode() == UserMode.STUDENT).count();
        long totalInstructors = userProfileRepository.findAll().stream().filter(u -> u.getMode() == UserMode.INSTRUCTOR).count();
        long totalCourses = courseRepository.count();
        long publishedCourses = courseRepository.countByStatusAndDeletedAtIsNull(CourseStatus.PUBLISHED);
        long openReportsCount = reportRepository.countByStatusIn(List.of(ReportStatus.PENDING, ReportStatus.IN_REVIEW));
        long totalActivitiesCount = auditLogRepository.count();

        return new AdminDashboardSummaryDto(
                totalUsers,
                totalStudents,
                totalInstructors,
                totalCourses,
                publishedCourses,
                openReportsCount,
                totalActivitiesCount
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminActivityDto> getActivities(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<AuditLogEntity> logPage = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);

        Set<Long> userIds = new HashSet<>();
        for (AuditLogEntity log : logPage.getContent()) {
            if (log.getActorUserId() != null) userIds.add(log.getActorUserId());
        }

        Map<Long, LearningUserProfileEntity> userMap = userProfileRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(LearningUserProfileEntity::getId, u -> u));

        List<AdminActivityDto> dtos = logPage.getContent().stream().map(log -> {
            LearningUserProfileEntity user = log.getActorUserId() != null ? userMap.get(log.getActorUserId()) : null;
            String actorName = user != null ? user.getDisplayName() : "System";
            String actorRole = user != null ? user.getRole().name() : "SYSTEM";
            String title = formatActivityTitle(log.getAction());
            String desc = formatActivityDesc(log.getAction(), actorName, log.getEntityType(), log.getEntityId());

            return new AdminActivityDto(
                    log.getId(),
                    log.getAction(),
                    log.getActorUserId(),
                    actorName,
                    actorRole,
                    log.getEntityType(),
                    log.getEntityId(),
                    title,
                    desc,
                    log.getDetails(),
                    log.getCreatedAt()
            );
        }).toList();

        return new PageResponse<>(
                dtos,
                logPage.getNumber(),
                logPage.getSize(),
                logPage.getTotalElements(),
                logPage.getTotalPages()
        );
    }

    private String formatActivityTitle(String action) {
        if (action == null) return "Activity";
        return action.replace('_', ' ');
    }

    private String formatActivityDesc(String action, String actor, String entityType, String entityId) {
        return actor + " performed " + (action != null ? action : "action") + " on " + entityType + " #" + entityId;
    }
}
