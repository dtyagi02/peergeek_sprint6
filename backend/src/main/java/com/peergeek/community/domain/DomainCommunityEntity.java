package com.peergeek.community.domain;

import com.peergeek.common.BaseEntity;
import com.peergeek.course.CategoryEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "domain_communities")
public class DomainCommunityEntity extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false, unique = true)
	private CategoryEntity category;

	@Column(length = 500)
	private String description;

	protected DomainCommunityEntity() {
	}

	public DomainCommunityEntity(CategoryEntity category, String description) {
		this.category = category;
		this.description = description;
	}

	public CategoryEntity getCategory() {
		return category;
	}

	public void setCategory(CategoryEntity category) {
		this.category = category;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
