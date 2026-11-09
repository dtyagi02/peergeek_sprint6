package com.peergeek.community.global;

import com.peergeek.community.common.PageResponse;
import com.peergeek.community.global.dto.AdminCommunityBlockedUserDto;
import com.peergeek.community.global.dto.AdminGlobalCommunityMessageDto;
import com.peergeek.community.global.dto.BlockCommunityUserRequest;
import com.peergeek.community.global.dto.DeleteGlobalMessageRequest;
import com.peergeek.community.security.CurrentUser;
import com.peergeek.community.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/community")
@PreAuthorize("hasRole('ADMIN')")
public class AdminGlobalCommunityController {

	private final GlobalCommunityService communityService;
	private final CurrentUserProvider currentUserProvider;

	public AdminGlobalCommunityController(
			GlobalCommunityService communityService,
			CurrentUserProvider currentUserProvider) {
		this.communityService = communityService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/messages")
	public ResponseEntity<PageResponse<AdminGlobalCommunityMessageDto>> getMessages(
			@RequestParam(required = false, defaultValue = "true") Boolean includeDeleted,
			@RequestParam(required = false) Long userId,
			@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(communityService.getAdminMessages(includeDeleted, userId, query, page, size));
	}

	@DeleteMapping("/messages/{messageId}")
	public ResponseEntity<Void> deleteMessage(
			@PathVariable Long messageId,
			@RequestBody(required = false) DeleteGlobalMessageRequest request) {
		CurrentUser admin = currentUserProvider.requireCurrentUser();
		communityService.adminDeleteMessage(messageId, admin.id(), request);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/users/{userId}/block")
	public ResponseEntity<AdminCommunityBlockedUserDto> blockUser(
			@PathVariable Long userId,
			@Valid @RequestBody(required = false) BlockCommunityUserRequest request) {
		CurrentUser admin = currentUserProvider.requireCurrentUser();
		return ResponseEntity.ok(communityService.blockUser(userId, admin.id(), request));
	}

	@PostMapping("/users/{userId}/unblock")
	public ResponseEntity<Void> unblockUser(
			@PathVariable Long userId,
			@Valid @RequestBody(required = false) BlockCommunityUserRequest request) {
		CurrentUser admin = currentUserProvider.requireCurrentUser();
		communityService.unblockUser(userId, admin.id(), request);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/blocked-users")
	public ResponseEntity<List<AdminCommunityBlockedUserDto>> getBlockedUsers() {
		return ResponseEntity.ok(communityService.getBlockedUsers());
	}
}

