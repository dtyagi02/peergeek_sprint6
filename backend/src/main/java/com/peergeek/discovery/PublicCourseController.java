package com.peergeek.discovery;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import com.peergeek.common.PageResponse;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.course.PublishedCourseSnapshot;
import com.peergeek.exception.NotFoundException;
import com.peergeek.file.ObjectStorageService;
import com.peergeek.file.ResourceDownloadService;
import com.peergeek.file.ResourceEntity;
import com.peergeek.file.ResourcePurpose;
import com.peergeek.file.ResourceRepository;
import com.peergeek.file.ResourceStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/courses")
public class PublicCourseController {
	private static final String CACHE_CONTROL_VALUE = "public, max-age=60";
	private static final String VARY_VALUE = "Accept-Encoding";

	private final PublicCourseQueryService queryService;
	private final CourseStructureQueryService courseStructure;
	private final ResourceRepository resourceRepository;
	private final ResourceDownloadService downloadService;

	public PublicCourseController(
			PublicCourseQueryService queryService,
			CourseStructureQueryService courseStructure,
			ResourceRepository resourceRepository,
			ResourceDownloadService downloadService) {
		this.queryService = queryService;
		this.courseStructure = courseStructure;
		this.resourceRepository = resourceRepository;
		this.downloadService = downloadService;
	}

	@GetMapping
	public ResponseEntity<PageResponse<CourseCardDto>> list(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) List<String> difficulty,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			@RequestParam(required = false) String sort) {
		PageResponse<CourseCardDto> response = queryService.searchCourses(
				q, category, difficulty, page, size, sort);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE)
				.header(HttpHeaders.VARY, VARY_VALUE)
				.body(response);
	}

	@GetMapping("/popular")
	public ResponseEntity<List<CourseCardDto>> popular() {
		List<CourseCardDto> response = queryService.getPopularCourses();
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE)
				.header(HttpHeaders.VARY, VARY_VALUE)
				.body(response);
	}

	@GetMapping("/{courseId}")
	public ResponseEntity<CoursePreviewDto> preview(
			@PathVariable long courseId,
			@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
		CoursePreviewDto preview = queryService.getCoursePreview(courseId);
		String etag = computeWeakEtag(preview);
		if (ifNoneMatch != null && (ifNoneMatch.equals(etag) || ifNoneMatch.equals(etag.replace("W/", "")))) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
					.header(HttpHeaders.ETAG, etag)
					.header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE)
					.header(HttpHeaders.VARY, VARY_VALUE)
					.build();
		}

		return ResponseEntity.ok()
				.header(HttpHeaders.ETAG, etag)
				.header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE)
				.header(HttpHeaders.VARY, VARY_VALUE)
				.body(preview);
	}

	@GetMapping("/{courseId}/thumbnail")
	public ResponseEntity<Void> thumbnail(@PathVariable long courseId) {
		if (!courseStructure.isPublished(courseId)) {
			throw new NotFoundException("The requested course was not found");
		}
		PublishedCourseSnapshot snapshot = courseStructure.getPublishedCourse(courseId)
				.orElseThrow(() -> new NotFoundException("The requested course was not found"));
		if (snapshot.thumbnailResourceId() == null) {
			throw new NotFoundException("The requested course was not found");
		}

		long thumbnailResourceId = snapshot.thumbnailResourceId();
		ResourceEntity resource = resourceRepository.findByIdAndDeletedAtIsNull(thumbnailResourceId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));

		if (resource.getCourseId() == null
				|| resource.getCourseId() != courseId
				|| resource.getPurpose() != ResourcePurpose.COURSE_THUMBNAIL
				|| resource.getStatus() != ResourceStatus.READY) {
			throw new NotFoundException("The requested resource was not found");
		}

		URI presignedUrl = downloadService.presignedGetUrl(thumbnailResourceId, ObjectStorageService.Disposition.INLINE);
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(presignedUrl)
				.header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE)
				.build();
	}

	private String computeWeakEtag(CoursePreviewDto preview) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			String raw = preview.id() + "|" + preview.title() + "|" + preview.description()
					+ "|" + preview.moduleCount() + "|" + preview.lessonCount()
					+ "|" + (preview.publishedAt() == null ? "" : preview.publishedAt().toEpochMilli())
					+ "|" + (preview.stats() == null ? "" : (preview.stats().enrollmentCount() + ":" + preview.stats().ratingCount()));
			return "W/\"" + HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8))) + "\"";
		} catch (NoSuchAlgorithmException e) {
			return "W/\"" + Integer.toHexString(preview.hashCode()) + "\"";
		}
	}
}
