package com.peergeek.courselearning.readiness;

import jakarta.persistence.*;

@Entity
@Table(name = "readiness_issues")
public class ReadinessIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity; // BLOCKING, WARNING

    @Column(name = "scope", nullable = false, length = 32)
    private String scope; // COURSE, MODULE, LESSON, RESOURCE

    @Column(name = "module_id")
    private Long moduleId;

    @Column(name = "lesson_id")
    private Long lessonId;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "module_title")
    private String moduleTitle;

    @Column(name = "lesson_title")
    private String lessonTitle;

    @Column(name = "resource_title")
    private String resourceTitle;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "fix_hint", length = 1000)
    private String fixHint;

    @Column(name = "details", columnDefinition = "JSON")
    private String details;

    protected ReadinessIssueEntity() {}

    public ReadinessIssueEntity(Long runId, String ruleCode, String severity, String scope, String message, String fixHint) {
        this.runId = runId;
        this.ruleCode = ruleCode;
        this.severity = severity;
        this.scope = scope;
        this.message = message;
        this.fixHint = fixHint;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRunId() {
        return runId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getSeverity() {
        return severity;
    }

    public String getScope() {
        return scope;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public void setLessonId(Long lessonId) {
        this.lessonId = lessonId;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getModuleTitle() {
        return moduleTitle;
    }

    public void setModuleTitle(String moduleTitle) {
        this.moduleTitle = moduleTitle;
    }

    public String getLessonTitle() {
        return lessonTitle;
    }

    public void setLessonTitle(String lessonTitle) {
        this.lessonTitle = lessonTitle;
    }

    public String getResourceTitle() {
        return resourceTitle;
    }

    public void setResourceTitle(String resourceTitle) {
        this.resourceTitle = resourceTitle;
    }

    public String getMessage() {
        return message;
    }

    public String getFixHint() {
        return fixHint;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}

