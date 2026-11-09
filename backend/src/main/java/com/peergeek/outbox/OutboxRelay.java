package com.peergeek.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    public OutboxRelay(OutboxEventRepository repository, ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider) {
        this.repository = repository;
        this.kafkaTemplateProvider = kafkaTemplateProvider;
    }

    @Scheduled(fixedDelayString = "${peergeek.outbox.relay-interval-ms:3000}")
    @Transactional
    public void publishPendingEvents() {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            return;
        }

        List<OutboxEventEntity> pending = repository.findByPublishedFalseOrderByCreatedAtAsc(PageRequest.of(0, 50));
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events to relay to Kafka", pending.size());
        for (OutboxEventEntity event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload()).get(5, TimeUnit.SECONDS);
                event.markPublished(Instant.now());
                repository.save(event);
                log.debug("Relayed outbox event {} to topic {}", event.getId(), event.getTopic());
            } catch (Exception ex) {
                log.warn("Failed to relay outbox event {} to topic {}: {}", event.getId(), event.getTopic(), ex.getMessage());
            }
        }
    }
}
