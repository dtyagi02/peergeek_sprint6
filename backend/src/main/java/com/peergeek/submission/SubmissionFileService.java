package com.peergeek.submission;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import com.peergeek.assignment.AssignmentEntity;
import com.peergeek.assignment.AssignmentException;
import com.peergeek.assignment.AssignmentRepository;
import com.peergeek.assignment.AssignmentStatus;
import com.peergeek.config.AssignmentSettings;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStatus;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.enrollment.EnrollmentQueryService;
import com.peergeek.enrollment.EnrollmentRequiredException;
import com.peergeek.exception.BadRequestException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.file.ObjectStorageService;
import com.peergeek.file.ObjectStorageService.Disposition;
import com.peergeek.file.ObjectStorageService.ObjectHead;
import com.peergeek.file.ObjectStorageService.PresignedGet;
import com.peergeek.file.ObjectStorageService.PresignedPut;
import com.peergeek.submission.dto.FileConfirmResponse;
import com.peergeek.submission.dto.FileDownloadUrlResponse;
import com.peergeek.submission.dto.FileUploadRequest;
import com.peergeek.submission.dto.FileUploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SubmissionFileService {

	private static final Logger log = LoggerFactory.getLogger(SubmissionFileService.class);
	private static final int SAMPLE_SIZE = 65536;

	private final SubmissionFileRepository fileRepository;
	private final SubmissionRepository submissionRepository;
	private final AssignmentRepository assignmentRepository;
	private final CourseStructureQueryService courseStructureQueryService;
	private final EnrollmentQueryService enrollmentQueryService;
	private final ObjectStorageService objectStorageService;
	private final AssignmentSettings settings;
	private final Clock clock;

	public SubmissionFileService(
			SubmissionFileRepository fileRepository,
			SubmissionRepository submissionRepository,
			AssignmentRepository assignmentRepository,
			CourseStructureQueryService courseStructureQueryService,
			EnrollmentQueryService enrollmentQueryService,
			ObjectStorageService objectStorageService,
			AssignmentSettings settings,
			Clock clock) {
		this.fileRepository = fileRepository;
		this.submissionRepository = submissionRepository;
		this.assignmentRepository = assignmentRepository;
		this.courseStructureQueryService = courseStructureQueryService;
		this.enrollmentQueryService = enrollmentQueryService;
		this.objectStorageService = objectStorageService;
		this.settings = settings;
		this.clock = clock;
	}

	@Transactional
	public FileUploadResponse initiateUpload(long assignmentId, FileUploadRequest request, long userId) {
		AssignmentEntity assignment = loadPublishedAssignment(assignmentId);
		verifyCourseVisibilityAndEnrollment(assignment.getCourseId(), userId);

		SubmissionEntity submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId)
				.orElseThrow(() -> new NotFoundException("Submission attempt not found"));

		if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
			throw AssignmentException.alreadySubmitted("Cannot upload files after submission");
		}

		Instant now = clock.instant();
		if (submission.getDeadlineAt() != null) {
			Instant cutoff = submission.getDeadlineAt().plus(Duration.ofSeconds(settings.getGraceSeconds()));
			if (now.isAfter(cutoff)) {
				throw AssignmentException.attemptExpired("Attempt has expired");
			}
		}

		if (!StringUtils.hasText(request.filename())) {
			throw new BadRequestException("Filename is required");
		}

		String extension = SubmissionFileSniffer.getExtension(request.filename()).toLowerCase(Locale.ROOT);
		List<String> allowedExtensions = settings.getAllowedFileExtensions().stream()
				.map(e -> e.toLowerCase(Locale.ROOT))
				.toList();

		if (!allowedExtensions.contains(extension)) {
			throw new BadRequestException("File type not allowed: " + extension);
		}

		if (request.sizeBytes() <= 0 || request.sizeBytes() > settings.getMaxFileSizeBytes()) {
			throw new BadRequestException("File size must be between 1 byte and " + settings.getMaxFileSizeBytes() + " bytes");
		}

		int readyCount = fileRepository.countBySubmissionIdAndStatus(submission.getId(), SubmissionFileStatus.READY);
		int pendingCount = fileRepository.countBySubmissionIdAndStatus(submission.getId(), SubmissionFileStatus.PENDING);
		if (readyCount + pendingCount >= settings.getMaxFilesPerSubmission()) {
			throw new BadRequestException("Maximum files limit reached (" + settings.getMaxFilesPerSubmission() + ")");
		}

		String key = "submissions/" + assignmentId + "/" + submission.getId() + "/" + UUID.randomUUID();
		PresignedPut presigned = objectStorageService.presignPut(
				key, request.mimeType(), request.sizeBytes(), Duration.ofMinutes(15));

		SubmissionFileEntity entity = new SubmissionFileEntity(
				submission,
				userId,
				sanitizeFilename(request.filename()),
				key,
				request.mimeType(),
				request.sizeBytes(),
				now);
		entity.setStatus(SubmissionFileStatus.PENDING);
		SubmissionFileEntity saved = fileRepository.save(entity);

		return new FileUploadResponse(
				saved.getId(),
				new FileUploadResponse.UploadDetails(
						presigned.url(),
						"PUT",
						presigned.headers(),
						presigned.expiresAt()));
	}

	@Transactional
	public FileConfirmResponse confirmUpload(long assignmentId, long fileId, long userId) {
		SubmissionFileEntity file = fileRepository.findById(fileId)
				.orElseThrow(() -> new NotFoundException("File not found"));

		SubmissionEntity submission = file.getSubmission();
		if (submission == null || !submission.getAssignmentId().equals(assignmentId) || !file.getUploadedBy().equals(userId)) {
			throw new NotFoundException("File not found");
		}

		if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
			throw AssignmentException.alreadySubmitted("Cannot confirm file after submission");
		}

		Optional<ObjectHead> head = objectStorageService.head(file.getObjectKey());
		if (head.isEmpty()) {
			file.setStatus(SubmissionFileStatus.FAILED);
			file.setFailureReason("OBJECT_NOT_FOUND");
			fileRepository.save(file);
			return new FileConfirmResponse(fileId, "FAILED", "OBJECT_NOT_FOUND");
		}

		if (head.get().sizeBytes() != file.getSizeBytes() || head.get().sizeBytes() == 0) {
			file.setStatus(SubmissionFileStatus.FAILED);
			file.setFailureReason("SIZE_MISMATCH");
			fileRepository.save(file);
			return new FileConfirmResponse(fileId, "FAILED", "SIZE_MISMATCH");
		}

		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			ByteArrayOutputStream sample = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			long bytesReadTotal = 0;

			try (InputStream in = objectStorageService.openStream(file.getObjectKey())) {
				int len;
				while ((len = in.read(buffer)) != -1) {
					bytesReadTotal += len;
					digest.update(buffer, 0, len);
					if (sample.size() < SAMPLE_SIZE) {
						sample.write(buffer, 0, Math.min(len, SAMPLE_SIZE - sample.size()));
					}
				}
			}

			if (bytesReadTotal != file.getSizeBytes()) {
				file.setStatus(SubmissionFileStatus.FAILED);
				file.setFailureReason("SIZE_MISMATCH");
				fileRepository.save(file);
				return new FileConfirmResponse(fileId, "FAILED", "SIZE_MISMATCH");
			}

			boolean validContent = SubmissionFileSniffer.isValidContent(
					file.getOriginalFilename(), file.getMimeType(), sample.toByteArray());

			if (!validContent) {
				file.setStatus(SubmissionFileStatus.FAILED);
				file.setFailureReason("CONTENT_MISMATCH");
				fileRepository.save(file);
				return new FileConfirmResponse(fileId, "FAILED", "CONTENT_MISMATCH");
			}

			String checksum = HexFormat.of().formatHex(digest.digest());
			file.setStatus(SubmissionFileStatus.READY);
			file.setChecksumSha256(checksum);
			file.setSizeBytes(bytesReadTotal);
			file.setFailureReason(null);
			fileRepository.save(file);

			return new FileConfirmResponse(fileId, "READY", null);
		} catch (Exception ex) {
			log.warn("Error verifying submission file id={}", fileId, ex);
			file.setStatus(SubmissionFileStatus.FAILED);
			file.setFailureReason("PROCESSING_ERROR");
			fileRepository.save(file);
			return new FileConfirmResponse(fileId, "FAILED", "PROCESSING_ERROR");
		}
	}

	@Transactional
	public void deleteFile(long assignmentId, long fileId, long userId) {
		SubmissionFileEntity file = fileRepository.findById(fileId)
				.orElseThrow(() -> new NotFoundException("File not found"));

		SubmissionEntity submission = file.getSubmission();
		if (submission == null || !submission.getAssignmentId().equals(assignmentId) || !file.getUploadedBy().equals(userId)) {
			throw new NotFoundException("File not found");
		}

		if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
			throw AssignmentException.alreadySubmitted("Cannot delete files after submission");
		}

		try {
			objectStorageService.delete(file.getObjectKey());
		} catch (Exception ex) {
			log.warn("Failed to delete object from storage key={}", file.getObjectKey(), ex);
		}

		fileRepository.delete(file);
	}

	@Transactional(readOnly = true)
	public FileDownloadUrlResponse getLearnerDownloadUrl(long assignmentId, long fileId, long userId) {
		SubmissionFileEntity file = fileRepository.findById(fileId)
				.orElseThrow(() -> new NotFoundException("File not found"));

		SubmissionEntity submission = file.getSubmission();
		if (submission == null || !submission.getAssignmentId().equals(assignmentId) || !file.getUploadedBy().equals(userId)) {
			throw new NotFoundException("File not found");
		}

		if (file.getStatus() != SubmissionFileStatus.READY) {
			throw new NotFoundException("File is not ready for download");
		}

		PresignedGet presigned = objectStorageService.presignGet(
				file.getObjectKey(),
				file.getOriginalFilename(),
				Duration.ofMinutes(15),
				Disposition.ATTACHMENT);

		return new FileDownloadUrlResponse(presigned.url(), presigned.expiresAt());
	}

	@Transactional(readOnly = true)
	public FileDownloadUrlResponse getInstructorDownloadUrl(long submissionId, long fileId, long instructorUserId) {
		SubmissionFileEntity file = fileRepository.findByIdAndSubmissionId(fileId, submissionId)
				.orElseThrow(() -> new NotFoundException("File not found"));

		SubmissionEntity submission = file.getSubmission();
		if (submission == null) {
			throw new NotFoundException("File not found");
		}

		AssignmentEntity assignment = assignmentRepository.findById(submission.getAssignmentId())
				.orElseThrow(() -> new NotFoundException("Assignment not found"));

		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(assignment.getCourseId())
				.orElseThrow(() -> new NotFoundException("Course not found"));

		if (metadata.deleted() || !metadata.ownerId().equals(instructorUserId)) {
			throw new NotFoundException("Course not found");
		}

		if (file.getStatus() != SubmissionFileStatus.READY) {
			throw new NotFoundException("File is not ready for download");
		}

		PresignedGet presigned = objectStorageService.presignGet(
				file.getObjectKey(),
				file.getOriginalFilename(),
				Duration.ofMinutes(15),
				Disposition.ATTACHMENT);

		return new FileDownloadUrlResponse(presigned.url(), presigned.expiresAt());
	}

	@Transactional
	public void cleanupAbandonedFiles() {
		Instant pendingCutoff = clock.instant().minus(Duration.ofHours(settings.getAbandonedPendingHours()));
		List<SubmissionFileEntity> pendingAbandoned = fileRepository.findByStatusAndCreatedAtBefore(
				SubmissionFileStatus.PENDING, pendingCutoff);

		int pendingCount = 0;
		for (SubmissionFileEntity file : pendingAbandoned) {
			try {
				objectStorageService.delete(file.getObjectKey());
			} catch (Exception ex) {
				log.warn("Failed to delete storage object key={}", file.getObjectKey(), ex);
			}
			file.setStatus(SubmissionFileStatus.FAILED);
			file.setFailureReason("ABANDONED_UPLOAD");
			fileRepository.save(file);
			pendingCount++;
		}

		Instant failedCutoff = clock.instant().minus(Duration.ofHours(settings.getFailedCleanupHours()));
		List<SubmissionFileEntity> failedAbandoned = fileRepository.findByStatusAndCreatedAtBefore(
				SubmissionFileStatus.FAILED, failedCutoff);

		int failedCount = 0;
		for (SubmissionFileEntity file : failedAbandoned) {
			try {
				objectStorageService.delete(file.getObjectKey());
			} catch (Exception ex) {
				log.warn("Failed to delete storage object key={}", file.getObjectKey(), ex);
			}
			fileRepository.delete(file);
			failedCount++;
		}

		if (pendingCount > 0 || failedCount > 0) {
			log.info("Cleaned up {} abandoned pending files and {} failed files", pendingCount, failedCount);
		}
	}

	private String sanitizeFilename(String filename) {
		if (!StringUtils.hasText(filename)) {
			return "submission-file";
		}
		String sanitized = filename.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
		return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
	}

	private AssignmentEntity loadPublishedAssignment(long assignmentId) {
		AssignmentEntity assignment = assignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new NotFoundException("Assignment not found"));
		if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
			throw new NotFoundException("Assignment not found");
		}
		return assignment;
	}

	private void verifyCourseVisibilityAndEnrollment(long courseId, long userId) {
		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("Course not found"));

		if (metadata.deleted()) {
			throw new NotFoundException("Course not found");
		}

		boolean activelyEnrolled = enrollmentQueryService.isActivelyEnrolled(userId, courseId);
		if (metadata.status() != CourseStatus.PUBLISHED) {
			if (!activelyEnrolled) {
				throw new NotFoundException("Course not found");
			}
		} else {
			if (!activelyEnrolled) {
				throw new EnrollmentRequiredException();
			}
		}
	}
}
