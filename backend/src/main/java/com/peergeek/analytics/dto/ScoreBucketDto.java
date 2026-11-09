package com.peergeek.analytics.dto;

public record ScoreBucketDto(
		String bucket,
		long count,
		double percentage) {
}
