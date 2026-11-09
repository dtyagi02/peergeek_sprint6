package com.peergeek.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.peergeek.config.ResourceSettings;
import com.peergeek.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class ResourceUploadPolicyTest {
	private final ResourceSettings settings = new ResourceSettings();
	private final ResourceUploadPolicy policy = new ResourceUploadPolicy(settings);

	@Test
	void sanitizesFilenameAndUsesConfiguredMimeMapping() {
		var validated = policy.validate(
				"C:\\fakepath\\Lesson.PDF", " application/pdf ", 100, ResourceType.PDF,
				ResourcePurpose.LESSON_RESOURCE);

		assertThat(validated.filename()).isEqualTo("Lesson.PDF");
		assertThat(validated.mimeType()).isEqualTo("application/pdf");
		assertThat(validated.defaultTitle()).isEqualTo("Lesson");
	}

	@Test
	void rejectsExtensionMimeMismatchAndOversizeFiles() {
		assertThatThrownBy(() -> policy.validate(
				"lesson.pdf", "text/plain", 10, ResourceType.PDF, ResourcePurpose.LESSON_RESOURCE))
				.isInstanceOf(BadRequestException.class);

		assertThatThrownBy(() -> policy.validate(
				"lesson.pdf", "application/pdf", settings.maxPdfBytes() + 1,
				ResourceType.PDF, ResourcePurpose.LESSON_RESOURCE))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void appliesTheSmallerThumbnailLimitAndRejectsNonImageTypes() {
		assertThatThrownBy(() -> policy.validate(
				"cover.png", "image/png", settings.maxThumbnailBytes() + 1,
				ResourceType.IMAGE, ResourcePurpose.COURSE_THUMBNAIL))
				.isInstanceOf(BadRequestException.class);

		assertThatThrownBy(() -> policy.validate(
				"cover.pdf", "application/pdf", 10,
				ResourceType.PDF, ResourcePurpose.COURSE_THUMBNAIL))
				.isInstanceOf(BadRequestException.class);
	}
}
