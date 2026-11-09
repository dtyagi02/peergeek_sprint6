package com.peergeek.certificate;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peergeek.certificate.dto.AdminCertificateDto;
import com.peergeek.certificate.dto.ReconciliationSummaryDto;
import com.peergeek.certificate.dto.RevokeCertificateRequest;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import com.peergeek.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCertificateControllerTest {

	@Mock
	private CertificateService certificateService;

	@Mock
	private CurrentUserProvider currentUserProvider;

	private MockMvc mockMvc;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		AdminCertificateController controller = new AdminCertificateController(certificateService, currentUserProvider);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	@DisplayName("GET /api/v1/admin/certificates: lists certificates with pagination")
	void listCertificates() throws Exception {
		AdminCertificateDto dto = new AdminCertificateDto(
				10L, "PG-ADMIN01", "vcode", 1L, "Student A", 5L, "Java 101",
				"Dr. Instructor", CertificateStatus.ISSUED, Instant.now(), null, null, null, null, 0, "/api/v1/certificates/10/download"
		);
		when(certificateService.listFiltered(any(), any(), any(), any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(dto)));

		mockMvc.perform(get("/api/v1/admin/certificates")
						.param("page", "0")
						.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].certificateNumber").value("PG-ADMIN01"))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	@DisplayName("POST /api/v1/admin/certificates/{id}/revoke: revokes certificate")
	void revokeCertificate() throws Exception {
		when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(999L, UserRole.ADMIN));
		CertificateEntity cert = new CertificateEntity("PG-REV01", "vcode", 1L, 5L, 50L, "Student", "Java", "Dr. Jane", Instant.now());
		cert.revoke("Plagiarism confirmed", 999L);
		when(certificateService.revoke(eq(10L), eq("Plagiarism confirmed"), eq(999L))).thenReturn(cert);

		RevokeCertificateRequest request = new RevokeCertificateRequest("Plagiarism confirmed");

		mockMvc.perform(post("/api/v1/admin/certificates/10/revoke")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVOKED"))
				.andExpect(jsonPath("$.revocationReason").value("Plagiarism confirmed"))
				.andExpect(jsonPath("$.revokedBy").value(999));
	}

	@Test
	@DisplayName("POST /api/v1/admin/certificates/{id}/retry: triggers admin retry")
	void retryCertificate() throws Exception {
		when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(999L, UserRole.ADMIN));

		mockMvc.perform(post("/api/v1/admin/certificates/10/retry"))
				.andExpect(status().isAccepted());

		verify(certificateService).retry(999L, 10L, true);
	}

	@Test
	@DisplayName("POST /api/v1/admin/certificates/reconcile: batch reconciliation")
	void reconcileBatch() throws Exception {
		ReconciliationSummaryDto summary = new ReconciliationSummaryDto(10, 8, 5, 3, 0, 2, List.of());
		when(certificateService.reconcileBatch(eq(5L), eq(1L))).thenReturn(summary);

		mockMvc.perform(post("/api/v1/admin/certificates/reconcile")
						.param("courseId", "5")
						.param("userId", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalExamined").value(10))
				.andExpect(jsonPath("$.newlyIssued").value(3));
	}
}

