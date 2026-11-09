package com.peergeek.file;

import com.peergeek.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "lesson_expected_resources",
		uniqueConstraints = @UniqueConstraint(name = "uk_expected_resources_lesson_type",
				columnNames = {"lesson_id", "resource_type"}))
public class LessonExpectedResourceEntity extends BaseEntity {
	@Column(name = "lesson_id", nullable = false)
	private Long lessonId;

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", nullable = false, length = 16)
	private ResourceType resourceType;

	@Column(name = "min_count", nullable = false)
	private int minCount;

	@Column(length = 80)
	private String label;

	protected LessonExpectedResourceEntity() {
	}

	public LessonExpectedResourceEntity(long lessonId, ResourceType resourceType, int minCount, String label) {
		this.lessonId = lessonId;
		this.resourceType = resourceType;
		this.minCount = minCount;
		this.label = label;
	}

	public Long getLessonId() { return lessonId; }
	public ResourceType getResourceType() { return resourceType; }
	public int getMinCount() { return minCount; }
	public String getLabel() { return label; }
}
