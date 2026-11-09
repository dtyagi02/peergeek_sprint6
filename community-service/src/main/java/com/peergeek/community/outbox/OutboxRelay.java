package com.peergeek.community.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> pendingEvents = outboxRepository.findPendingEvents(PageRequest.of(0, 50));
        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEventEntity event : pendingEvents) {
            try {
                // Synchronously await broker confirmation with 5s timeout
                kafkaTemplate.send(event.getTopic(), event.getEventId(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);

                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                event.setErrorMessage(null);
                log.info("Relayed community outbox event id={} to topic={}", event.getEventId(), event.getTopic());
            } catch (Exception ex) {
                event.setRetryCount(event.getRetryCount() + 1);
                event.setErrorMessage(ex.getMessage());
                log.warn("Failed to relay outbox event id={} to topic={} (retry={}): {}",
                        event.getEventId(), event.getTopic(), event.getRetryCount(), ex.getMessage());
            }
        }
        outboxRepository.saveAll(pendingEvents);
    }
}

