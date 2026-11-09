package com.peergeek.file;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.peergeek.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instructor")
public class InstructorResourceController {
	private final InstructorResourceService resources;
	private final CourseThumbnailService thumbnails;
	private final ResourceUploadService uploads;
	private final CurrentUserProvider currentUser;

	public InstructorResourceController(
			InstructorResourceService resources,
			CourseThumbnailService thumbnails,
			ResourceUploadService uploads,
			CurrentUserProvider currentUser) {
		this.resources = resources;
		this.thumbnails = thumbnails;
		this.uploads = uploads;
		this.currentUser = currentUser;
	}

	@GetMapping("/lessons/{lessonId}/resources")
	public List<LessonResourceQueryService.ResourceSnapshot> list(@PathVariable long lessonId) {
		return resources.list(lessonId, actorId());
	}

	@PostMapping("/lessons/{lessonId}/resources/links")
	@ResponseStatus(HttpStatus.CREATED)
	public ResourceUploadService.ResourceDto createLink(
			@PathVariable long lessonId, @Valid @RequestBody LinkRequest request) {
		return resources.createLink(lessonId, actorId(),
				new InstructorResourceService.LinkRequest(request.url(), request.title()));
	}

	@PatchMapping("/resources/{resourceId}")
	public ResourceUploadService.ResourceDto update(
			@PathVariable long resourceId, @Valid @RequestBody UpdateRequest request) {
		return resources.update(resourceId, actorId(),
				new InstructorResourceService.UpdateRequest(request.title(), request.url()));
	}

	@DeleteMapping("/resources/{resourceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long resourceId) {
		resources.delete(resourceId, actorId());
	}

	@PutMapping("/lessons/{lessonId}/resources/order")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reorder(@PathVariable long lessonId, @Valid @RequestBody OrderRequest request) {
		resources.reorder(lessonId, actorId(), request.resourceIds());
	}

	@GetMapping("/lessons/{lessonId}/expected-resources")
	public List<LessonResourceQueryService.ExpectedResourceSnapshot> listExpected(@PathVariable long lessonId) {
		return resources.listExpected(lessonId, actorId());
	}

	@PutMapping("/lessons/{lessonId}/expected-resources")
	public List<LessonResourceQueryService.ExpectedResourceSnapshot> replaceExpected(
			@PathVariable long lessonId, @Valid @RequestBody List<ExpectedRequest> request) {
		return resources.replaceExpected(lessonId, actorId(), request.stream()
				.map(item -> new InstructorResourceService.ExpectedRequest(
						item.resourceType(), item.minCount(), item.label()))
				.toList());
	}

	@GetMapping("/resources/{resourceId}/download-url")
	public InstructorResourceService.DownloadUrl downloadUrl(@PathVariable long resourceId) {
		return resources.downloadUrl(resourceId, actorId());
	}

	@PostMapping("/courses/{courseId}/thumbnail/uploads")
	@ResponseStatus(HttpStatus.CREATED)
	public ResourceUploadService.UploadCreated uploadThumbnail(
			@PathVariable long courseId, @Valid @RequestBody ThumbnailUploadRequest request) {
		ResourceUploadService.UploadRequest upload = new ResourceUploadService.UploadRequest(
				request.filename(), request.mimeType(), request.sizeBytes(), ResourceType.IMAGE, request.title());
		return uploads.createThumbnailUpload(courseId, actorId(), upload);
	}

	@GetMapping("/courses/{courseId}/thumbnail")
	public CourseThumbnailService.ThumbnailDto getThumbnail(@PathVariable long courseId) {
		return thumbnails.get(courseId, actorId());
	}

	@DeleteMapping("/courses/{courseId}/thumbnail")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteThumbnail(@PathVariable long courseId) {
		thumbnails.delete(courseId, actorId());
	}

	private long actorId() {
		return currentUser.requireCurrentUser().id();
	}

	public record LinkRequest(@NotBlank @Size(max = 2048) String url, @NotBlank @Size(max = 255) String title) {
	}

	public record UpdateRequest(@Size(max = 255) String title, @Size(max = 2048) String url) {
	}

	public record OrderRequest(@NotNull List<Long> resourceIds) {
	}

	public record ExpectedRequest(
			@NotNull ResourceType resourceType,
			@Positive int minCount,
			@Size(max = 80) String label) {
	}

	public record ThumbnailUploadRequest(
			@NotBlank @Size(max = 255) String filename,
			@NotBlank @Size(max = 255) String mimeType,
			@Positive long sizeBytes,
			@Size(max = 255) String title) {
	}
}
