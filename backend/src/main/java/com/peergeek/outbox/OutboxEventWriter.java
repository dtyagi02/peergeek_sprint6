package com.peergeek.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxEventWriter {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventWriter.class);
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxEventWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordEvent(String aggregateType, String aggregateId, String eventType, String topic, Map<String, Object> payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String id = UUID.randomUUID().toString();
            OutboxEventEntity entity = new OutboxEventEntity(id, aggregateType, aggregateId, eventType, topic, payloadJson, Instant.now());
            repository.save(entity);
            log.debug("Recorded outbox event {} for {} on topic {}", id, eventType, topic);
        } catch (Exception e) {
            log.error("Failed to record outbox event for {}: {}", eventType, e.getMessage(), e);
            throw new RuntimeException("Transactional outbox write failed", e);
        }
    }
}
