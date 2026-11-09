package com.peergeek.certificate;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.peergeek.certificate.dto.CertificateDto;
import com.peergeek.certificate.dto.CertificateVerificationDto;
import com.peergeek.certificate.dto.ReconciliationSummaryDto;
import com.peergeek.course.CourseDifficulty;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStatus;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.enrollment.EnrollmentEntity;
import com.peergeek.enrollment.EnrollmentRepository;
import com.peergeek.enrollment.EnrollmentStatus;
import com.peergeek.exception.ConflictException;
import com.peergeek.exception.ForbiddenException;
import com.peergeek.exception.NotFoundException;
import com.peergeek.file.ObjectStorageService;
import com.peergeek.notification.NotificationService;
import com.peergeek.user.UserEntity;
import com.peergeek.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

	@Mock
	private CertificateRepository certificates;

	@Mock
	private EnrollmentRepository enrollments;

	@Mock
	private CertificateEligibilityService eligibilityService;

	@Mock
	private CourseStructureQueryService courses;

	@Mock
	private ObjectStorageService storage;

	@Mock
	private CertificatePdfGenerator pdf;

	@Mock
	private NotificationService notifications;

	private CertificateService service;

	private final Long userId = 1L;
	private final Long courseId = 10L;
	private final Long enrollmentId = 100L;
	private EnrollmentEntity enrollment;
	private UserEntity student;
	private UserEntity instructor;
	private CourseMetadataSnapshot course;

	@BeforeEach
	void setUp() {
		service = new CertificateService(
				certificates, enrollments, eligibilityService, courses,
				storage, pdf, notifications, "http://localhost:5173"
		);

		enrollment = new EnrollmentEntity(userId, courseId, Instant.now());
		ReflectionTestUtils.setField(enrollment, "id", enrollmentId);

		student = new UserEntity("student@test.com", "hash", "Student One");
		ReflectionTestUtils.setField(student, "id", userId);

		instructor = new UserEntity("instructor@test.com", "hash", "Instructor One");
		ReflectionTestUtils.setField(instructor, "id", 2L);

		course = new CourseMetadataSnapshot(
				courseId, 2L, CourseStatus.PUBLISHED, false, 1L,
				"Full-Stack Web Dev", "Summary", 1L, "Web Dev", true,
				CourseDifficulty.INTERMEDIATE, List.of(), null
		);
	}

	@Test
	@DisplayName("issueIfEligible returns empty when student is not eligible")
	void issueIfEligibleWhenNotEligible() {
		when(eligibilityService.checkEligibility(userId, courseId))
				.thenReturn(CertificateEligibilityResult.ineligible("Progress below 100%"));

		Optional<CertificateEntity> result = service.issueIfEligible(userId, courseId);
		assertThat(result).isEmpty();
		verify(certificates, never()).save(any());
	}

	@Test
	@DisplayName("issueIfEligible issues and stores PDF when eligible and no existing certificate")
	void issueIfEligibleSuccess() {
		when(eligibilityService.checkEligibility(userId, courseId))
				.thenReturn(CertificateEligibilityResult.eligible(enrollment, course, student, instructor));
		when(certificates.findByEnrollmentId(enrollmentId)).thenReturn(Optional.empty());

		byte[] fakePdf = new byte[]{1, 2, 3, 4};
		when(pdf.generate(any(), anyString())).thenReturn(fakePdf);

		when(certificates.save(any(CertificateEntity.class))).thenAnswer(invocation -> {
			CertificateEntity c = invocation.getArgument(0);
			if (c.getId() == null) {
				ReflectionTestUtils.setField(c, "id", 501L);
			}
			return c;
		});

		Optional<CertificateEntity> result = service.issueIfEligible(userId, courseId);

		assertThat(result).isPresent();
		CertificateEntity cert = result.get();
		assertThat(cert.getStatus()).isEqualTo(CertificateStatus.ISSUED);
		assertThat(cert.getFileKey()).isEqualTo("certificates/" + userId + "/" + cert.getCertificateNumber() + ".pdf");
		verify(storage).putObject(eq(cert.getFileKey()), eq("application/pdf"), eq((long) fakePdf.length), any(InputStream.class));
		verify(notifications).createNotification(eq(userId), any(), anyString(), anyString(), eq("CERTIFICATE"), eq(501L), anyString(), anyString());
	}

	@Test
	@DisplayName("issueIfEligible is idempotent: does not duplicate if already ISSUED")
	void issueIfEligibleAlreadyIssued() {
		when(eligibilityService.checkEligibility(userId, courseId))
				.thenReturn(CertificateEligibilityResult.eligible(enrollment, course, student, instructor));

		CertificateEntity existing = new CertificateEntity("PG-EXISTING", "vcode", userId, courseId, enrollmentId, "Student", "Course", "Inst", Instant.now());
		existing.issue("certificates/1/PG-EXISTING.pdf");
		when(certificates.findByEnrollmentId(enrollmentId)).thenReturn(Optional.of(existing));

		Optional<CertificateEntity> result = service.issueIfEligible(userId, courseId);
		assertThat(result).isPresent();
		assertThat(result.get().getStatus()).isEqualTo(CertificateStatus.ISSUED);
		verify(pdf, never()).generate(any(), any());
	}

	@Test
	@DisplayName("issueIfEligible will NEVER reissue a REVOKED certificate")
	void issueIfEligibleNeverReissuesRevoked() {
		when(eligibilityService.checkEligibility(userId, courseId))
				.thenReturn(CertificateEligibilityResult.eligible(enrollment, course, student, instructor));

		CertificateEntity existing = new CertificateEntity("PG-REVOKED", "vcode", userId, courseId, enrollmentId, "Student", "Course", "Inst", Instant.now());
		existing.revoke("Cheating", 99L);
		when(certificates.findByEnrollmentId(enrollmentId)).thenReturn(Optional.of(existing));

		Optional<CertificateEntity> result = service.issueIfEligible(userId, courseId);
		assertThat(result).isPresent();
		assertThat(result.get().getStatus()).isEqualTo(CertificateStatus.REVOKED);
		verify(pdf, never()).generate(any(), any());
	}

	@Test
	@DisplayName("generateAndStore handles failure gracefully by marking certificate FAILED")
	void generateAndStoreFailure() {
		CertificateEntity cert = new CertificateEntity("PG-FAIL01", "vcode", userId, courseId, enrollmentId, "Student", "Course", "Inst", Instant.now());
		when(pdf.generate(any(), any())).thenThrow(new RuntimeException("Font corrupt"));

		service.generateAndStore(cert);

		assertThat(cert.getStatus()).isEqualTo(CertificateStatus.FAILED);
		assertThat(cert.getFailureReason()).contains("Font corrupt");
		assertThat(cert.getRetryCount()).isEqualTo(1);
		verify(certificates).save(cert);
	}

	@Test
	@DisplayName("reconcileForUser checks all active enrollments and returns detailed summary")
	void reconcileForUserSuccess() {
		when(enrollments.findByUserIdAndStatus(userId, EnrollmentStatus.ACTIVE)).thenReturn(List.of(enrollment));
		when(eligibilityService.checkEligibility(enrollment))
				.thenReturn(CertificateEligibilityResult.eligible(enrollment, course, student, instructor));
		when(certificates.findByEnrollmentId(enrollmentId)).thenReturn(Optional.empty());
		when(pdf.generate(any(), any())).thenReturn(new byte[]{10, 20});
		when(certificates.save(any(CertificateEntity.class))).thenAnswer(i -> i.getArgument(0));

		ReconciliationSummaryDto summary = service.reconcileForUser(userId);

		assertThat(summary.totalExamined()).isEqualTo(1);
		assertThat(summary.eligible()).isEqualTo(1);
		assertThat(summary.newlyIssued()).isEqualTo(1);
		assertThat(summary.failed()).isEqualTo(0);
		assertThat(summary.details()).hasSize(1);
		assertThat(summary.details().get(0)).contains("[CERTIFICATE_GENERATED]");
	}

	@Test
	@DisplayName("reconcileForUser records specific ineligible rejection reasons and already issued status")
	void reconcileForUserWithIneligibleAndAlreadyIssued() {
		EnrollmentEntity ineligibleEnrollment = new EnrollmentEntity(userId, 101L, Instant.now());
		ReflectionTestUtils.setField(ineligibleEnrollment, "id", 2L);

		when(enrollments.findByUserIdAndStatus(userId, EnrollmentStatus.ACTIVE))
				.thenReturn(List.of(enrollment, ineligibleEnrollment));

		when(eligibilityService.checkEligibility(enrollment))
				.thenReturn(CertificateEligibilityResult.eligible(enrollment, course, student, instructor));
		CertificateEntity existing = new CertificateEntity("PG-EXISTING", "vcode", userId, courseId, enrollmentId, "Student", "Course", "Inst", Instant.now());
		existing.issue("certificates/1/PG-EXISTING.pdf");
		when(certificates.findByEnrollmentId(enrollmentId)).thenReturn(Optional.of(existing));

		when(eligibilityService.checkEligibility(ineligibleEnrollment))
				.thenReturn(CertificateEligibilityResult.ineligible("Lessons incomplete (5/10, 50%)"));

		ReconciliationSummaryDto summary = service.reconcileForUser(userId);

		assertThat(summary.totalExamined()).isEqualTo(2);
		assertThat(summary.eligible()).isEqualTo(1);
		assertThat(summary.alreadyIssued()).isEqualTo(1);
		assertThat(summary.ineligible()).isEqualTo(1);
		assertThat(summary.details()).hasSize(2);
		assertThat(summary.details().get(0)).contains("[CERTIFICATE_ALREADY_ISSUED]");
		assertThat(summary.details().get(1)).contains("[MISSING_REQUIRED_LESSONS]");
	}

	@Test
	@DisplayName("revoke updates status to REVOKED with reason and adminId, and throws if already revoked")
	void revokeCertificate() {
		CertificateEntity cert = new CertificateEntity("PG-TOREVOKE", "vcode", userId, courseId, enrollmentId, "Student", "Course", "Inst", Instant.now());
		cert.issue("some-key");
		ReflectionTestUtils.setField(cert, "id", 101L);
		when(certificates.findById(101L)).thenReturn(Optional.of(cert));
		when(certificates.save(any(CertificateEntity.class))).thenAnswer(i -> i.getArgument(0));

		CertificateEntity revoked = service.revoke(101L, "Academic dishonesty", 999L);
		assertThat(revoked.getStatus()).isEqualTo(CertificateStatus.REVOKED);
		assertThat(revoked.getRevocationReason()).isEqualTo("Academic dishonesty");
		assertThat(revoked.getRevokedBy()).isEqualTo(999L);

		// Second revoke attempt fails
		assertThatThrownBy(() -> service.revoke(101L, "Again", 999L))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("already revoked");
	}

	@Test
	@DisplayName("retry throws ConflictException if certificate is REVOKED")
	void retryRevokedThrowsConflict() {
		CertificateEntity cert = new CertificateEntity("PG-REVOKED", "vcode", userId, courseId, enrollmentId, "Student", "Course", "Inst", Instant.now());
		cert.revoke("Reason", 999L);
		ReflectionTestUtils.setField(cert, "id", 102L);
		when(certificates.findById(102L)).thenReturn(Optional.of(cert));

		assertThatThrownBy(() -> service.retry(userId, 102L, false))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("Revoked certificates cannot be retried");
	}

	@Test
	@DisplayName("verify returns valid=true for ISSUED certificate and valid=false for REVOKED")
	void verifyStatus() {
		CertificateEntity issued = new CertificateEntity("PG-ISS01", "token-issued", userId, courseId, enrollmentId, "Devansh", "Java Course", "Dr. Jane", Instant.now());
		issued.issue("key");
		when(certificates.findByVerificationCode("token-issued")).thenReturn(Optional.of(issued));

		CertificateVerificationDto resultIssued = service.verify("token-issued");
		assertThat(resultIssued.valid()).isTrue();
		assertThat(resultIssued.status()).isEqualTo(CertificateStatus.ISSUED);
		assertThat(resultIssued.studentName()).isEqualTo("Devansh");

		CertificateEntity revoked = new CertificateEntity("PG-REV01", "token-revoked", userId, courseId, enrollmentId, "Devansh", "Java Course", "Dr. Jane", Instant.now());
		revoked.revoke("Plagiarism detected", 999L);
		when(certificates.findByVerificationCode("token-revoked")).thenReturn(Optional.of(revoked));

		CertificateVerificationDto resultRevoked = service.verify("token-revoked");
		assertThat(resultRevoked.valid()).isFalse();
		assertThat(resultRevoked.status()).isEqualTo(CertificateStatus.REVOKED);
		assertThat(resultRevoked.revocationReason()).isEqualTo("Plagiarism detected");
	}
}
