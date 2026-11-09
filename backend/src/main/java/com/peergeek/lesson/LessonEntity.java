package com.peergeek.lesson;

import com.peergeek.common.BaseEntity;
import com.peergeek.module.ModuleEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "lessons")
public class LessonEntity extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "module_id", nullable = false)
	private ModuleEntity module;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(length = 500)
	private String summary;

	@Column(nullable = false)
	private int position;

	@OneToOne(mappedBy = "lesson", fetch = FetchType.LAZY)
	private LessonContentEntity content;

	protected LessonEntity() {}

	public LessonEntity(ModuleEntity module, String title, String summary, int position) {
		this.module = module;
		this.title = title;
		this.summary = summary;
		this.position = position;
	}

	public ModuleEntity getModule() { return module; }
	public String getTitle() { return title; }
	public String getSummary() { return summary; }
	public int getPosition() { return position; }
	public LessonContentEntity getContent() { return content; }
	public void update(String title, String summary) {
		this.title = title;
		this.summary = summary;
	}
	public void setPosition(int position) { this.position = position; }
	public void moveTo(ModuleEntity module, int position) {
		this.module = module;
		this.position = position;
	}
}
