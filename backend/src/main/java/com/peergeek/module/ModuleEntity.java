package com.peergeek.module;

import com.peergeek.common.BaseEntity;
import com.peergeek.course.CourseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_modules")
public class ModuleEntity extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "course_id", nullable = false)
	private CourseEntity course;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(length = 1000)
	private String description;

	@Column(nullable = false)
	private int position;

	protected ModuleEntity() {}

	public ModuleEntity(CourseEntity course, String title, String description, int position) {
		this.course = course;
		this.title = title;
		this.description = description;
		this.position = position;
	}

	public CourseEntity getCourse() { return course; }
	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public int getPosition() { return position; }
	public void update(String title, String description) {
		this.title = title;
		this.description = description;
	}
	public void setPosition(int position) { this.position = position; }
}
