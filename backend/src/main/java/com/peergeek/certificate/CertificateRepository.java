package com.peergeek.certificate;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CertificateRepository extends JpaRepository<CertificateEntity, Long>, JpaSpecificationExecutor<CertificateEntity> {
	Optional<CertificateEntity> findByEnrollmentId(Long enrollmentId);
	Optional<CertificateEntity> findByVerificationCode(String verificationCode);
	Optional<CertificateEntity> findByCertificateNumber(String certificateNumber);
	List<CertificateEntity> findByUserIdOrderByIssuedAtDesc(Long userId);

	@Query("SELECT c FROM CertificateEntity c WHERE " +
			"(:status IS NULL OR c.status = :status) AND " +
			"(:courseId IS NULL OR c.courseId = :courseId) AND " +
			"(:userId IS NULL OR c.userId = :userId) AND " +
			"(:search IS NULL OR LOWER(c.studentName) LIKE LOWER(CONCAT('%', :search, '%')) " +
			"OR LOWER(c.courseTitle) LIKE LOWER(CONCAT('%', :search, '%')) " +
			"OR LOWER(c.certificateNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
	Page<CertificateEntity> findFiltered(
			@Param("status") CertificateStatus status,
			@Param("search") String search,
			@Param("courseId") Long courseId,
			@Param("userId") Long userId,
			Pageable pageable);
}
