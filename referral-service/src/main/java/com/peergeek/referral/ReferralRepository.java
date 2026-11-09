package com.peergeek.referral;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReferralRepository extends JpaRepository<ReferralEntity, Long> {

	Optional<ReferralEntity> findByReferralCode(String referralCode);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT r FROM ReferralEntity r WHERE r.referralCode = :referralCode")
	Optional<ReferralEntity> findByReferralCodeForUpdate(@Param("referralCode") String referralCode);

	Optional<ReferralEntity> findByReferredUserId(Long referredUserId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT r FROM ReferralEntity r WHERE r.referredUserId = :referredUserId")
	Optional<ReferralEntity> findByReferredUserIdForUpdate(@Param("referredUserId") Long referredUserId);

	boolean existsByReferralCode(String referralCode);

	boolean existsByInviterIdAndInvitedEmailAndStatus(Long inviterId, String invitedEmail, ReferralStatus status);

	boolean existsByReferredUserId(Long referredUserId);

	@Query("SELECT COUNT(r) FROM ReferralEntity r WHERE r.inviterId = :inviterId AND r.createdAt >= :since")
	long countRecentInvitations(@Param("inviterId") Long inviterId, @Param("since") Instant since);

	List<ReferralEntity> findAllByInviterId(Long inviterId);

	Page<ReferralEntity> findAllByInviterIdOrderByCreatedAtDesc(Long inviterId, Pageable pageable);
}

