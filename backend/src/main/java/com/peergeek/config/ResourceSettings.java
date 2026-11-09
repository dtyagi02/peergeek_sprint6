package com.peergeek.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("peergeek.resources")
public class ResourceSettings {
	private long maxPdfBytes = 50L * 1024 * 1024;
	private long maxDocumentBytes = 50L * 1024 * 1024;
	private long maxImageBytes = 10L * 1024 * 1024;
	private long maxThumbnailBytes = 5L * 1024 * 1024;
	private long maxVideoBytes = 1024L * 1024 * 1024;
	private int maxResourcesPerLesson = 50;
	private Duration putUrlTtl = Duration.ofMinutes(15);
	private Duration getUrlTtl = Duration.ofMinutes(5);
	private Duration abandonedUploadAge = Duration.ofHours(24);
	private Duration deletedResourceGrace = Duration.ofHours(1);
	private Duration stuckProcessingAge = Duration.ofMinutes(15);
	private Duration cleanupInterval = Duration.ofMinutes(15);
	private int maxProcessingAttempts = 3;
	private int processingThreads = 2;
	private int processingQueueCapacity = 32;
	private long maxOoxmlExpandedBytes = 64L * 1024 * 1024;
	private List<String> allowedPdfMimeTypes = List.of("application/pdf");
	private List<String> allowedDocumentMimeTypes = List.of(
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"application/vnd.openxmlformats-officedocument.presentationml.presentation",
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			"text/plain",
			"text/csv");
	private List<String> allowedImageMimeTypes = List.of("image/png", "image/jpeg", "image/webp", "image/gif");
	private List<String> allowedVideoMimeTypes = List.of("video/mp4", "video/webm");
	private List<String> allowedPdfMappings = List.of("pdf=application/pdf");
	private List<String> allowedDocumentMappings = List.of(
			"docx=application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"pptx=application/vnd.openxmlformats-officedocument.presentationml.presentation",
			"xlsx=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			"txt=text/plain",
			"csv=text/csv");
	private List<String> allowedImageMappings = List.of(
			"png=image/png", "jpg=image/jpeg", "jpeg=image/jpeg", "webp=image/webp", "gif=image/gif");
	private List<String> allowedVideoMappings = List.of("mp4=video/mp4", "webm=video/webm");

	public long maxPdfBytes() { return maxPdfBytes; }
	public void setMaxPdfBytes(long value) { maxPdfBytes = value; }
	public long maxDocumentBytes() { return maxDocumentBytes; }
	public void setMaxDocumentBytes(long value) { maxDocumentBytes = value; }
	public long maxImageBytes() { return maxImageBytes; }
	public void setMaxImageBytes(long value) { maxImageBytes = value; }
	public long maxThumbnailBytes() { return maxThumbnailBytes; }
	public void setMaxThumbnailBytes(long value) { maxThumbnailBytes = value; }
	public long maxVideoBytes() { return maxVideoBytes; }
	public void setMaxVideoBytes(long value) { maxVideoBytes = value; }
	public int maxResourcesPerLesson() { return maxResourcesPerLesson; }
	public void setMaxResourcesPerLesson(int value) { maxResourcesPerLesson = value; }
	public Duration putUrlTtl() { return putUrlTtl; }
	public void setPutUrlTtl(Duration value) { putUrlTtl = value; }
	public Duration getUrlTtl() { return getUrlTtl; }
	public void setGetUrlTtl(Duration value) { getUrlTtl = value; }
	public Duration abandonedUploadAge() { return abandonedUploadAge; }
	public void setAbandonedUploadAge(Duration value) { abandonedUploadAge = value; }
	public Duration deletedResourceGrace() { return deletedResourceGrace; }
	public void setDeletedResourceGrace(Duration value) { deletedResourceGrace = value; }
	public Duration stuckProcessingAge() { return stuckProcessingAge; }
	public void setStuckProcessingAge(Duration value) { stuckProcessingAge = value; }
	public Duration cleanupInterval() { return cleanupInterval; }
	public void setCleanupInterval(Duration value) { cleanupInterval = value; }
	public int maxProcessingAttempts() { return maxProcessingAttempts; }
	public void setMaxProcessingAttempts(int value) { maxProcessingAttempts = value; }
	public int processingThreads() { return processingThreads; }
	public void setProcessingThreads(int value) { processingThreads = value; }
	public int processingQueueCapacity() { return processingQueueCapacity; }
	public void setProcessingQueueCapacity(int value) { processingQueueCapacity = value; }
	public long maxOoxmlExpandedBytes() { return maxOoxmlExpandedBytes; }
	public void setMaxOoxmlExpandedBytes(long value) { maxOoxmlExpandedBytes = value; }
	public List<String> allowedPdfMimeTypes() { return allowedPdfMimeTypes; }
	public void setAllowedPdfMimeTypes(List<String> value) { allowedPdfMimeTypes = List.copyOf(value); }
	public List<String> allowedDocumentMimeTypes() { return allowedDocumentMimeTypes; }
	public void setAllowedDocumentMimeTypes(List<String> value) { allowedDocumentMimeTypes = List.copyOf(value); }
	public List<String> allowedImageMimeTypes() { return allowedImageMimeTypes; }
	public void setAllowedImageMimeTypes(List<String> value) { allowedImageMimeTypes = List.copyOf(value); }
	public List<String> allowedVideoMimeTypes() { return allowedVideoMimeTypes; }
	public void setAllowedVideoMimeTypes(List<String> value) { allowedVideoMimeTypes = List.copyOf(value); }
	public List<String> allowedPdfMappings() { return allowedPdfMappings; }
	public void setAllowedPdfMappings(List<String> value) { allowedPdfMappings = List.copyOf(value); }
	public List<String> allowedDocumentMappings() { return allowedDocumentMappings; }
	public void setAllowedDocumentMappings(List<String> value) { allowedDocumentMappings = List.copyOf(value); }
	public List<String> allowedImageMappings() { return allowedImageMappings; }
	public void setAllowedImageMappings(List<String> value) { allowedImageMappings = List.copyOf(value); }
	public List<String> allowedVideoMappings() { return allowedVideoMappings; }
	public void setAllowedVideoMappings(List<String> value) { allowedVideoMappings = List.copyOf(value); }
}
