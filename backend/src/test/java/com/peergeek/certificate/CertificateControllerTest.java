package com.peergeek.certificate;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import com.peergeek.certificate.dto.CertificateDto;
import com.peergeek.certificate.dto.CertificateVerificationDto;
import com.peergeek.certificate.dto.ReconciliationSummaryDto;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import com.peergeek.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CertificateControllerTest {

	@Mock
	private CertificateService service;

	@Mock
	private CurrentUserProvider currentUserProvider;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		CertificateController controller = new CertificateController(service, currentUserProvider);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	@DisplayName("GET /api/v1/certificates: returns student certificates")
	void listMine() throws Exception {
		when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(42L, UserRole.USER));
		CertificateDto dto = new CertificateDto(
				1L, 10L, "PG-ABC", "vcode", "Student", "Course 101", "Dr. Jane",
				Instant.now(), CertificateStatus.ISSUED, "/api/v1/certificates/1/download-url"
		);
		when(service.listForUser(42L)).thenReturn(List.of(dto));

		mockMvc.perform(get("/api/v1/certificates"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].certificateNumber").value("PG-ABC"))
				.andExpect(jsonPath("$[0].status").value("ISSUED"));
	}

	@Test
	@DisplayName("POST /api/v1/certificates/reconcile: returns reconciliation summary")
	void reconcileMine() throws Exception {
		when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(42L, UserRole.USER));
		ReconciliationSummaryDto summary = new ReconciliationSummaryDto(2, 2, 1, 1, 0, 0, List.of());
		when(service.reconcileForUser(42L)).thenReturn(summary);

		mockMvc.perform(post("/api/v1/certificates/reconcile"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalExamined").value(2))
				.andExpect(jsonPath("$.newlyIssued").value(1));
	}

	@Test
	@DisplayName("GET /api/v1/certificates/{id}/download: streams PDF binary")
	void downloadStream() throws Exception {
		when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(42L, UserRole.USER));
		byte[] pdfBytes = "%PDF-1.4 test".getBytes();
		CertificateService.CertificateStream stream = new CertificateService.CertificateStream(
				new ByteArrayInputStream(pdfBytes), "PG-ABC.pdf", pdfBytes.length);
		when(service.downloadStream(42L, 1L, false)).thenReturn(stream);

		mockMvc.perform(get("/api/v1/certificates/1/download"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"PG-ABC.pdf\""))
				.andExpect(content().bytes(pdfBytes));
	}

	@Test
	@DisplayName("GET /api/v1/certificates/{id}/download-url: returns presigned URI")
	void downloadUrl() throws Exception {
		when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(42L, UserRole.USER));
		when(service.downloadUrl(42L, 1L, false)).thenReturn(URI.create("https://s3.example.com/cert.pdf"));

		mockMvc.perform(get("/api/v1/certificates/1/download-url"))
				.andExpect(status().isOk())
				.andExpect(content().string("\"https://s3.example.com/cert.pdf\""));
	}

	@Test
	@DisplayName("POST /api/v1/certificates/{id}/retry: triggers retry")
	void retry() throws Exception {
		when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(42L, UserRole.USER));

		mockMvc.perform(post("/api/v1/certificates/1/retry"))
				.andExpect(status().isAccepted());

		verify(service).retry(42L, 1L, false);
	}

	@Test
	@DisplayName("GET /api/v1/certificates/verify/{verificationCode}: public verification")
	void verifyCertificate() throws Exception {
		CertificateVerificationDto dto = new CertificateVerificationDto(
				true, CertificateStatus.ISSUED, "PG-ABC", "Student Name", "Course Title", "Instructor", Instant.now()
		);
		when(service.verify("token123")).thenReturn(dto);

		mockMvc.perform(get("/api/v1/certificates/verify/token123"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.valid").value(true))
				.andExpect(jsonPath("$.certificateNumber").value("PG-ABC"))
				.andExpect(jsonPath("$.studentName").value("Student Name"));
	}
}

