package com.peergeek.courselearning.report;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<ReportEntity, Long> {

    Page<ReportEntity> findByCourseId(Long courseId, Pageable pageable);

    Page<ReportEntity> findByCourseIdAndStatus(Long courseId, ReportStatus status, Pageable pageable);

    Page<ReportEntity> findByReporterUserId(Long reporterUserId, Pageable pageable);

    long countByStatusIn(Collection<ReportStatus> statuses);

    @Query("""
            select r.courseId, count(r) from ReportEntity r
            where r.courseId in :courseIds
            and r.status in ('PENDING', 'IN_REVIEW')
            group by r.courseId
            """)
    List<Object[]> countOpenReportsByCourseIds(@Param("courseIds") Collection<Long> courseIds);

    @Query(value = """
            select r from ReportEntity r
            left join CourseEntity c on r.courseId = c.id
            where (:useStatuses = false or r.status in :statuses)
            and (:query is null or lower(c.title) like lower(concat('%', :query, '%')) or lower(r.details) like lower(concat('%', :query, '%')))
            order by r.createdAt desc
            """,
            countQuery = """
            select count(r) from ReportEntity r
            left join CourseEntity c on r.courseId = c.id
            where (:useStatuses = false or r.status in :statuses)
            and (:query is null or lower(c.title) like lower(concat('%', :query, '%')) or lower(r.details) like lower(concat('%', :query, '%')))
            """)
    Page<ReportEntity> findAdminReports(
            @Param("useStatuses") boolean useStatuses,
            @Param("statuses") Collection<ReportStatus> statuses,
            @Param("query") String query,
            Pageable pageable);
}
