package com.peergeek.courselearning.audit;

public final class AuditAction {
    public static final String COURSE_CREATED = "COURSE_CREATED";
    public static final String COURSE_PUBLISHED = "COURSE_PUBLISHED";
    public static final String COURSE_UNPUBLISHED = "COURSE_UNPUBLISHED";
    public static final String REPORT_CREATED = "REPORT_CREATED";
    public static final String REPORT_STATUS_UPDATED = "REPORT_STATUS_UPDATED";
    public static final String REVIEW_CREATED = "REVIEW_CREATED";
    public static final String ENROLLMENT_CREATED = "ENROLLMENT_CREATED";

    private AuditAction() {}
}
