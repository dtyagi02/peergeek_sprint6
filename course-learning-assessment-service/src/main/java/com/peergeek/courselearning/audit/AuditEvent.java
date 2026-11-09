package com.peergeek.courselearning.audit;

import java.util.Map;

public record AuditEvent(
        Long actorUserId,
        String action,
        String entityType,
        String entityId,
        Map<String, Object> details) {
    public AuditEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
