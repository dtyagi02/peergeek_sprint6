package com.peergeek.courselearning.audit;

import java.time.Instant;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuditRecordWriter {
    private final EntityManager entityManager;

    public AuditRecordWriter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void write(AuditEvent event, String ip, String userAgent, Instant createdAt) {
        entityManager.persist(new AuditLogEntity(
                event.actorUserId(), event.action(), event.entityType(), event.entityId(),
                event.details(), ip, userAgent, createdAt));
        entityManager.flush();
    }
}
