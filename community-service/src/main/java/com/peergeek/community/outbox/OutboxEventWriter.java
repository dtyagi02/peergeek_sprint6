package com.peergeek.community.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class OutboxEventWriter {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventWriter.class);
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventWriter(OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void writeEvent(String topic, String eventType, Object payload) {
        String eventId = UUID.randomUUID().toString();
        writeEvent(topic, eventType, eventId, payload);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void writeEvent(String topic, String eventType, String eventId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutboxEventEntity entity = new OutboxEventEntity(eventId, eventType, topic, json);
            outboxRepository.save(entity);
            log.info("Persisted transactional community outbox event id={}, type={}, topic={}", eventId, eventType, topic);
        } catch (Exception ex) {
            log.error("Failed to persist transactional outbox event for topic={}: {}", topic, ex.getMessage(), ex);
            throw new RuntimeException("Transactional outbox write failed", ex);
        }
    }
}

