package com.peergeek.review;

import java.time.Instant;

public record ReviewDto(
		Long id,
		Long courseId,
		Long userId,
		String reviewerName,
		String reviewerUsername,
		String reviewerAvatarUrl,
		int rating,
		String reviewText,
		Instant createdAt,
		Instant updatedAt) {

	public ReviewDto(
			Long id,
			Long courseId,
			Long userId,
			String reviewerName,
			int rating,
			String reviewText,
			Instant createdAt,
			Instant updatedAt) {
		this(id, courseId, userId, reviewerName, null, null, rating, reviewText, createdAt, updatedAt);
	}
}
