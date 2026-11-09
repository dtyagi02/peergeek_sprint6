package com.peergeek.courselearning.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionAnswerRepository extends JpaRepository<SubmissionAnswerEntity, Long> {
    List<SubmissionAnswerEntity> findBySubmissionId(Long submissionId);
    Optional<SubmissionAnswerEntity> findBySubmissionIdAndQuestionId(Long submissionId, Long questionId);
}

