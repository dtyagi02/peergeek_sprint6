package com.peergeek.courselearning.assignment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.peergeek.courselearning.common.AssignmentKind;
import com.peergeek.courselearning.common.AssignmentStatus;
import com.peergeek.courselearning.common.QuestionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class AssignmentDtos {

    public record CreateAssignmentRequest(
            AssignmentKind kind,
            String title,
            String instructions,
            Long moduleId,
            Long lessonId,
            Instant opensAt,
            Instant dueAt,
            Integer timeLimitMinutes,
            Boolean allowLate,
            Boolean allowText,
            Boolean allowFiles,
            Boolean showCorrectAnswers,
            BigDecimal maxPoints
    ) {}

    public record UpdateAssignmentRequest(
            String title,
            String instructions,
            Long moduleId,
            Long lessonId,
            Instant opensAt,
            Instant dueAt,
            Integer timeLimitMinutes,
            Boolean allowLate,
            Boolean allowText,
            Boolean allowFiles,
            Boolean showCorrectAnswers,
            BigDecimal maxPoints,
            Long version
    ) {}

    public record QuestionUpsertRequest(
            Long id,
            QuestionType type,
            String prompt,
            BigDecimal points,
            List<QuestionOption> options,
            String codeLanguage
    ) {}

    public record QuestionDto(
            Long id,
            int position,
            QuestionType type,
            String prompt,
            BigDecimal points,
            List<QuestionOption> options,
            String codeLanguage
    ) {}

    public record InstructorAssignmentDetailDto(
            Long id,
            Long courseId,
            Long moduleId,
            Long lessonId,
            AssignmentKind kind,
            String title,
            String instructions,
            AssignmentStatus status,
            BigDecimal maxPoints,
            Instant opensAt,
            Instant dueAt,
            Integer timeLimitMinutes,
            boolean allowLate,
            boolean allowText,
            boolean allowFiles,
            boolean showCorrectAnswers,
            int attemptCount,
            Instant publishedAt,
            long version,
            List<QuestionDto> questions
    ) {}

    public record InstructorAssignmentSummaryDto(
            Long id,
            Long courseId,
            Long moduleId,
            Long lessonId,
            AssignmentKind kind,
            String title,
            String instructions,
            AssignmentStatus status,
            BigDecimal maxPoints,
            Instant opensAt,
            Instant dueAt,
            Integer timeLimitMinutes,
            boolean allowLate,
            boolean allowText,
            boolean allowFiles,
            boolean showCorrectAnswers,
            int attemptCount,
            Instant publishedAt,
            long version,
            SubmissionCounts counts
    ) {
        public record SubmissionCounts(int submitted, int graded, int inProgress) {}
    }

    public record LearnerAssignmentSummaryDto(
            long id,
            long courseId,
            String courseTitle,
            AssignmentKind kind,
            String title,
            Long moduleId,
            Long lessonId,
            Instant opensAt,
            Instant dueAt,
            Integer timeLimitMinutes,
            BigDecimal maxPoints,
            String status,
            boolean late,
            BigDecimal totalPoints
    ) {}

    public record LearnerAssignmentDetailDto(
            long id,
            long courseId,
            String title,
            String instructions,
            AssignmentKind kind,
            BigDecimal maxPoints,
            Instant opensAt,
            Instant dueAt,
            Integer timeLimitMinutes,
            boolean allowLate,
            boolean allowText,
            boolean allowFiles,
            boolean showCorrectAnswers,
            Long moduleId,
            Long lessonId,
            LearnerAttemptSummaryDto attempt
    ) {
        @JsonProperty("hasAttempt")
        public boolean hasAttempt() {
            return attempt != null;
        }

        @JsonProperty("attemptStatus")
        public String attemptStatus() {
            return attempt != null ? attempt.status() : null;
        }
    }

    public record LearnerAttemptSummaryDto(
            long submissionId,
            String status,
            Instant startedAt,
            Instant deadlineAt,
            Instant submittedAt,
            BigDecimal marks,
            BigDecimal totalPoints
    ) {}

    public record LearnerAttemptDto(
            long submissionId,
            long assignmentId,
            String status,
            Instant startedAt,
            Instant deadlineAt,
            Instant serverNow,
            long version,
            String textAnswer,
            List<LearnerQuestionDto> questions,
            List<LearnerAnswerDto> answers
    ) {
        public LearnerAttemptDto {
            questions = questions == null ? List.of() : List.copyOf(questions);
            answers = answers == null ? List.of() : List.copyOf(answers);
        }
    }

    public record LearnerQuestionDto(
            long id,
            int position,
            QuestionType type,
            String prompt,
            BigDecimal points,
            List<LearnerQuestionOptionDto> options,
            String codeLanguage
    ) {
        public LearnerQuestionDto {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record LearnerQuestionOptionDto(
            String id,
            String text
    ) {}

    public record LearnerAnswerDto(
            long questionId,
            String answerText,
            List<String> selectedOptionIds
    ) {
        public LearnerAnswerDto {
            selectedOptionIds = selectedOptionIds == null ? List.of() : List.copyOf(selectedOptionIds);
        }
    }

    public record LearnerSubmissionDetailDto(
            long submissionId,
            long assignmentId,
            String assignmentTitle,
            AssignmentKind kind,
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
            List<SubmissionFileInfoDto> files,
            List<LearnerSubmissionQuestionDto> questions
    ) {
        public LearnerSubmissionDetailDto {
            files = files == null ? List.of() : List.copyOf(files);
            questions = questions == null ? List.of() : List.copyOf(questions);
        }

        public record SubmissionFileInfoDto(
                long id,
                String filename,
                String mimeType,
                long sizeBytes,
                String status
        ) {}

        public record LearnerSubmissionQuestionDto(
                long questionId,
                int position,
                QuestionType type,
                String prompt,
                BigDecimal maxPoints,
                String codeLanguage,
                List<SubmissionQuestionOptionDto> options,
                LearnerQuestionAnswerDto studentAnswer
        ) {
            public LearnerSubmissionQuestionDto {
                options = options == null ? List.of() : List.copyOf(options);
            }
        }

        public record SubmissionQuestionOptionDto(
                String id,
                String text,
                Boolean correct
        ) {}

        public record LearnerQuestionAnswerDto(
                String answerText,
                List<String> selectedOptionIds,
                BigDecimal pointsAwarded,
                String feedback
        ) {
            public LearnerQuestionAnswerDto {
                selectedOptionIds = selectedOptionIds == null ? List.of() : List.copyOf(selectedOptionIds);
            }
        }
    }

    public record DraftSubmissionRequest(
            long version,
            String textAnswer,
            List<DraftAnswerItem> answers
    ) {
        public DraftSubmissionRequest {
            answers = answers == null ? List.of() : List.copyOf(answers);
        }

        public record DraftAnswerItem(
                long questionId,
                String answerText,
                List<String> selectedOptionIds
        ) {
            public DraftAnswerItem {
                selectedOptionIds = selectedOptionIds == null ? List.of() : List.copyOf(selectedOptionIds);
            }
        }
    }

    public record SubmitAssignmentRequest(
            long version
    ) {}

    public record DraftSaveResult(
            long version,
            Instant savedAt
    ) {}
}

