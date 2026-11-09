package com.peergeek.courselearning.assignment;

import com.peergeek.courselearning.common.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentRepository extends JpaRepository<AssignmentEntity, Long> {

    List<AssignmentEntity> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    List<AssignmentEntity> findByCourseIdAndStatusOrderByCreatedAtDesc(Long courseId, AssignmentStatus status);

    Optional<AssignmentEntity> findByIdAndCourseId(Long id, Long courseId);

    int countByCourseId(Long courseId);

    int countByCourseIdAndStatus(Long courseId, AssignmentStatus status);
}

