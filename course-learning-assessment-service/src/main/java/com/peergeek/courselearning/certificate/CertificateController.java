package com.peergeek.courselearning.certificate;

import com.peergeek.courselearning.security.CurrentUser;
import com.peergeek.courselearning.security.CurrentUserProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/certificates")
public class CertificateController {

    private final CertificateService certificateService;
    private final CurrentUserProvider currentUserProvider;

    public CertificateController(CertificateService certificateService, CurrentUserProvider currentUserProvider) {
        this.certificateService = certificateService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<List<CertificateDtos.CertificateDto>> getMyCertificates() {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        return ResponseEntity.ok(certificateService.getMyCertificates(user.id()));
    }

    @PostMapping("/reconcile")
    public ResponseEntity<CertificateDtos.ReconciliationSummaryDto> reconcileCertificates() {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        return ResponseEntity.ok(certificateService.reconcileForUser(user.id()));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<CertificateDtos.CertificateDto> retryCertificate(@PathVariable("id") Long id) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        return ResponseEntity.ok(certificateService.retryForUser(id, user.id()));
    }

    @GetMapping("/{id}/download-url")
    public ResponseEntity<CertificateDtos.DownloadUrl> getCertificateDownloadUrl(@PathVariable("id") Long id) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        return ResponseEntity.ok(certificateService.getDownloadUrl(id, user.id()));
    }

    @GetMapping("/verify/{code}")
    public ResponseEntity<CertificateDtos.CertificateVerificationDto> verifyCertificate(@PathVariable("code") String code) {
        return ResponseEntity.ok(certificateService.verify(code));
    }
}

