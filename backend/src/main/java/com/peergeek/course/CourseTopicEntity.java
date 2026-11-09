package com.peergeek.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_topics")
public class CourseTopicEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "course_id", nullable = false)
	private CourseEntity course;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false, length = 40)
	private String name;

	protected CourseTopicEntity() {}

	CourseTopicEntity(CourseEntity course, int position, String name) {
		this.course = course;
		this.position = position;
		this.name = name;
	}

	public Long getId() { return id; }
	public int getPosition() { return position; }
	public String getName() { return name; }
}
