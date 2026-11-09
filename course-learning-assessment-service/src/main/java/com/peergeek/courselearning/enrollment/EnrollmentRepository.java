package com.peergeek.courselearning.enrollment;

import com.peergeek.courselearning.common.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<EnrollmentEntity, Long> {

    Optional<EnrollmentEntity> findByUserIdAndCourseId(Long userId, Long courseId);

    Optional<EnrollmentEntity> findByUserIdAndCourseIdAndStatus(Long userId, Long courseId, EnrollmentStatus status);

    boolean existsByUserIdAndCourseIdAndStatus(Long userId, Long courseId, EnrollmentStatus status);

    List<EnrollmentEntity> findByUserIdAndStatusOrderByEnrolledAtDesc(Long userId, EnrollmentStatus status);

    Page<EnrollmentEntity> findByUserIdAndStatusOrderByEnrolledAtDesc(Long userId, EnrollmentStatus status, Pageable pageable);

    int countByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    int countByUserIdAndStatus(Long userId, EnrollmentStatus status);
}

