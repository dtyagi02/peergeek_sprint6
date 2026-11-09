package com.peergeek.course;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.peergeek.common.BaseEntity;
import com.peergeek.user.UserEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "courses")
public class CourseEntity extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private UserEntity owner;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private CategoryEntity category;

	@Enumerated(EnumType.STRING)
	@Column(length = 16)
	private CourseDifficulty difficulty;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private CourseStatus status = CourseStatus.DRAFT;

	@Column(name = "thumbnail_resource_id")
	private Long thumbnailResourceId;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "content_updated_at", nullable = false)
	private Instant contentUpdatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Version
	@Column(nullable = false)
	private long version;

	@OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	private List<CourseTopicEntity> topics = new ArrayList<>();

	protected CourseEntity() {}

	public CourseEntity(UserEntity owner, String title, String description, CategoryEntity category,
			CourseDifficulty difficulty, Instant now) {
		this.owner = owner;
		this.title = title;
		this.description = description;
		this.category = category;
		this.difficulty = difficulty;
		this.contentUpdatedAt = now;
	}

	public UserEntity getOwner() { return owner; }
	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public CategoryEntity getCategory() { return category; }
	public CourseDifficulty getDifficulty() { return difficulty; }
	public CourseStatus getStatus() { return status; }
	public Long getThumbnailResourceId() { return thumbnailResourceId; }
	public Instant getPublishedAt() { return publishedAt; }
	public Instant getContentUpdatedAt() { return contentUpdatedAt; }
	public Instant getDeletedAt() { return deletedAt; }
	public long getVersion() { return version; }
	public List<CourseTopicEntity> getTopics() { return topics; }

	public void updateDetails(String title, String description, CategoryEntity category,
			CourseDifficulty difficulty) {
		this.title = title;
		this.description = description;
		this.category = category;
		this.difficulty = difficulty;
	}

	public void replaceTopics(List<String> names) {
		topics.clear();
		for (int index = 0; index < names.size(); index++) {
			topics.add(new CourseTopicEntity(this, index, names.get(index)));
		}
	}

	public void markContentUpdated(Instant now) { contentUpdatedAt = now; }
	public void softDelete(Instant now) { deletedAt = now; }

}
