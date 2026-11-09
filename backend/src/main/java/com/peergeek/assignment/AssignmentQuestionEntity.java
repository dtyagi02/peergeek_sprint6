package com.peergeek.assignment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "assignment_questions")
public class AssignmentQuestionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "assignment_id", nullable = false)
	private AssignmentEntity assignment;

	@Column(name = "position", nullable = false)
	private int position;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 32)
	private QuestionType type;

	@Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
	private String prompt;

	@Column(name = "points", nullable = false, precision = 7, scale = 2)
	private BigDecimal points;

	@Convert(converter = QuestionOptionsConverter.class)
	@Column(name = "options", columnDefinition = "json")
	private List<QuestionOption> options = new ArrayList<>();

	@Column(name = "code_language", length = 30)
	private String codeLanguage;

	protected AssignmentQuestionEntity() {
	}

	public AssignmentQuestionEntity(
			AssignmentEntity assignment,
			int position,
			QuestionType type,
			String prompt,
			BigDecimal points,
			List<QuestionOption> options,
			String codeLanguage) {
		this.assignment = assignment;
		this.position = position;
		this.type = type;
		this.prompt = prompt;
		this.points = points;
		this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
		this.codeLanguage = codeLanguage;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public AssignmentEntity getAssignment() {
		return assignment;
	}

	public void setAssignment(AssignmentEntity assignment) {
		this.assignment = assignment;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public QuestionType getType() {
		return type;
	}

	public void setType(QuestionType type) {
		this.type = type;
	}

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	public BigDecimal getPoints() {
		return points;
	}

	public void setPoints(BigDecimal points) {
		this.points = points;
	}

	public List<QuestionOption> getOptions() {
		return options;
	}

	public void setOptions(List<QuestionOption> options) {
		this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
	}

	public String getCodeLanguage() {
		return codeLanguage;
	}

	public void setCodeLanguage(String codeLanguage) {
		this.codeLanguage = codeLanguage;
	}
}
