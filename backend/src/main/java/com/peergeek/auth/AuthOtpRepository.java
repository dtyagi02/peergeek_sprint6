package com.peergeek.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AuthOtpRepository extends JpaRepository<AuthOtpEntity, Long> {
	List<AuthOtpEntity> findAllByEmailAndPurposeAndConsumedAtIsNull(String email, OtpPurpose purpose);

	Optional<AuthOtpEntity> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, OtpPurpose purpose);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select otp from AuthOtpEntity otp where otp.email = :email and otp.purpose = :purpose and otp.consumedAt is null order by otp.createdAt desc")
	List<AuthOtpEntity> findAllUnconsumedForUpdate(@Param("email") String email, @Param("purpose") OtpPurpose purpose);

	Optional<AuthOtpEntity> findByResetTokenHashAndConsumedAtIsNull(String resetTokenHash);
}
