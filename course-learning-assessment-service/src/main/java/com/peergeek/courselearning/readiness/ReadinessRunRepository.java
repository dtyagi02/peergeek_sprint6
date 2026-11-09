package com.peergeek.courselearning.readiness;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReadinessRunRepository extends JpaRepository<ReadinessRunEntity, Long> {
    Optional<ReadinessRunEntity> findFirstByCourseIdOrderByCreatedAtDesc(Long courseId);
    List<ReadinessRunEntity> findByCourseIdOrderByCreatedAtDesc(Long courseId);
    void deleteByCourseId(Long courseId);
}

