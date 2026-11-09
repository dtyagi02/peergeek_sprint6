package com.peergeek.courselearning.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void relayEvents() {
        List<OutboxEventEntity> pendingEvents = outboxRepository.findUnpublishedEvents(PageRequest.of(0, 50));
        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEventEntity event : pendingEvents) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventId(), event.getPayload()).get();
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                event.setErrorMessage(null);
                outboxRepository.save(event);
                log.info("Relayed course-learning outbox event id={} to topic={}", event.getEventId(), event.getTopic());
            } catch (Exception ex) {
                event.incrementRetryCount();
                event.setErrorMessage(ex.getMessage());
                outboxRepository.save(event);
                log.warn("Failed to relay outbox event id={}, attempt={}: {}", event.getEventId(), event.getRetryCount(), ex.getMessage());
                break;
            }
        }
    }
}

