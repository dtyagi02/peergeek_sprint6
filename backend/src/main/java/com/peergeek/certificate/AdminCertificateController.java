package com.peergeek.certificate;

import com.peergeek.certificate.dto.AdminCertificateDto;
import com.peergeek.certificate.dto.ReconciliationSummaryDto;
import com.peergeek.certificate.dto.RevokeCertificateRequest;
import com.peergeek.common.PageResponse;
import com.peergeek.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/certificates")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCertificateController {

	private final CertificateService certificateService;
	private final CurrentUserProvider currentUserProvider;

	public AdminCertificateController(CertificateService certificateService, CurrentUserProvider currentUserProvider) {
		this.certificateService = certificateService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping
	public ResponseEntity<PageResponse<AdminCertificateDto>> list(
			@RequestParam(required = false) CertificateStatus status,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Long courseId,
			@RequestParam(required = false) Long userId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "issuedAt"));
		Page<AdminCertificateDto> result = certificateService.listFiltered(status, search, courseId, userId, pageable);
		return ResponseEntity.ok(PageResponse.from(result));
	}

	@PostMapping("/{id}/revoke")
	public ResponseEntity<AdminCertificateDto> revoke(
			@PathVariable Long id,
			@Valid @RequestBody RevokeCertificateRequest request) {
		var admin = currentUserProvider.requireCurrentUser();
		CertificateEntity revoked = certificateService.revoke(id, request.reason(), admin.id());
		return ResponseEntity.ok(AdminCertificateDto.from(revoked, null));
	}

	@PostMapping("/{id}/retry")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void retry(@PathVariable Long id) {
		var admin = currentUserProvider.requireCurrentUser();
		certificateService.retry(admin.id(), id, true);
	}

	@PostMapping("/reconcile")
	public ResponseEntity<ReconciliationSummaryDto> reconcileBatch(
			@RequestParam(required = false) Long courseId,
			@RequestParam(required = false) Long userId) {
		ReconciliationSummaryDto summary = certificateService.reconcileBatch(courseId, userId);
		return ResponseEntity.ok(summary);
	}
}

