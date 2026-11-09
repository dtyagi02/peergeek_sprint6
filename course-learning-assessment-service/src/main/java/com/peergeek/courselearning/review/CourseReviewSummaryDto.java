package com.peergeek.courselearning.review;

import java.util.Map;

public record CourseReviewSummaryDto(
        Long courseId,
        double averageRating,
        int totalReviews,
        Map<Integer, Integer> distribution,
        Map<Integer, Double> distributionPercentages) {
}
