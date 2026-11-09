package com.peergeek.lesson;

import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "lesson_contents")
public class LessonContentEntity {
	@Id
	@Column(name = "lesson_id")
	private Long lessonId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lesson_id", nullable = false)
	private LessonEntity lesson;

	@Column(name = "content_format", nullable = false, length = 32)
	private String contentFormat;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "content_document", nullable = false, columnDefinition = "json")
	private JsonNode document;

	@Column(name = "word_count", nullable = false)
	private int wordCount;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected LessonContentEntity() {}

	public LessonContentEntity(LessonEntity lesson, JsonNode document, int wordCount, Instant updatedAt) {
		this.lesson = lesson;
		this.contentFormat = "tiptap-json-v1";
		this.document = document.deepCopy();
		this.wordCount = wordCount;
		this.updatedAt = updatedAt;
	}

	public JsonNode getDocument() { return document; }
	public int getWordCount() { return wordCount; }
	public long getVersion() { return version; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void update(JsonNode document, int wordCount, Instant updatedAt) {
		this.document = document.deepCopy();
		this.wordCount = wordCount;
		this.updatedAt = updatedAt;
	}
}
