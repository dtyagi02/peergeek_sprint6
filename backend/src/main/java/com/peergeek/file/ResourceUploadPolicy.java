package com.peergeek.file;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.peergeek.config.ResourceSettings;
import com.peergeek.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ResourceUploadPolicy {
	private final ResourceSettings settings;

	public ResourceUploadPolicy(ResourceSettings settings) {
		this.settings = settings;
	}

	public ValidatedUpload validate(
			String filename,
			String declaredMimeType,
			long sizeBytes,
			ResourceType type,
			ResourcePurpose purpose) {
		if (type == ResourceType.LINK) {
			throw new BadRequestException("Links do not accept file uploads.");
		}
		if (purpose == ResourcePurpose.COURSE_THUMBNAIL && type != ResourceType.IMAGE) {
			throw new BadRequestException("Course thumbnails must be image resources.");
		}
		if (sizeBytes <= 0) {
			throw new BadRequestException("The file must contain at least one byte.");
		}
		String cleanFilename = sanitizeFilename(filename);
		String extension = extension(cleanFilename);
		String mimeType = normalizedMimeType(declaredMimeType);
		Map<String, String> allowedMappings = allowedMappings(type);
		String expectedMimeType = allowedMappings.get(extension);
		if (expectedMimeType == null || !expectedMimeType.equals(mimeType)
				|| !allowedMimeTypes(type).contains(mimeType)) {
			throw new BadRequestException("The filename extension and MIME type are not allowed for this resource type.");
		}
		long maximumSize = maximumBytes(type, purpose);
		if (sizeBytes > maximumSize) {
			throw new BadRequestException("The file exceeds the maximum size of " + maximumSize + " bytes.");
		}
		return new ValidatedUpload(cleanFilename, mimeType, sizeBytes, type, titleFromFilename(cleanFilename));
	}

	public Map<String, List<String>> limits() {
		Map<String, List<String>> extensionsByType = new LinkedHashMap<>();
		for (ResourceType type : List.of(
				ResourceType.PDF, ResourceType.DOCUMENT, ResourceType.IMAGE, ResourceType.VIDEO)) {
			extensionsByType.put(type.name(), List.copyOf(allowedMappings(type).keySet()));
		}
		return Map.copyOf(extensionsByType);
	}

	public long maximumBytes(ResourceType type, ResourcePurpose purpose) {
		if (purpose == ResourcePurpose.COURSE_THUMBNAIL) {
			return settings.maxThumbnailBytes();
		}
		return switch (type) {
			case PDF -> settings.maxPdfBytes();
			case DOCUMENT -> settings.maxDocumentBytes();
			case IMAGE -> settings.maxImageBytes();
			case VIDEO -> settings.maxVideoBytes();
			case LINK -> 0;
		};
	}

	private Map<String, String> allowedMappings(ResourceType type) {
		List<String> entries = switch (type) {
			case PDF -> settings.allowedPdfMappings();
			case DOCUMENT -> settings.allowedDocumentMappings();
			case IMAGE -> settings.allowedImageMappings();
			case VIDEO -> settings.allowedVideoMappings();
			case LINK -> List.of();
		};
		Map<String, String> mappings = new LinkedHashMap<>();
		for (String entry : entries) {
			String[] parts = entry.split("=", 2);
			if (parts.length == 2 && StringUtils.hasText(parts[0]) && StringUtils.hasText(parts[1])) {
				mappings.put(parts[0].trim().toLowerCase(Locale.ROOT),
						parts[1].trim().toLowerCase(Locale.ROOT));
			}
		}
		return Map.copyOf(mappings);
	}

	private List<String> allowedMimeTypes(ResourceType type) {
		List<String> configured = switch (type) {
			case PDF -> settings.allowedPdfMimeTypes();
			case DOCUMENT -> settings.allowedDocumentMimeTypes();
			case IMAGE -> settings.allowedImageMimeTypes();
			case VIDEO -> settings.allowedVideoMimeTypes();
			case LINK -> List.of();
		};
		return configured.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
	}

	private String normalizedMimeType(String value) {
		if (!StringUtils.hasText(value)) {
			throw new BadRequestException("A MIME type is required.");
		}
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private String sanitizeFilename(String filename) {
		if (!StringUtils.hasText(filename)) {
			throw new BadRequestException("A filename is required.");
		}
		int separator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
		String basename = filename.substring(separator + 1).replaceAll("\\p{Cntrl}", "").trim();
		if (basename.isBlank()) {
			throw new BadRequestException("A filename is required.");
		}
		return basename.length() > 255 ? basename.substring(0, 255) : basename;
	}

	private String extension(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot < 0 || dot == filename.length() - 1
				? ""
				: filename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private String titleFromFilename(String filename) {
		int dot = filename.lastIndexOf('.');
		String title = dot > 0 ? filename.substring(0, dot) : filename;
		return title.length() > 255 ? title.substring(0, 255) : title;
	}

	public record ValidatedUpload(
			String filename, String mimeType, long sizeBytes, ResourceType type, String defaultTitle) {
	}
}
