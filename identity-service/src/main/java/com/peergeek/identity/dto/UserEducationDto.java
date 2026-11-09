package com.peergeek.identity.dto;

import com.peergeek.identity.entity.UserEducationEntity;

public record UserEducationDto(
		Long id,
		String institution,
		String degree,
		String fieldOfStudy,
		Integer startYear,
		Integer endYear) {

	public static UserEducationDto from(UserEducationEntity entity) {
		return new UserEducationDto(
				entity.getId(),
				entity.getInstitution(),
				entity.getDegree(),
				entity.getFieldOfStudy(),
				entity.getStartYear(),
				entity.getEndYear());
	}
}

