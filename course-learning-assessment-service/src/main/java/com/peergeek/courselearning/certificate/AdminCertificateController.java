package com.peergeek.courselearning.certificate;

import com.peergeek.courselearning.common.PageResponse;
import com.peergeek.courselearning.security.CurrentUser;
import com.peergeek.courselearning.security.CurrentUserProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/certificates")
public class AdminCertificateController {

    private final CertificateService certificateService;
    private final CurrentUserProvider currentUserProvider;

    public AdminCertificateController(CertificateService certificateService, CurrentUserProvider currentUserProvider) {
        this.certificateService = certificateService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<PageResponse<CertificateDtos.AdminCertificateDto>> getAdminCertificates(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "courseId", required = false) Long courseId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(certificateService.getAdminCertificates(status, search, courseId, userId, pageable));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<CertificateDtos.AdminCertificateDto> revokeAdminCertificate(
            @PathVariable("id") Long id,
            @RequestBody CertificateDtos.RevokeCertificateRequest request) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        return ResponseEntity.ok(certificateService.revokeAdmin(id, request.reason(), user.id()));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<CertificateDtos.AdminCertificateDto> retryAdminCertificate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(certificateService.retryAdmin(id));
    }

    @PostMapping("/reconcile")
    public ResponseEntity<CertificateDtos.ReconciliationSummaryDto> reconcileAdminCertificates(
            @RequestParam(value = "courseId", required = false) Long courseId,
            @RequestParam(value = "userId", required = false) Long userId) {
        return ResponseEntity.ok(certificateService.reconcileAdmin(courseId, userId));
    }
}

