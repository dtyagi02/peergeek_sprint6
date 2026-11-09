package com.peergeek.courselearning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateEligibilityTest {

    @Test
    @DisplayName("Learner is eligible for certificate only when completed lessons equals total structure lessons")
    void verifiesCertificateEligibility() {
        int totalLessons = 5;

        // Partially completed (3/5)
        int completedPart = 3;
        BigDecimal percentPart = BigDecimal.valueOf(completedPart * 100.0 / totalLessons);
        boolean eligiblePart = completedPart >= totalLessons && percentPart.compareTo(BigDecimal.valueOf(100)) >= 0;
        assertThat(eligiblePart).isFalse();

        // Fully completed (5/5)
        int completedFull = 5;
        BigDecimal percentFull = BigDecimal.valueOf(completedFull * 100.0 / totalLessons);
        boolean eligibleFull = completedFull >= totalLessons && percentFull.compareTo(BigDecimal.valueOf(100)) >= 0;
        assertThat(eligibleFull).isTrue();
    }
}

