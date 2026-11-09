package com.peergeek.submission;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SubmissionRepository extends JpaRepository<SubmissionEntity, Long> {

	Optional<SubmissionEntity> findByAssignmentIdAndUserId(Long assignmentId, Long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from SubmissionEntity s where s.assignmentId = :assignmentId and s.userId = :userId")
	Optional<SubmissionEntity> findWithLockByAssignmentIdAndUserId(
			@Param("assignmentId") Long assignmentId,
			@Param("userId") Long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from SubmissionEntity s where s.id = :id")
	Optional<SubmissionEntity> findByIdWithLock(@Param("id") Long id);

	List<SubmissionEntity> findByAssignmentId(Long assignmentId);

	Page<SubmissionEntity> findByAssignmentIdAndStatus(Long assignmentId, SubmissionStatus status, Pageable pageable);

	Page<SubmissionEntity> findByAssignmentId(Long assignmentId, Pageable pageable);

	Page<SubmissionEntity> findByAssignmentIdInAndStatus(Collection<Long> assignmentIds, SubmissionStatus status, Pageable pageable);

	Page<SubmissionEntity> findByAssignmentIdIn(Collection<Long> assignmentIds, Pageable pageable);

	List<SubmissionEntity> findByUserId(Long userId);

	Page<SubmissionEntity> findByUserId(Long userId, Pageable pageable);

	Page<SubmissionEntity> findByUserIdAndStatus(Long userId, SubmissionStatus status, Pageable pageable);

	List<SubmissionEntity> findByUserIdAndAssignmentIdIn(Long userId, Collection<Long> assignmentIds);

	int countByAssignmentId(Long assignmentId);

	int countByAssignmentIdAndStatus(Long assignmentId, SubmissionStatus status);

	@Query("select s from SubmissionEntity s where s.status = :status and s.deadlineAt is not null and s.deadlineAt < :threshold")
	List<SubmissionEntity> findExpiredInProgress(
			@Param("status") SubmissionStatus status,
			@Param("threshold") Instant threshold,
			Pageable pageable);

	@Query("select s.assignmentId as assignmentId, s.status as status, count(s.id) as count from SubmissionEntity s where s.assignmentId in :assignmentIds group by s.assignmentId, s.status")
	List<SubmissionCountProjection> countByAssignmentIdInGroupByStatus(@Param("assignmentIds") Collection<Long> assignmentIds);

	int countByAssignmentIdAndStatusIn(Long assignmentId, Collection<SubmissionStatus> statuses);

	int countByAssignmentIdInAndStatus(Collection<Long> assignmentIds, SubmissionStatus status);

	@Query("select s.userId from SubmissionEntity s where s.assignmentId = :assignmentId and s.status in :statuses")
	java.util.Set<Long> findUserIdsByAssignmentIdAndStatusIn(
			@Param("assignmentId") Long assignmentId,
			@Param("statuses") Collection<SubmissionStatus> statuses);

	@Query("select avg(s.totalPoints) as avgScore, max(s.totalPoints) as maxScore, min(s.totalPoints) as minScore " +
			"from SubmissionEntity s where s.assignmentId = :assignmentId and s.status = :status")
	SubmissionScoreStats getScoreStatsByAssignmentId(
			@Param("assignmentId") Long assignmentId,
			@Param("status") SubmissionStatus status);

	interface SubmissionScoreStats {
		Double getAvgScore();
		Integer getMaxScore();
		Integer getMinScore();
	}

	interface SubmissionCountProjection {
		Long getAssignmentId();
		SubmissionStatus getStatus();
		int getCount();
	}
}
