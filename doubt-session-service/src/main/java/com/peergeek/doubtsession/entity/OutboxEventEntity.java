package com.peergeek.doubtsession.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, unique = true, length = 64)
	private String eventId;

	@Column(name = "event_type", nullable = false, length = 128)
	private String eventType;

	@Column(nullable = false, length = 128)
	private String topic;

	@Column(nullable = false, columnDefinition = "JSON")
	private String payload;

	@Column(nullable = false)
	private boolean published;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	public OutboxEventEntity() {
	}

	public OutboxEventEntity(String eventId, String eventType, String topic, String payload) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.topic = topic;
		this.payload = payload;
		this.published = false;
		this.retryCount = 0;
		this.createdAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getEventId() {
		return eventId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getTopic() {
		return topic;
	}

	public String getPayload() {
		return payload;
	}

	public boolean isPublished() {
		return published;
	}

	public void setPublished(boolean published) {
		this.published = published;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getPublishedAt() {
		return publishedAt;
	}

	public void setPublishedAt(LocalDateTime publishedAt) {
		this.publishedAt = publishedAt;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
}

