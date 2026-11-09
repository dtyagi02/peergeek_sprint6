package com.peergeek.courselearning.progress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourseProgressRepository extends JpaRepository<CourseProgressEntity, Long> {
    Optional<CourseProgressEntity> findByEnrollmentId(Long enrollmentId);
}

