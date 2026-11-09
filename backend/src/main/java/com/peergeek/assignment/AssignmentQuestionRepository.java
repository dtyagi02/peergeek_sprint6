package com.peergeek.assignment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentQuestionRepository extends JpaRepository<AssignmentQuestionEntity, Long> {

	List<AssignmentQuestionEntity> findByAssignmentIdOrderByPositionAsc(Long assignmentId);

	void deleteByAssignmentId(Long assignmentId);
}
