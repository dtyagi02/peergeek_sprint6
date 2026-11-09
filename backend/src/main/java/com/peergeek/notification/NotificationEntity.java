package com.peergeek.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 64)
	private NotificationType type;

	@Column(name = "title", nullable = false, length = 255)
	private String title;

	@Column(name = "message", nullable = false, length = 1000)
	private String message;

	@Column(name = "resource_type", length = 64)
	private String resourceType;

	@Column(name = "resource_id")
	private Long resourceId;

	@Column(name = "action_url", length = 500)
	private String actionUrl;

	@Column(name = "is_read", nullable = false)
	private boolean isRead;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "read_at")
	private Instant readAt;

	@Column(name = "event_key", length = 128)
	private String eventKey;

	protected NotificationEntity() {}

	public NotificationEntity(
			Long userId,
			NotificationType type,
			String title,
			String message,
			String resourceType,
			Long resourceId,
			String actionUrl,
			Instant createdAt,
			String eventKey) {
		this.userId = userId;
		this.type = type;
		this.title = title;
		this.message = message;
		this.resourceType = resourceType;
		this.resourceId = resourceId;
		this.actionUrl = actionUrl;
		this.isRead = false;
		this.createdAt = createdAt;
		this.eventKey = eventKey;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public NotificationType getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getMessage() {
		return message;
	}

	public String getResourceType() {
		return resourceType;
	}

	public Long getResourceId() {
		return resourceId;
	}

	public String getActionUrl() {
		return actionUrl;
	}

	public boolean isRead() {
		return isRead;
	}

	public void setRead(boolean read) {
		isRead = read;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getReadAt() {
		return readAt;
	}

	public void setReadAt(Instant readAt) {
		this.readAt = readAt;
	}

	public String getEventKey() {
		return eventKey;
	}
}
