package com.peergeek.submission;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionAnswerRepository extends JpaRepository<SubmissionAnswerEntity, Long> {

	List<SubmissionAnswerEntity> findBySubmissionId(Long submissionId);

	Optional<SubmissionAnswerEntity> findBySubmissionIdAndQuestionId(Long submissionId, Long questionId);

	void deleteBySubmissionId(Long submissionId);
}
