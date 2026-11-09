package com.peergeek.submission;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionFileRepository extends JpaRepository<SubmissionFileEntity, Long> {

	List<SubmissionFileEntity> findBySubmissionId(Long submissionId);

	List<SubmissionFileEntity> findBySubmissionIdAndStatus(Long submissionId, SubmissionFileStatus status);

	Optional<SubmissionFileEntity> findByIdAndSubmissionId(Long id, Long submissionId);

	Optional<SubmissionFileEntity> findByObjectKey(String objectKey);

	int countBySubmissionIdAndStatus(Long submissionId, SubmissionFileStatus status);

	List<SubmissionFileEntity> findByStatusAndCreatedAtBefore(SubmissionFileStatus status, Instant before);
}
