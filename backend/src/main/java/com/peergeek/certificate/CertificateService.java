package com.peergeek.certificate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import com.peergeek.certificate.dto.*;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.enrollment.EnrollmentEntity;
import com.peergeek.enrollment.EnrollmentRepository;
import com.peergeek.enrollment.EnrollmentStatus;
import com.peergeek.exception.ConflictException;
import com.peergeek.exception.ForbiddenException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.file.ObjectStorageService;
import com.peergeek.notification.NotificationService;
import com.peergeek.notification.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CertificateService {
	private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

	private final CertificateRepository certificates;
	private final EnrollmentRepository enrollments;
	private final CertificateEligibilityService eligibilityService;
	private final CourseStructureQueryService courses;
	private final ObjectStorageService storage;
	private final CertificatePdfGenerator pdf;
	private final NotificationService notifications;
	private final String frontendBaseUrl;

	public CertificateService(
			CertificateRepository certificates,
			EnrollmentRepository enrollments,
			CertificateEligibilityService eligibilityService,
			CourseStructureQueryService courses,
			ObjectStorageService storage,
			CertificatePdfGenerator pdf,
			NotificationService notifications,
			@Value("${peergeek.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
		this.certificates = certificates;
		this.enrollments = enrollments;
		this.eligibilityService = eligibilityService;
		this.courses = courses;
		this.storage = storage;
		this.pdf = pdf;
		this.notifications = notifications;
		this.frontendBaseUrl = frontendBaseUrl;
	}

	@Transactional
	public Optional<CertificateEntity> issueIfEligible(Long userId, Long courseId) {
		CertificateEligibilityResult eligibility = eligibilityService.checkEligibility(userId, courseId);
		return issueIfEligible(eligibility);
	}

	@Transactional
	public Optional<CertificateEntity> issueIfEligible(CertificateEligibilityResult eligibility) {
		if (eligibility == null || !eligibility.eligible()) {
			if (eligibility != null) {
				log.info("Certificate not eligible: {}", eligibility.reason());
			}
			return Optional.empty();
		}

		EnrollmentEntity enrollment = eligibility.enrollment();
		Long userId = enrollment.getUserId();
		Long courseId = enrollment.getCourseId();

		Optional<CertificateEntity> existingOpt = certificates.findByEnrollmentId(enrollment.getId());
		if (existingOpt.isPresent()) {
			CertificateEntity existing = existingOpt.get();
			if (existing.getStatus() == CertificateStatus.ISSUED) {
				log.debug("Certificate already issued for enrollmentId={}", enrollment.getId());
				return Optional.of(existing);
			}
			if (existing.getStatus() == CertificateStatus.REVOKED) {
				log.warn("Certificate for enrollmentId={} has been REVOKED and cannot be reissued", enrollment.getId());
				return Optional.of(existing);
			}
			// If FAILED or PENDING, retry generation
			existing.markPending();
			CertificateEntity saved = certificates.save(existing);
			generateAndStore(saved);
			return Optional.of(saved);
		}

		String number = "PG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
		String verification = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

		CertificateEntity certificate = new CertificateEntity(
				number,
				verification,
				userId,
				courseId,
				enrollment.getId(),
				eligibility.student().getDisplayName(),
				eligibility.course().title(),
				eligibility.instructor().getDisplayName(),
				Instant.now()
		);

		CertificateEntity saved = certificates.save(certificate);
		generateAndStore(saved);
		return Optional.of(saved);
	}

	public void generateAndStore(CertificateEntity certificate) {
		String certNumber = certificate.getCertificateNumber();
		try {
			String verificationUrl = frontendBaseUrl.replaceAll("/+$", "") + "/verify/certificate/" + certificate.getVerificationCode();
			byte[] pdfBytes = pdf.generate(certificate, verificationUrl);

			String fileKey = "certificates/" + certificate.getUserId() + "/" + certNumber + ".pdf";
			storage.putObject(fileKey, "application/pdf", pdfBytes.length, new ByteArrayInputStream(pdfBytes));

			certificate.issue(fileKey);
			certificates.save(certificate);
			log.info("Certificate {} successfully issued and stored at {}", certNumber, fileKey);

			// Send notification non-blockingly
			sendIssuanceNotification(certificate);
		} catch (Exception ex) {
			log.error("Failed to generate and store certificate {}: {}", certNumber, ex.getMessage(), ex);
			certificate.fail(ex.getMessage());
			certificates.save(certificate);
		}
	}

	private void sendIssuanceNotification(CertificateEntity certificate) {
		try {
			notifications.createNotification(
					certificate.getUserId(),
					NotificationType.CERTIFICATE_ISSUED,
					"Certificate Available!",
					"Congratulations! Your certificate of completion for " + certificate.getCourseTitle() + " is ready to download.",
					"CERTIFICATE",
					certificate.getId(),
					"/certificates",
					"certificate-issued:" + certificate.getId()
			);
		} catch (Exception ex) {
			log.warn("Could not dispatch notification for certificate {}: {}", certificate.getId(), ex.getMessage());
		}
	}

	@Transactional
	public ReconciliationSummaryDto reconcileForUser(Long userId) {
		List<EnrollmentEntity> userEnrollments = enrollments.findByUserIdAndStatus(userId, EnrollmentStatus.ACTIVE);
		return reconcileEnrollmentList(userEnrollments);
	}

	@Transactional
	public ReconciliationSummaryDto reconcileBatch(Long courseId, Long userId) {
		List<EnrollmentEntity> candidates;
		if (userId != null && courseId != null) {
			candidates = enrollments.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE)
					.map(List::of).orElse(List.of());
		} else if (userId != null) {
			candidates = enrollments.findByUserIdAndStatus(userId, EnrollmentStatus.ACTIVE);
		} else if (courseId != null) {
			// Find all active enrollments for this course
			candidates = enrollments.findAll().stream()
					.filter(e -> e.getCourseId().equals(courseId) && e.getStatus() == EnrollmentStatus.ACTIVE)
					.toList();
		} else {
			// All active enrollments
			candidates = enrollments.findAll().stream()
					.filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
					.toList();
		}
		return reconcileEnrollmentList(candidates);
	}

	private ReconciliationSummaryDto reconcileEnrollmentList(List<EnrollmentEntity> candidates) {
		int totalExamined = 0;
		int eligible = 0;
		int alreadyIssued = 0;
		int newlyIssued = 0;
		int failed = 0;
		int ineligible = 0;
		List<String> details = new ArrayList<>();

		for (EnrollmentEntity enrollment : candidates) {
			totalExamined++;
			CertificateEligibilityResult result = eligibilityService.checkEligibility(enrollment);
			if (!result.eligible()) {
				ineligible++;
				String reasonCode = classifyIneligibilityReason(result.reason());
				details.add("Enrollment " + enrollment.getId() + " (Course " + enrollment.getCourseId() + "): [" + reasonCode + "] " + result.reason());
				continue;
			}

			eligible++;
			Optional<CertificateEntity> existingOpt = certificates.findByEnrollmentId(enrollment.getId());
			if (existingOpt.isPresent()) {
				CertificateEntity existing = existingOpt.get();
				if (existing.getStatus() == CertificateStatus.ISSUED) {
					alreadyIssued++;
					details.add("Enrollment " + enrollment.getId() + " (Course " + enrollment.getCourseId() + "): [CERTIFICATE_ALREADY_ISSUED] Certificate " + existing.getCertificateNumber() + " already issued");
					continue;
				}
				if (existing.getStatus() == CertificateStatus.REVOKED) {
					details.add("Enrollment " + enrollment.getId() + " (Course " + enrollment.getCourseId() + "): [CERTIFICATE_REVOKED] Certificate is REVOKED, skipping");
					continue;
				}
				// Retry failed or pending
				existing.markPending();
				certificates.save(existing);
				generateAndStore(existing);
				if (existing.getStatus() == CertificateStatus.ISSUED) {
					newlyIssued++;
					details.add("Enrollment " + enrollment.getId() + " (Course " + enrollment.getCourseId() + "): [CERTIFICATE_GENERATED] Certificate " + existing.getCertificateNumber() + " successfully generated on retry");
				} else {
					failed++;
					details.add("Enrollment " + enrollment.getId() + " (Course " + enrollment.getCourseId() + "): [PDF_GENERATION_FAILED] " + existing.getFailureReason());
				}
			} else {
				// New certificate
				Optional<CertificateEntity> issued = issueIfEligible(result);
				if (issued.isPresent() && issued.get().getStatus() == CertificateStatus.ISSUED) {
					newlyIssued++;
					details.add("Enrollment " + enrollment.getId() + " (Course " + enrollment.getCourseId() + "): [CERTIFICATE_GENERATED] Certificate " + issued.get().getCertificateNumber() + " successfully generated");
				} else {
					failed++;
					String failReason = issued.map(CertificateEntity::getFailureReason).orElse("Issuance failed");
					details.add("Enrollment " + enrollment.getId() + " (Course " + enrollment.getCourseId() + "): [PDF_GENERATION_FAILED] " + failReason);
				}
			}
		}

		return new ReconciliationSummaryDto(totalExamined, eligible, alreadyIssued, newlyIssued, failed, ineligible, details);
	}

	private String classifyIneligibilityReason(String reason) {
		if (reason == null) return "ELIGIBILITY_CHECK_FAILED";
		if (reason.contains("Lessons incomplete") || reason.contains("contains no lessons")) {
			return "MISSING_REQUIRED_LESSONS";
		}
		if (reason.contains("Active enrollment not found") || reason.contains("Enrollment status is")) {
			return "ENROLLED_NOT_COMPLETED";
		}
		return "ELIGIBILITY_CHECK_FAILED";
	}

	@Transactional
	public void retry(Long userId, Long certificateId, boolean admin) {
		CertificateEntity certificate = certificates.findById(certificateId)
				.orElseThrow(() -> new NotFoundException("Certificate not found"));

		if (!admin && !certificate.getUserId().equals(userId)) {
			throw new ForbiddenException("You cannot retry this certificate");
		}

		if (certificate.getStatus() == CertificateStatus.REVOKED) {
			throw new ConflictException("Revoked certificates cannot be retried or reissued");
		}

		if (certificate.getStatus() == CertificateStatus.ISSUED) {
			return; // already issued
		}

		certificate.markPending();
		certificates.save(certificate);
		generateAndStore(certificate);
	}

	@Transactional
	public CertificateEntity revoke(Long certificateId, String reason, Long adminUserId) {
		CertificateEntity certificate = certificates.findById(certificateId)
				.orElseThrow(() -> new NotFoundException("Certificate not found"));

		if (certificate.getStatus() == CertificateStatus.REVOKED) {
			throw new ConflictException("Certificate is already revoked");
		}

		certificate.revoke(reason, adminUserId);
		CertificateEntity saved = certificates.save(certificate);
		log.warn("Certificate {} revoked by admin {} for reason: {}", saved.getCertificateNumber(), adminUserId, reason);
		return saved;
	}

	@Transactional(readOnly = true)
	public List<CertificateDto> listForUser(Long userId) {
		return certificates.findByUserIdOrderByIssuedAtDesc(userId).stream()
				.map(c -> CertificateDto.from(c, c.getStatus() == CertificateStatus.ISSUED ? "/api/v1/certificates/" + c.getId() + "/download-url" : null))
				.toList();
	}

	@Transactional(readOnly = true)
	public Page<AdminCertificateDto> listFiltered(CertificateStatus status, String search, Long courseId, Long userId, Pageable pageable) {
		String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
		return certificates.findFiltered(status, cleanSearch, courseId, userId, pageable)
				.map(c -> AdminCertificateDto.from(c, c.getStatus() == CertificateStatus.ISSUED ? "/api/v1/certificates/" + c.getId() + "/download" : null));
	}

	@Transactional(readOnly = true)
	public URI downloadUrl(Long userId, Long id, boolean admin) {
		CertificateEntity c = certificates.findById(id).orElseThrow(() -> new NotFoundException("Certificate not found"));
		if (!admin && !c.getUserId().equals(userId)) {
			throw new ForbiddenException("You cannot access this certificate");
		}
		if (c.getStatus() != CertificateStatus.ISSUED || c.getFileKey() == null) {
			throw new ConflictException("Certificate is not available for download");
		}
		return storage.presignGet(c.getFileKey(), c.getCertificateNumber() + ".pdf", Duration.ofMinutes(15), ObjectStorageService.Disposition.ATTACHMENT).url();
	}

	@Transactional(readOnly = true)
	public CertificateStream downloadStream(Long userId, Long id, boolean admin) {
		CertificateEntity c = certificates.findById(id).orElseThrow(() -> new NotFoundException("Certificate not found"));
		if (!admin && !c.getUserId().equals(userId)) {
			throw new ForbiddenException("You cannot access this certificate");
		}
		if (c.getStatus() != CertificateStatus.ISSUED || c.getFileKey() == null) {
			throw new ConflictException("Certificate is not available for download");
		}
		InputStream is = storage.openStream(c.getFileKey());
		var head = storage.head(c.getFileKey()).orElse(new ObjectStorageService.ObjectHead(0, "application/pdf"));
		String filename = c.getCertificateNumber() + ".pdf";
		return new CertificateStream(is, filename, head.sizeBytes());
	}

	@Transactional(readOnly = true)
	public CertificateVerificationDto verify(String code) {
		CertificateEntity c = certificates.findByVerificationCode(code).orElse(null);
		if (c == null) {
			return new CertificateVerificationDto(false, null, null, null, null, null, null, null, null);
		}

		if (c.getStatus() == CertificateStatus.REVOKED) {
			return new CertificateVerificationDto(
					false,
					CertificateStatus.REVOKED,
					c.getCertificateNumber(),
					c.getStudentName(),
					c.getCourseTitle(),
					c.getInstructorName(),
					c.getIssuedAt(),
					c.getRevokedAt(),
					c.getRevocationReason()
			);
		}

		boolean valid = c.getStatus() == CertificateStatus.ISSUED;
		return new CertificateVerificationDto(
				valid,
				c.getStatus(),
				c.getCertificateNumber(),
				c.getStudentName(),
				c.getCourseTitle(),
				c.getInstructorName(),
				c.getIssuedAt(),
				null,
				null
		);
	}

	public record CertificateStream(InputStream stream, String filename, long contentLength) {}
}
