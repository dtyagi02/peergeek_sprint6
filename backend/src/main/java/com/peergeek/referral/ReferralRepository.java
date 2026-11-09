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

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT r FROM ReferralEntity r WHERE r.referredUser.id = :userId")
	Optional<ReferralEntity> findByReferredUserIdForUpdate(@Param("userId") Long userId);

	Optional<ReferralEntity> findFirstByReferredUserIdAndStatus(Long userId, ReferralStatus status);

	Optional<ReferralEntity> findFirstByInvitedEmailAndStatus(String invitedEmail, ReferralStatus status);

	boolean existsByReferredUserId(Long userId);

	boolean existsByReferralCode(String referralCode);

	boolean existsByInviter_IdAndInvitedEmailAndStatus(Long inviterId, String invitedEmail, ReferralStatus status);

	@Query("SELECT COUNT(r) FROM ReferralEntity r WHERE r.inviter.id = :inviterId AND r.createdAt >= :since")
	long countRecentInvitations(@Param("inviterId") Long inviterId, @Param("since") Instant since);

	List<ReferralEntity> findAllByInviter_Id(Long inviterId);

	Page<ReferralEntity> findAllByInviter_IdOrderByCreatedAtDesc(Long inviterId, Pageable pageable);
}

