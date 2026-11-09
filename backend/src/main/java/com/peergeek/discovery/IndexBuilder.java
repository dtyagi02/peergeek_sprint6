package com.peergeek.discovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import com.peergeek.course.PublishedCourseSnapshot;
import com.peergeek.user.PublicProfileDto;
import org.springframework.stereotype.Component;

@Component
public class IndexBuilder {
	public PublicCourseIndexEntity build(
			PublishedCourseSnapshot snapshot, PublicProfileDto instructor, Instant now) {
		String shortDescription = extractShortDescription(snapshot.description());
		String instructorName = instructor == null ? null : instructor.displayName();
		List<String> topics = snapshot.topics();
		String topicsText = topics == null || topics.isEmpty() ? null : String.join(", ", topics);
		String searchText = buildSearchText(
				snapshot.title(),
				topicsText,
				snapshot.categoryName(),
				instructorName,
				snapshot.description());
		String fingerprint = computeFingerprint(snapshot, instructorName);

		return new PublicCourseIndexEntity(
				snapshot.courseId(),
				snapshot.title(),
				shortDescription,
				searchText,
				snapshot.categoryId(),
				snapshot.categoryName(),
				snapshot.categorySlug(),
				snapshot.difficulty() == null ? null : snapshot.difficulty().name(),
				topicsText,
				snapshot.ownerId(),
				instructorName,
				snapshot.moduleCount(),
				snapshot.lessonCount(),
				snapshot.thumbnailResourceId(),
				snapshot.publishedAt(),
				fingerprint,
				now);
	}

	public String computeFingerprint(PublishedCourseSnapshot snapshot, String instructorName) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			String raw = String.join("|",
					Long.toString(snapshot.courseId()),
					snapshot.title() == null ? "" : snapshot.title(),
					snapshot.description() == null ? "" : snapshot.description(),
					snapshot.categoryId() == null ? "" : Long.toString(snapshot.categoryId()),
					snapshot.categoryName() == null ? "" : snapshot.categoryName(),
					snapshot.categorySlug() == null ? "" : snapshot.categorySlug(),
					snapshot.difficulty() == null ? "" : snapshot.difficulty().name(),
					snapshot.topics() == null ? "" : String.join(",", snapshot.topics()),
					Long.toString(snapshot.ownerId()),
					instructorName == null ? "" : instructorName,
					Integer.toString(snapshot.moduleCount()),
					Integer.toString(snapshot.lessonCount()),
					snapshot.thumbnailResourceId() == null ? "" : Long.toString(snapshot.thumbnailResourceId()),
					snapshot.publishedAt() == null ? "" : Long.toString(snapshot.publishedAt().toEpochMilli()));
			return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 algorithm not available", exception);
		}
	}

	public String extractShortDescription(String description) {
		if (description == null || description.isBlank()) {
			return null;
		}
		String collapsed = description.replaceAll("\\s+", " ").trim();
		if (collapsed.length() <= 200) {
			return collapsed;
		}
		int boundary = collapsed.lastIndexOf(' ', 200);
		if (boundary <= 0) {
			boundary = 200;
		}
		return collapsed.substring(0, boundary).trim() + "...";
	}

	public String buildSearchText(
			String title, String topics, String categoryName, String instructorName, String description) {
		StringBuilder builder = new StringBuilder();
		if (title != null) builder.append(title).append(' ');
		if (topics != null) builder.append(topics).append(' ');
		if (categoryName != null) builder.append(categoryName).append(' ');
		if (instructorName != null) builder.append(instructorName).append(' ');
		if (description != null) builder.append(description);
		return builder.toString().replaceAll("\\s+", " ").trim();
	}
}
