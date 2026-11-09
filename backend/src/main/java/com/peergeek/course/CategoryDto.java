package com.peergeek.course;

public record CategoryDto(Long id, String name, String slug) {
	public static CategoryDto from(CategoryEntity category) {
		return new CategoryDto(category.getId(), category.getName(), category.getSlug());
	}
}
