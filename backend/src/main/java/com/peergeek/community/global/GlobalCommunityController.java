package com.peergeek.community.global;

import java.util.List;
import com.peergeek.community.global.dto.CommunityUserBlockStatusDto;
import com.peergeek.community.global.dto.CreateGlobalMessageRequest;
import com.peergeek.community.global.dto.DeleteGlobalMessageRequest;
import com.peergeek.community.global.dto.GlobalCommunityMessageDto;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import com.peergeek.user.UserRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community/global")
public class GlobalCommunityController {

	private final GlobalCommunityService communityService;
	private final CurrentUserProvider currentUserProvider;

	public GlobalCommunityController(
			GlobalCommunityService communityService,
			CurrentUserProvider currentUserProvider) {
		this.communityService = communityService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/messages")
	public List<GlobalCommunityMessageDto> getMessages(
			@RequestParam(required = false) Long afterId,
			@RequestParam(required = false) Long beforeId,
			@RequestParam(defaultValue = "50") int limit) {
		CurrentUser currentUser = currentUserProvider.requireCurrentUser();
		boolean isAdmin = currentUser.role() == UserRole.ADMIN;
		return communityService.getMessages(afterId, beforeId, limit, currentUser.id(), isAdmin);
	}

	@PostMapping("/messages")
	@ResponseStatus(HttpStatus.CREATED)
	public GlobalCommunityMessageDto postMessage(@Valid @RequestBody CreateGlobalMessageRequest request) {
		CurrentUser currentUser = currentUserProvider.requireCurrentUser();
		return communityService.postMessage(currentUser.id(), request);
	}

	@DeleteMapping("/messages/{messageId}")
	public ResponseEntity<Void> deleteMessage(
			@PathVariable Long messageId,
			@RequestBody(required = false) DeleteGlobalMessageRequest request) {
		CurrentUser currentUser = currentUserProvider.requireCurrentUser();
		boolean isAdmin = currentUser.role() == UserRole.ADMIN;
		communityService.deleteMessage(messageId, currentUser.id(), isAdmin, request);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/status")
	public CommunityUserBlockStatusDto getStatus() {
		CurrentUser currentUser = currentUserProvider.requireCurrentUser();
		return communityService.getUserBlockStatus(currentUser.id());
	}
}
