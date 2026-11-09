package com.peergeek.assignment;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<AssignmentEntity, Long> {

	List<AssignmentEntity> findByCourseId(Long courseId);

	Page<AssignmentEntity> findByCourseId(Long courseId, Pageable pageable);

	Page<AssignmentEntity> findByCourseIdAndStatus(Long courseId, AssignmentStatus status, Pageable pageable);

	List<AssignmentEntity> findByCourseIdAndStatus(Long courseId, AssignmentStatus status);

	List<AssignmentEntity> findByCourseIdAndStatusIn(Long courseId, Collection<AssignmentStatus> statuses);

	int countByCourseId(Long courseId);

	int countByCourseIdAndStatus(Long courseId, AssignmentStatus status);

	boolean existsByCourseIdAndStatus(Long courseId, AssignmentStatus status);

	java.util.Optional<AssignmentEntity> findByIdAndCourseId(Long id, Long courseId);

	org.springframework.data.domain.Page<AssignmentEntity> findByStatusAndDueAtBetween(
			AssignmentStatus status, java.time.Instant from, java.time.Instant to, Pageable pageable);

	@Query("select a from AssignmentEntity a where a.courseId in :courseIds and a.status = :status")
	List<AssignmentEntity> findByCourseIdInAndStatus(
			@Param("courseIds") Collection<Long> courseIds,
			@Param("status") AssignmentStatus status);
}
