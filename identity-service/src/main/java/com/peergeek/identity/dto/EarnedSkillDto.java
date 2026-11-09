package com.peergeek.identity.dto;

import java.time.Instant;
import java.util.List;

public record EarnedSkillDto(
		String skillName,
		List<String> sourceCourses,
		Instant earnedAt) {
}

