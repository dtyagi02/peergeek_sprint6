package com.peergeek.doubtsession.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.peergeek.doubtsession.entity.DoubtSessionEntity;
import com.peergeek.doubtsession.entity.DoubtSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DoubtSessionRepository extends JpaRepository<DoubtSessionEntity, Long> {

	Optional<DoubtSessionEntity> findBySessionUid(String sessionUid);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from DoubtSessionEntity s where s.sessionUid = :sessionUid")
	Optional<DoubtSessionEntity> findWithLockBySessionUid(@Param("sessionUid") String sessionUid);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from DoubtSessionEntity s where s.id = :id")
	Optional<DoubtSessionEntity> findWithLockById(@Param("id") Long id);

	@Query("select s from DoubtSessionEntity s where s.instructorId = :instructorId and s.status in :statuses order by s.requestedAt desc")
	List<DoubtSessionEntity> findActiveByInstructorId(
			@Param("instructorId") Long instructorId,
			@Param("statuses") Collection<DoubtSessionStatus> statuses);

	@Query("select s from DoubtSessionEntity s where s.studentId = :studentId and s.status in :statuses order by s.requestedAt desc")
	List<DoubtSessionEntity> findActiveByStudentId(
			@Param("studentId") Long studentId,
			@Param("statuses") Collection<DoubtSessionStatus> statuses);

	boolean existsByInstructorIdAndStatusIn(Long instructorId, Collection<DoubtSessionStatus> statuses);

	boolean existsByStudentIdAndStatusIn(Long studentId, Collection<DoubtSessionStatus> statuses);

	List<DoubtSessionEntity> findByStatusAndExpiresAtBefore(DoubtSessionStatus status, Instant now);

	List<DoubtSessionEntity> findByStatusAndConnectDeadlineAtBefore(DoubtSessionStatus status, Instant now);

	@Query("select s from DoubtSessionEntity s where s.status = :status and s.reconnectDeadlineAt is not null and s.reconnectDeadlineAt < :now")
	List<DoubtSessionEntity> findReconnectTimedOut(
			@Param("status") DoubtSessionStatus status,
			@Param("now") Instant now);

	Page<DoubtSessionEntity> findByStudentIdOrderByRequestedAtDesc(Long studentId, Pageable pageable);

	Page<DoubtSessionEntity> findByInstructorIdOrderByRequestedAtDesc(Long instructorId, Pageable pageable);

	Optional<DoubtSessionEntity> findByLivekitRoomName(String livekitRoomName);
}

