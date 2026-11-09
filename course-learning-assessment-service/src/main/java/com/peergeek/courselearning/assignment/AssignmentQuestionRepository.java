package com.peergeek.courselearning.assignment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentQuestionRepository extends JpaRepository<AssignmentQuestionEntity, Long> {
    List<AssignmentQuestionEntity> findByAssignmentIdOrderByPositionAsc(Long assignmentId);
    void deleteByAssignmentId(Long assignmentId);
}

