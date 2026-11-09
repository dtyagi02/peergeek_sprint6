package com.peergeek.user;

import java.time.Instant;
import java.util.List;

public record EarnedSkillDto(
		String skillName,
		List<String> sourceCourses,
		Instant earnedAt) {
}
