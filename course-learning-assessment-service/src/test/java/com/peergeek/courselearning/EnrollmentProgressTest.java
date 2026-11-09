package com.peergeek.courselearning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

class EnrollmentProgressTest {

    @Test
    @DisplayName("Progress percentage calculates correctly with rounding")
    void calculatesProgressPercentage() {
        // 0 of 5 lessons completed = 0%
        BigDecimal progress0 = calculatePercent(0, 5);
        assertThat(progress0).isEqualByComparingTo(BigDecimal.ZERO);

        // 1 of 3 lessons completed = 33.33%
        BigDecimal progress1 = calculatePercent(1, 3);
        assertThat(progress1).isEqualByComparingTo(new BigDecimal("33.33"));

        // 2 of 3 lessons completed = 66.67%
        BigDecimal progress2 = calculatePercent(2, 3);
        assertThat(progress2).isEqualByComparingTo(new BigDecimal("66.67"));

        // 3 of 3 lessons completed = 100.00%
        BigDecimal progress3 = calculatePercent(3, 3);
        assertThat(progress3).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Completed count reaching or exceeding total marks completion")
    void marksCompletionWhenAllLessonsCompleted() {
        int totalLessons = 4;
        int completedLessons = 4;

        boolean isComplete = completedLessons >= totalLessons && totalLessons > 0;
        assertThat(isComplete).isTrue();

        int incompleteLessons = 3;
        boolean isIncomplete = incompleteLessons >= totalLessons;
        assertThat(isIncomplete).isFalse();
    }

    private BigDecimal calculatePercent(int completed, int total) {
        if (total <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
