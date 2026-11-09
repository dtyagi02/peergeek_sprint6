package com.peergeek.user;

import jakarta.validation.Valid;
import java.util.Map;
import com.peergeek.security.CurrentUserProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
	private final UserService userService;
	private final CurrentUserProvider currentUserProvider;

	public UserController(UserService userService, CurrentUserProvider currentUserProvider) {
		this.userService = userService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/me")
	public UserProfileDto currentProfile() {
		return userService.currentProfile(currentUserProvider.requireCurrentUser().id());
	}

	@GetMapping("/me/profile")
	public UserFullProfileDto currentFullProfile() {
		return userService.getFullProfile(
				currentUserProvider.requireCurrentUser().id(),
				currentUserProvider.currentUser());
	}

	@PatchMapping("/me")
	public UserProfileDto updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
		return userService.updateProfile(currentUserProvider.requireCurrentUser().id(), request);
	}

	@PutMapping("/me/profile")
	public UserFullProfileDto updateFullProfile(@Valid @RequestBody UpdateProfileRequest request) {
		userService.updateProfile(currentUserProvider.requireCurrentUser().id(), request);
		return userService.getFullProfile(
				currentUserProvider.requireCurrentUser().id(),
				currentUserProvider.currentUser());
	}

	@PatchMapping("/me/mode")
	public UserProfileDto updateMode(@Valid @RequestBody UpdateModeRequest request) {
		return userService.updateMode(currentUserProvider.requireCurrentUser().id(), request.mode());
	}

	@GetMapping("/{id}/public")
	public ResponseEntity<PublicProfileResponseDto> publicProfile(@PathVariable Long id) {
		return ResponseEntity.ok(PublicProfileResponseDto.from(userService.publicProfile(id)));
	}

	@GetMapping("/by-username/{username}")
	public ResponseEntity<PublicProfileResponseDto> publicProfileByUsername(@PathVariable String username) {
		return ResponseEntity.ok(PublicProfileResponseDto.from(userService.publicProfileByUsername(username)));
	}

	@GetMapping("/{userId}/profile")
	public ResponseEntity<UserFullProfileDto> getUserProfile(@PathVariable Long userId) {
		return ResponseEntity.ok(userService.getFullProfile(userId, currentUserProvider.currentUser()));
	}

	@GetMapping("/by-username/{username}/profile")
	public ResponseEntity<UserFullProfileDto> getUserProfileByUsername(@PathVariable String username) {
		return ResponseEntity.ok(userService.getFullProfileByUsername(username, currentUserProvider.currentUser()));
	}

	// Education endpoints
	@PostMapping("/me/education")
	@ResponseStatus(HttpStatus.CREATED)
	public UserEducationDto addEducation(@Valid @RequestBody UpsertEducationRequest request) {
		return userService.addEducation(currentUserProvider.requireCurrentUser().id(), request);
	}

	@PutMapping("/me/education/{educationId}")
	public UserEducationDto updateEducation(
			@PathVariable Long educationId, @Valid @RequestBody UpsertEducationRequest request) {
		return userService.updateEducation(currentUserProvider.requireCurrentUser().id(), educationId, request);
	}

	@DeleteMapping("/me/education/{educationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteEducation(@PathVariable Long educationId) {
		userService.deleteEducation(currentUserProvider.requireCurrentUser().id(), educationId);
	}

	// Projects endpoints
	@PostMapping("/me/projects")
	@ResponseStatus(HttpStatus.CREATED)
	public UserProjectDto addProject(@Valid @RequestBody UpsertProjectRequest request) {
		return userService.addProject(currentUserProvider.requireCurrentUser().id(), request);
	}

	@PutMapping("/me/projects/{projectId}")
	public UserProjectDto updateProject(
			@PathVariable Long projectId, @Valid @RequestBody UpsertProjectRequest request) {
		return userService.updateProject(currentUserProvider.requireCurrentUser().id(), projectId, request);
	}

	@DeleteMapping("/me/projects/{projectId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteProject(@PathVariable Long projectId) {
		userService.deleteProject(currentUserProvider.requireCurrentUser().id(), projectId);
	}

	// Avatar endpoints
	@PostMapping("/me/avatar")
	public Map<String, String> uploadAvatar(@RequestParam("file") MultipartFile file) {
		return userService.uploadAvatar(currentUserProvider.requireCurrentUser().id(), file);
	}

	@DeleteMapping("/me/avatar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteAvatar() {
		userService.deleteAvatar(currentUserProvider.requireCurrentUser().id());
	}

	@GetMapping("/{userId}/avatar")
	public ResponseEntity<InputStreamResource> getAvatar(@PathVariable Long userId) {
		UserService.AvatarStream stream = userService.getAvatarStream(userId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
				.contentType(MediaType.parseMediaType(stream.contentType()))
				.contentLength(stream.contentLength())
				.body(new InputStreamResource(stream.stream()));
	}
}
