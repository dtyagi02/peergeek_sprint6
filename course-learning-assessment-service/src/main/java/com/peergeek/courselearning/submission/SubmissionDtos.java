package com.peergeek.courselearning.submission;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class SubmissionDtos {

    public record GradeSubmissionRequest(
            long version,
            BigDecimal marks,
            List<QuestionGradeItem> questionGrades,
            String overallFeedback
    ) {
        public GradeSubmissionRequest {
            questionGrades = questionGrades == null ? List.of() : List.copyOf(questionGrades);
        }

        public record QuestionGradeItem(
                long questionId,
                BigDecimal points,
                String feedback
        ) {}
    }

    public record InstructorSubmissionSummaryDto(
            long submissionId,
            long assignmentId,
            String assignmentTitle,
            LearnerSummary learner,
            String status,
            Instant submittedAt,
            boolean late,
            boolean autoSubmitted,
            boolean autoGraded,
            BigDecimal totalPoints,
            BigDecimal maxPoints
    ) {
        public record LearnerSummary(
                long id,
                String displayName
        ) {}
    }

    public record InstructorSubmissionDetailDto(
            long submissionId,
            long assignmentId,
            String assignmentTitle,
            LearnerSummary learner,
            String status,
            Instant startedAt,
            Instant deadlineAt,
            Instant submittedAt,
            boolean late,
            boolean autoSubmitted,
            boolean autoGraded,
            BigDecimal marks,
            BigDecimal totalPoints,
            BigDecimal maxPoints,
            String overallFeedback,
            String textAnswer,
            Long gradedBy,
            Instant gradedAt,
            long version,
            List<SubmissionFileInfoDto> files,
            List<SubmissionQuestionAnswerDto> answers
    ) {
        public record LearnerSummary(long id, String displayName, String email) {}
        public record SubmissionFileInfoDto(long id, String filename, String mimeType, long sizeBytes, String status) {}
        public record SubmissionQuestionAnswerDto(
                long questionId,
                int position,
                String prompt,
                BigDecimal maxPoints,
                String answerText,
                List<String> selectedOptionIds,
                BigDecimal autoPoints,
                BigDecimal points,
                String feedback
        ) {}
    }

    public record FileUploadRequest(
            String filename,
            String mimeType,
            long sizeBytes
    ) {}

    public record FileUploadResponse(
            long fileId,
            UploadDetails upload
    ) {
        public record UploadDetails(
                URI url,
                String method,
                Map<String, String> headers,
                Instant expiresAt
        ) {}
    }

    public record FileConfirmResponse(
            long fileId,
            String status,
            String filename
    ) {}

    public record FileDownloadUrlResponse(
            URI url,
            Instant expiresAt
    ) {}
}

