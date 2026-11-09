package com.peergeek.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_projects")
public class UserProjectEntity extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(length = 500)
	private String technologies;

	@Column(name = "project_url", length = 500)
	private String projectUrl;

	@Column(name = "repo_url", length = 500)
	private String repoUrl;

	public UserProjectEntity() {
	}

	public UserProjectEntity(Long userId, String title, String description, String technologies, String projectUrl, String repoUrl) {
		this.userId = userId;
		this.title = title;
		this.description = description;
		this.technologies = technologies;
		this.projectUrl = projectUrl;
		this.repoUrl = repoUrl;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getTechnologies() {
		return technologies;
	}

	public void setTechnologies(String technologies) {
		this.technologies = technologies;
	}

	public String getProjectUrl() {
		return projectUrl;
	}

	public void setProjectUrl(String projectUrl) {
		this.projectUrl = projectUrl;
	}

	public String getRepoUrl() {
		return repoUrl;
	}

	public void setRepoUrl(String repoUrl) {
		this.repoUrl = repoUrl;
	}
}

