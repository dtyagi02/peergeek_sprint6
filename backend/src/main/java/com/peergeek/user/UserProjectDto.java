package com.peergeek.user;

public record UserProjectDto(
		Long id,
		String title,
		String description,
		String technologies,
		String projectUrl,
		String repoUrl) {

	public static UserProjectDto from(UserProjectEntity entity) {
		return new UserProjectDto(
				entity.getId(),
				entity.getTitle(),
				entity.getDescription(),
				entity.getTechnologies(),
				entity.getProjectUrl(),
				entity.getRepoUrl());
	}
}
