package com.peergeek.courselearning.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionFileRepository extends JpaRepository<SubmissionFileEntity, Long> {
    List<SubmissionFileEntity> findBySubmissionId(Long submissionId);
    Optional<SubmissionFileEntity> findByIdAndSubmissionId(Long id, Long submissionId);
}

