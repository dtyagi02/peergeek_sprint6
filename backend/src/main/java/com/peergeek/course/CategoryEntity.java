package com.peergeek.course;

import com.peergeek.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class CategoryEntity extends BaseEntity {
	@Column(nullable = false, unique = true, length = 80)
	private String name;

	@Column(nullable = false, unique = true, length = 100)
	private String slug;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(nullable = false)
	private boolean active = true;

	protected CategoryEntity() {}

	public String getName() { return name; }
	public String getSlug() { return slug; }
	public int getSortOrder() { return sortOrder; }
	public boolean isActive() { return active; }
}
