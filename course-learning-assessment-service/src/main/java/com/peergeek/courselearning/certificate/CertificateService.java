package com.peergeek.courselearning.certificate;

import com.peergeek.courselearning.common.EnrollmentStatus;
import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.enrollment.EnrollmentEntity;
import com.peergeek.courselearning.enrollment.EnrollmentRepository;
import com.peergeek.courselearning.exception.BadRequestException;
import com.peergeek.courselearning.exception.ConflictException;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.file.ObjectStorageService;
import com.peergeek.courselearning.outbox.OutboxEventWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final CertificateRepository certificateRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CertificateEligibilityService eligibilityService;
    private final CertificatePdfGenerator pdfGenerator;
    private final ObjectStorageService storageService;
    private final OutboxEventWriter outboxEventWriter;
    private final String frontendBaseUrl;

    public CertificateService(
            CertificateRepository certificateRepository,
            EnrollmentRepository enrollmentRepository,
            CertificateEligibilityService eligibilityService,
            CertificatePdfGenerator pdfGenerator,
            ObjectStorageService storageService,
            OutboxEventWriter outboxEventWriter,
            @Value("${peergeek.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.certificateRepository = certificateRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.eligibilityService = eligibilityService;
        this.pdfGenerator = pdfGenerator;
        this.storageService = storageService;
        this.outboxEventWriter = outboxEventWriter;
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

        Optional<CertificateEntity> existingOpt = certificateRepository.findByEnrollmentId(enrollment.getId());
        if (existingOpt.isPresent()) {
            CertificateEntity existing = existingOpt.get();
            if ("ISSUED".equalsIgnoreCase(existing.getStatus())) {
                return Optional.of(existing);
            }
            if ("REVOKED".equalsIgnoreCase(existing.getStatus())) {
                log.warn("Certificate for enrollmentId={} has been REVOKED and cannot be reissued", enrollment.getId());
                return Optional.of(existing);
            }
            // FAILED or PENDING: retry
            existing.setStatus("PENDING");
            CertificateEntity saved = certificateRepository.save(existing);
            generateAndStore(saved);
            return Optional.of(saved);
        }

        String number = "PG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        String verification = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

        String studentName = (eligibility.student() != null && eligibility.student().getDisplayName() != null)
                ? eligibility.student().getDisplayName() : "PeerGeek Student";
        String courseTitle = eligibility.course().getTitle();
        String instructorName = (eligibility.instructor() != null && eligibility.instructor().getDisplayName() != null)
                ? eligibility.instructor().getDisplayName() : "PeerGeek Faculty";

        CertificateEntity certificate = new CertificateEntity(
                number,
                verification,
                userId,
                courseId,
                enrollment.getId(),
                studentName,
                courseTitle,
                instructorName,
                Instant.now()
        );

        CertificateEntity saved = certificateRepository.save(certificate);
        generateAndStore(saved);
        return Optional.of(saved);
    }

    public void generateAndStore(CertificateEntity certificate) {
        String certNumber = certificate.getCertificateNumber();
        try {
            String verificationUrl = frontendBaseUrl.replaceAll("/+$", "") + "/verify/certificate/" + certificate.getVerificationCode();
            byte[] pdfBytes = pdfGenerator.generate(certificate, verificationUrl);

            String fileKey = "certificates/" + certificate.getUserId() + "/" + certNumber + ".pdf";
            storageService.putObject(fileKey, pdfBytes, "application/pdf");

            certificate.setFileKey(fileKey);
            certificate.setStatus("ISSUED");
            certificate.setFailureReason(null);
            certificate = certificateRepository.save(certificate);
            log.info("Certificate {} successfully issued and stored at {}", certNumber, fileKey);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("certificateId", certificate.getId());
            payload.put("certificateNumber", certificate.getCertificateNumber());
            payload.put("userId", certificate.getUserId());
            payload.put("courseId", certificate.getCourseId());
            payload.put("courseTitle", certificate.getCourseTitle());
            payload.put("issuedAt", certificate.getIssuedAt().toString());

            outboxEventWriter.writeEvent(
                    "peergeek.learning.certificate-issued.v1",
                    "CertificateIssued",
                    payload
            );
        } catch (Exception ex) {
            log.error("Failed to generate and store certificate {}: {}", certNumber, ex.getMessage(), ex);
            certificate.setStatus("FAILED");
            certificate.setFailureReason(ex.getMessage());
            certificate.setRetryCount(certificate.getRetryCount() + 1);
            certificateRepository.save(certificate);
        }
    }

    @Transactional(readOnly = true)
    public List<CertificateDtos.CertificateDto> getMyCertificates(Long userId) {
        List<CertificateEntity> certs = certificateRepository.findByUserIdOrderByIssuedAtDesc(userId);
        List<CertificateDtos.CertificateDto> dtos = new ArrayList<>();
        for (CertificateEntity c : certs) {
            String downloadUrl = null;
            if ("ISSUED".equalsIgnoreCase(c.getStatus()) && c.getFileKey() != null) {
                downloadUrl = storageService.presignGet(c.getFileKey(), Duration.ofHours(1));
            }
            dtos.add(CertificateDtos.CertificateDto.from(c, downloadUrl));
        }
        return dtos;
    }

    @Transactional
    public CertificateDtos.ReconciliationSummaryDto reconcileForUser(Long userId) {
        List<EnrollmentEntity> userEnrollments = enrollmentRepository.findByUserIdAndStatusOrderByEnrolledAtDesc(userId, EnrollmentStatus.ACTIVE);
        return reconcileEnrollmentList(userEnrollments);
    }

    @Transactional
    public CertificateDtos.CertificateDto retryForUser(Long certificateId, Long userId) {
        CertificateEntity certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new NotFoundException("Certificate not found: " + certificateId));

        if (!certificate.getUserId().equals(userId)) {
            throw new ForbiddenException("You cannot retry another user's certificate");
        }

        if ("ISSUED".equalsIgnoreCase(certificate.getStatus())) {
            String downloadUrl = storageService.presignGet(certificate.getFileKey(), Duration.ofHours(1));
            return CertificateDtos.CertificateDto.from(certificate, downloadUrl);
        }

        certificate.setStatus("PENDING");
        certificate = certificateRepository.save(certificate);
        generateAndStore(certificate);

        String downloadUrl = ("ISSUED".equalsIgnoreCase(certificate.getStatus()) && certificate.getFileKey() != null)
                ? storageService.presignGet(certificate.getFileKey(), Duration.ofHours(1)) : null;

        return CertificateDtos.CertificateDto.from(certificate, downloadUrl);
    }

    @Transactional(readOnly = true)
    public CertificateDtos.DownloadUrl getDownloadUrl(Long certificateId, Long userId) {
        CertificateEntity certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new NotFoundException("Certificate not found: " + certificateId));

        if (!certificate.getUserId().equals(userId)) {
            throw new ForbiddenException("You cannot access another user's certificate");
        }

        if (!"ISSUED".equalsIgnoreCase(certificate.getStatus()) || certificate.getFileKey() == null) {
            throw new ConflictException("Certificate is not ready for download (status: " + certificate.getStatus() + ")");
        }

        String url = storageService.presignGet(certificate.getFileKey(), Duration.ofHours(1));
        return new CertificateDtos.DownloadUrl(url);
    }

    @Transactional(readOnly = true)
    public CertificateDtos.CertificateVerificationDto verify(String verificationCode) {
        Optional<CertificateEntity> certOpt = certificateRepository.findByVerificationCode(verificationCode);
        if (certOpt.isEmpty()) {
            return new CertificateDtos.CertificateVerificationDto(
                    false, "NOT_FOUND", null, null, null, null, null, null, null
            );
        }

        CertificateEntity c = certOpt.get();
        if ("REVOKED".equalsIgnoreCase(c.getStatus())) {
            return new CertificateDtos.CertificateVerificationDto(
                    false, "REVOKED", c.getCertificateNumber(), c.getStudentName(),
                    c.getCourseTitle(), c.getInstructorName(), c.getIssuedAt(),
                    c.getRevokedAt(), c.getRevocationReason()
            );
        }

        boolean valid = "ISSUED".equalsIgnoreCase(c.getStatus());
        return new CertificateDtos.CertificateVerificationDto(
                valid, c.getStatus(), c.getCertificateNumber(), c.getStudentName(),
                c.getCourseTitle(), c.getInstructorName(), c.getIssuedAt()
        );
    }

    // --- Admin Operations ---

    @Transactional(readOnly = true)
    public PageResponse<CertificateDtos.AdminCertificateDto> getAdminCertificates(
            String status, String search, Long courseId, Long userId, Pageable pageable) {
        Page<CertificateEntity> page = certificateRepository.findAllByOrderByIssuedAtDesc(pageable);

        List<CertificateDtos.AdminCertificateDto> dtos = page.getContent().stream()
                .filter(c -> status == null || "ALL".equalsIgnoreCase(status) || c.getStatus().equalsIgnoreCase(status))
                .filter(c -> courseId == null || c.getCourseId().equals(courseId))
                .filter(c -> userId == null || c.getUserId().equals(userId))
                .filter(c -> {
                    if (search == null || search.isBlank()) return true;
                    String s = search.toLowerCase();
                    return c.getCertificateNumber().toLowerCase().contains(s)
                            || c.getStudentName().toLowerCase().contains(s)
                            || c.getCourseTitle().toLowerCase().contains(s);
                })
                .map(c -> {
                    String dl = null;
                    if ("ISSUED".equalsIgnoreCase(c.getStatus()) && c.getFileKey() != null) {
                        try {
                            dl = storageService.presignGet(c.getFileKey(), Duration.ofHours(1));
                        } catch (Exception ignored) {}
                    }
                    return CertificateDtos.AdminCertificateDto.from(c, dl);
                })
                .toList();

        return PageResponse.of(dtos, pageable.getPageNumber(), pageable.getPageSize(), (int) page.getTotalElements());
    }

    @Transactional
    public CertificateDtos.AdminCertificateDto revokeAdmin(Long id, String reason, Long adminUserId) {
        CertificateEntity c = certificateRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Certificate not found: " + id));

        c.setStatus("REVOKED");
        c.setRevokedAt(Instant.now());
        c.setRevocationReason(reason);
        c.setRevokedBy(adminUserId);
        c = certificateRepository.save(c);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("certificateId", c.getId());
        payload.put("certificateNumber", c.getCertificateNumber());
        payload.put("userId", c.getUserId());
        payload.put("revokedAt", c.getRevokedAt().toString());
        payload.put("reason", reason);

        outboxEventWriter.writeEvent(
                "peergeek.learning.certificate-revoked.v1",
                "CertificateRevoked",
                payload
        );

        return CertificateDtos.AdminCertificateDto.from(c, null);
    }

    @Transactional
    public CertificateDtos.AdminCertificateDto retryAdmin(Long id) {
        CertificateEntity c = certificateRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Certificate not found: " + id));

        c.setStatus("PENDING");
        c = certificateRepository.save(c);
        generateAndStore(c);

        String dl = ("ISSUED".equalsIgnoreCase(c.getStatus()) && c.getFileKey() != null)
                ? storageService.presignGet(c.getFileKey(), Duration.ofHours(1)) : null;

        return CertificateDtos.AdminCertificateDto.from(c, dl);
    }

    @Transactional
    public CertificateDtos.ReconciliationSummaryDto reconcileAdmin(Long courseId, Long userId) {
        List<EnrollmentEntity> enrollments;
        if (userId != null && courseId != null) {
            enrollments = enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE)
                    .map(List::of).orElse(List.of());
        } else if (userId != null) {
            enrollments = enrollmentRepository.findByUserIdAndStatusOrderByEnrolledAtDesc(userId, EnrollmentStatus.ACTIVE);
        } else {
            enrollments = enrollmentRepository.findAll().stream()
                    .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
                    .filter(e -> courseId == null || e.getCourseId().equals(courseId))
                    .toList();
        }
        return reconcileEnrollmentList(enrollments);
    }

    private CertificateDtos.ReconciliationSummaryDto reconcileEnrollmentList(List<EnrollmentEntity> enrollments) {
        int totalExamined = enrollments.size();
        int eligible = 0;
        int alreadyIssued = 0;
        int newlyIssued = 0;
        int failed = 0;
        int ineligible = 0;
        List<String> details = new ArrayList<>();

        for (EnrollmentEntity en : enrollments) {
            CertificateEligibilityResult res = eligibilityService.checkEligibility(en);
            if (res.eligible()) {
                eligible++;
                Optional<CertificateEntity> existing = certificateRepository.findByEnrollmentId(en.getId());
                if (existing.isPresent() && "ISSUED".equalsIgnoreCase(existing.get().getStatus())) {
                    alreadyIssued++;
                } else {
                    Optional<CertificateEntity> issued = issueIfEligible(res);
                    if (issued.isPresent() && "ISSUED".equalsIgnoreCase(issued.get().getStatus())) {
                        newlyIssued++;
                        details.add("Issued certificate for enrollment " + en.getId());
                    } else {
                        failed++;
                        details.add("Failed to issue certificate for enrollment " + en.getId());
                    }
                }
            } else {
                ineligible++;
            }
        }

        return new CertificateDtos.ReconciliationSummaryDto(
                totalExamined, eligible, alreadyIssued, newlyIssued, failed, ineligible, details
        );
    }
}

