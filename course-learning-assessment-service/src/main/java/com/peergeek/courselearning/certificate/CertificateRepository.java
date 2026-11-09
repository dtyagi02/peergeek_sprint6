package com.peergeek.courselearning.certificate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<CertificateEntity, Long> {

    Optional<CertificateEntity> findByEnrollmentId(Long enrollmentId);

    Optional<CertificateEntity> findByVerificationCode(String verificationCode);

    Optional<CertificateEntity> findByCertificateNumber(String certificateNumber);

    List<CertificateEntity> findByUserIdOrderByIssuedAtDesc(Long userId);

    Page<CertificateEntity> findAllByOrderByIssuedAtDesc(Pageable pageable);

    int countByUserId(Long userId);

    int countByCourseId(Long courseId);
}

