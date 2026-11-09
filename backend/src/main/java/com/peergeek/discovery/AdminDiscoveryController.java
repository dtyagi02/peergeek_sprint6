package com.peergeek.discovery;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import com.peergeek.security.CurrentUserProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/discovery")
public class AdminDiscoveryController {
	private final DiscoveryReconciliationService reconciliationService;
	private final CurrentUserProvider currentUserProvider;
	private final AuditService auditService;

	public AdminDiscoveryController(
			DiscoveryReconciliationService reconciliationService,
			CurrentUserProvider currentUserProvider,
			AuditService auditService) {
		this.reconciliationService = reconciliationService;
		this.currentUserProvider = currentUserProvider;
		this.auditService = auditService;
	}

	@PostMapping("/reindex")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> reindex() {
		long actorId = currentUserProvider.requireCurrentUser().id();
		auditService.record(new AuditEvent(
				actorId,
				DiscoveryAuditActions.DISCOVERY_REINDEX_REQUESTED,
				"DISCOVERY",
				"GLOBAL",
				Map.of("timestamp", Instant.now().toString())));

		CompletableFuture.runAsync(reconciliationService::reconcileNow);
		return ResponseEntity.accepted().build();
	}
}
