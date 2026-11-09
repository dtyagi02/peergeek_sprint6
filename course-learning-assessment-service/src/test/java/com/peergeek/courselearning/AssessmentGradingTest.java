package com.peergeek.courselearning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentGradingTest {

    record Question(Long id, BigDecimal points, Long correctOptionId) {}
    record Answer(Long questionId, Long selectedOptionId) {}

    @Test
    @DisplayName("MCQ auto-grading accurately assigns points for correct answers and zero for incorrect")
    void gradesMcqAssessmentAccurately() {
        List<Question> questions = List.of(
                new Question(1L, new BigDecimal("25.00"), 101L),
                new Question(2L, new BigDecimal("25.00"), 104L),
                new Question(3L, new BigDecimal("25.00"), 107L),
                new Question(4L, new BigDecimal("25.00"), 110L)
        );

        List<Answer> studentAnswers = List.of(
                new Answer(1L, 101L), // correct (25)
                new Answer(2L, 103L), // incorrect (0)
                new Answer(3L, 107L), // correct (25)
                new Answer(4L, 110L)  // correct (25)
        );

        Map<Long, Question> questionMap = questions.stream()
                .collect(java.util.stream.Collectors.toMap(Question::id, q -> q));

        BigDecimal totalPoints = questions.stream()
                .map(Question::points)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal score = BigDecimal.ZERO;
        for (Answer ans : studentAnswers) {
            Question q = questionMap.get(ans.questionId());
            if (q != null && q.correctOptionId().equals(ans.selectedOptionId())) {
                score = score.add(q.points());
            }
        }

        assertThat(totalPoints).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(score).isEqualByComparingTo(new BigDecimal("75.00"));

        BigDecimal percentage = score.multiply(BigDecimal.valueOf(100))
                .divide(totalPoints, 2, RoundingMode.HALF_UP);
        assertThat(percentage).isEqualByComparingTo(new BigDecimal("75.00"));
    }
}

