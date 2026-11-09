package com.peergeek.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtpEntity, Long> {
	List<PasswordResetOtpEntity> findAllByUser_IdAndConsumedAtIsNull(Long userId);

	Optional<PasswordResetOtpEntity> findFirstByUser_IdAndConsumedAtIsNullOrderByCreatedAtDesc(Long userId);

	Optional<PasswordResetOtpEntity> findFirstByUser_IdOrderByCreatedAtDesc(Long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select otp from PasswordResetOtpEntity otp where otp.user.id = :userId and otp.consumedAt is null order by otp.createdAt desc")
	List<PasswordResetOtpEntity> findAllUnconsumedForUpdate(@Param("userId") Long userId);
}
