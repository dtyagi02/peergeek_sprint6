package com.peergeek.courselearning.course;

import com.peergeek.courselearning.common.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<CourseEntity, Long> {

    List<CourseEntity> findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long ownerId);

    Optional<CourseEntity> findByIdAndDeletedAtIsNull(Long id);

    Optional<CourseEntity> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

    Page<CourseEntity> findByStatusAndDeletedAtIsNull(CourseStatus status, Pageable pageable);

    @Query("SELECT c FROM CourseEntity c WHERE c.deletedAt IS NULL AND " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:query IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<CourseEntity> findForAdmin(@Param("status") CourseStatus status,
                                    @Param("query") String query,
                                    Pageable pageable);

    long countByOwnerIdAndStatusAndDeletedAtIsNull(Long ownerId, CourseStatus status);

    long countByStatusAndDeletedAtIsNull(CourseStatus status);
}

