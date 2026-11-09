package com.peergeek.discovery;

import java.util.List;

public final class DiscoveryCacheEnvelopes {
	public static final int CURRENT_SCHEMA_VERSION = 1;

	private DiscoveryCacheEnvelopes() {}

	public record CoursePreviewEnvelope(int schemaVersion, CoursePreviewDto payload) {}
	public record PublicOutlineEnvelope(int schemaVersion, List<CoursePreviewDto.ModulePreviewDto> payload) {}
	public record PopularCoursesEnvelope(int schemaVersion, List<CourseCardDto> payload) {}
	public record PublicCategoriesEnvelope(int schemaVersion, List<PublicCategoryDto> payload) {}
}
