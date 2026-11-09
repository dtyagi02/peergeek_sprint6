package com.peergeek.courselearning.submission;

import com.peergeek.courselearning.common.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<SubmissionEntity, Long> {

    Optional<SubmissionEntity> findByAssignmentIdAndUserId(Long assignmentId, Long userId);

    List<SubmissionEntity> findByAssignmentIdOrderBySubmittedAtDesc(Long assignmentId);

    List<SubmissionEntity> findByUserIdOrderByStartedAtDesc(Long userId);

    List<SubmissionEntity> findByUserIdAndAssignmentIdIn(Long userId, List<Long> assignmentIds);

    int countByAssignmentIdAndStatus(Long assignmentId, SubmissionStatus status);
}

