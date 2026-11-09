package com.peergeek.certificate;

import java.net.URI;
import java.util.List;
import com.peergeek.certificate.dto.CertificateDto;
import com.peergeek.certificate.dto.CertificateVerificationDto;
import com.peergeek.certificate.dto.ReconciliationSummaryDto;
import com.peergeek.security.CurrentUserProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/certificates")
public class CertificateController {
	private final CertificateService service;
	private final CurrentUserProvider current;

	public CertificateController(CertificateService service, CurrentUserProvider current) {
		this.service = service;
		this.current = current;
	}

	@GetMapping
	public List<CertificateDto> mine() {
		return service.listForUser(current.requireCurrentUser().id());
	}

	@PostMapping("/reconcile")
	public ReconciliationSummaryDto reconcile() {
		return service.reconcileForUser(current.requireCurrentUser().id());
	}

	@GetMapping("/{id}/download-url")
	public URI downloadUrl(@PathVariable Long id) {
		var principal = current.requireCurrentUser();
		return service.downloadUrl(principal.id(), id, principal.role().name().equals("ADMIN"));
	}

	@GetMapping("/{id}/download")
	public ResponseEntity<Resource> downloadStream(@PathVariable Long id) {
		var principal = current.requireCurrentUser();
		CertificateService.CertificateStream streamInfo = service.downloadStream(
				principal.id(), id, principal.role().name().equals("ADMIN"));

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + streamInfo.filename() + "\"")
				.contentLength(streamInfo.contentLength())
				.body(new InputStreamResource(streamInfo.stream()));
	}

	@PostMapping("/{id}/retry")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void retry(@PathVariable Long id) {
		var principal = current.requireCurrentUser();
		service.retry(principal.id(), id, principal.role().name().equals("ADMIN"));
	}

	@GetMapping("/verify/{verificationCode}")
	public CertificateVerificationDto verify(@PathVariable String verificationCode) {
		return service.verify(verificationCode);
	}
}
