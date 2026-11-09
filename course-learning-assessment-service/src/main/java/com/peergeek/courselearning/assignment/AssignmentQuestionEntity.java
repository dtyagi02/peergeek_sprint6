package com.peergeek.courselearning.assignment;

import com.peergeek.courselearning.common.QuestionType;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "assignment_questions")
public class AssignmentQuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "position", nullable = false)
    private int position = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private QuestionType type = QuestionType.MCQ;

    @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "points", nullable = false, precision = 6, scale = 2)
    private BigDecimal points = new BigDecimal("1.00");

    @Column(name = "options", columnDefinition = "JSON")
    private String options;

    @Column(name = "code_language", length = 64)
    private String codeLanguage;

    protected AssignmentQuestionEntity() {}

    public AssignmentQuestionEntity(Long assignmentId, int position, QuestionType type, String prompt, BigDecimal points, String options, String codeLanguage) {
        this.assignmentId = assignmentId;
        this.position = position;
        this.type = type != null ? type : QuestionType.MCQ;
        this.prompt = prompt;
        this.points = points != null ? points : new BigDecimal("1.00");
        this.options = options;
        this.codeLanguage = codeLanguage;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAssignmentId() {
        return assignmentId;
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

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public String getCodeLanguage() {
        return codeLanguage;
    }

    public void setCodeLanguage(String codeLanguage) {
        this.codeLanguage = codeLanguage;
    }
}

