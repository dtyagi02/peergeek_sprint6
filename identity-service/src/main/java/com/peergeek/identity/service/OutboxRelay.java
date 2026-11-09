package com.peergeek.identity.service;

import java.time.LocalDateTime;
import java.util.List;
import com.peergeek.identity.entity.OutboxEventEntity;
import com.peergeek.identity.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelay {
	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
	private final OutboxEventRepository outboxRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;

	public OutboxRelay(OutboxEventRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
		this.outboxRepository = outboxRepository;
		this.kafkaTemplate = kafkaTemplate;
	}

	@Scheduled(fixedDelayString = "${peergeek.outbox.relay-interval-ms:3000}")
	@Transactional
	public void relayEvents() {
		List<OutboxEventEntity> pendingEvents = outboxRepository.findPendingEvents(PageRequest.of(0, 50));
		if (pendingEvents.isEmpty()) {
			return;
		}

		for (OutboxEventEntity event : pendingEvents) {
			try {
				kafkaTemplate.send(event.getTopic(), event.getEventId(), event.getPayload()).get();
				event.setPublished(true);
				event.setPublishedAt(LocalDateTime.now());
				event.setErrorMessage(null);
				outboxRepository.save(event);
				log.info("Relayed identity outbox event id={} to topic={}", event.getEventId(), event.getTopic());
			} catch (Exception ex) {
				event.setRetryCount(event.getRetryCount() + 1);
				event.setErrorMessage(ex.getMessage());
				outboxRepository.save(event);
				log.warn("Failed to relay identity outbox event id={}, attempt={}: {}", event.getEventId(), event.getRetryCount(), ex.getMessage());
				break;
			}
		}
	}
}

