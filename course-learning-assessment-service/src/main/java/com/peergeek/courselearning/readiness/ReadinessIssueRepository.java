package com.peergeek.courselearning.readiness;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReadinessIssueRepository extends JpaRepository<ReadinessIssueEntity, Long> {
    List<ReadinessIssueEntity> findByRunId(Long runId);
}

