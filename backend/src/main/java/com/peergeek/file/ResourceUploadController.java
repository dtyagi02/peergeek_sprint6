package com.peergeek.file;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.peergeek.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instructor")
public class ResourceUploadController {
	private final ResourceUploadService resources;
	private final CurrentUserProvider currentUser;

	public ResourceUploadController(ResourceUploadService resources, CurrentUserProvider currentUser) {
		this.resources = resources;
		this.currentUser = currentUser;
	}

	@GetMapping("/resources/limits")
	public java.util.Map<String, Object> limits() {
		return resources.limits();
	}

	@PostMapping("/lessons/{lessonId}/resources/uploads")
	@ResponseStatus(HttpStatus.CREATED)
	public ResourceUploadService.UploadCreated createLessonUpload(
			@PathVariable long lessonId, @Valid @RequestBody UploadRequest request) {
		return resources.createLessonUpload(lessonId, actorId(), request.toServiceRequest());
	}

	@PostMapping("/resources/{resourceId}/upload-url")
	public ResourceUploadService.UploadInstruction uploadUrl(@PathVariable long resourceId) {
		return resources.refreshUploadUrl(resourceId, actorId());
	}

	@PostMapping("/resources/{resourceId}/confirm")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ResourceUploadService.ResourceDto confirm(@PathVariable long resourceId) {
		return resources.confirm(resourceId, actorId());
	}

	@GetMapping("/resources/{resourceId}")
	public ResourceUploadService.ResourceDto get(@PathVariable long resourceId) {
		return resources.get(resourceId, actorId());
	}

	private long actorId() {
		return currentUser.requireCurrentUser().id();
	}

	public record UploadRequest(
			@NotBlank @Size(max = 255) String filename,
			@NotBlank @Size(max = 255) String mimeType,
			@Positive long sizeBytes,
			@NotNull ResourceType type,
			@Size(max = 255) String title) {
		ResourceUploadService.UploadRequest toServiceRequest() {
			return new ResourceUploadService.UploadRequest(filename, mimeType, sizeBytes, type, title);
		}
	}
}
