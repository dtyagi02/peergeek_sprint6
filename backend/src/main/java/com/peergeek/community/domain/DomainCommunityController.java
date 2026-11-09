package com.peergeek.community.domain;

import com.peergeek.common.PageResponse;
import com.peergeek.community.domain.dto.DomainChatMessageDto;
import com.peergeek.community.domain.dto.DomainCommunityDto;
import com.peergeek.community.domain.dto.DomainMemberDto;
import com.peergeek.community.domain.dto.SendDomainMessageRequest;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.peergeek.user.UserRole;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/domains")
public class DomainCommunityController {

	private final DomainCommunityService domainCommunityService;
	private final CurrentUserProvider currentUserProvider;

	public DomainCommunityController(
			DomainCommunityService domainCommunityService,
			CurrentUserProvider currentUserProvider) {
		this.domainCommunityService = domainCommunityService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/communities")
	public ResponseEntity<List<DomainCommunityDto>> listCommunities() {
		CurrentUser user = currentUserProvider.currentUser().orElse(null);
		Long currentUserId = user != null ? user.id() : null;

		List<DomainCommunityDto> list = domainCommunityService.listDomainCommunities(currentUserId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(list);
	}

	@GetMapping("/{slug}/community")
	public ResponseEntity<DomainCommunityDto> getCommunity(@PathVariable("slug") String slug) {
		CurrentUser user = currentUserProvider.currentUser().orElse(null);
		Long currentUserId = user != null ? user.id() : null;

		DomainCommunityDto community = domainCommunityService.getDomainCommunity(slug, currentUserId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(community);
	}

	@PostMapping("/{slug}/community/join")
	public ResponseEntity<DomainCommunityDto> joinCommunity(@PathVariable("slug") String slug) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		DomainCommunityDto community = domainCommunityService.joinCommunity(slug, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(community);
	}

	@PostMapping("/{slug}/community/leave")
	public ResponseEntity<DomainCommunityDto> leaveCommunity(@PathVariable("slug") String slug) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		DomainCommunityDto community = domainCommunityService.leaveCommunity(slug, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(community);
	}

	@GetMapping("/{slug}/community/members")
	public ResponseEntity<PageResponse<DomainMemberDto>> getMembers(
			@PathVariable("slug") String slug,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		int safePage = Math.max(0, page);
		int safeSize = Math.min(50, Math.max(1, size));
		Pageable pageable = PageRequest.of(safePage, safeSize);

		PageResponse<DomainMemberDto> response = domainCommunityService.getMembers(slug, pageable);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(response);
	}

	@GetMapping("/{slug}/community/messages")
	public ResponseEntity<List<DomainChatMessageDto>> getMessages(
			@PathVariable("slug") String slug,
			@RequestParam(name = "beforeId", required = false) Long beforeId,
			@RequestParam(name = "afterId", required = false) Long afterId,
			@RequestParam(name = "limit", defaultValue = "50") int limit) {
		CurrentUser user = currentUserProvider.currentUser().orElse(null);
		Long currentUserId = user != null ? user.id() : null;

		List<DomainChatMessageDto> messages;
		if (afterId != null) {
			messages = domainCommunityService.getNewMessagesAfter(slug, afterId, currentUserId);
		} else {
			messages = domainCommunityService.getMessages(slug, beforeId, limit, currentUserId);
		}

		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(messages);
	}

	@PostMapping("/{slug}/community/messages")
	public ResponseEntity<DomainChatMessageDto> sendMessage(
			@PathVariable("slug") String slug,
			@Valid @RequestBody SendDomainMessageRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		DomainChatMessageDto message = domainCommunityService.sendMessage(slug, user.id(), request);

		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/domains/" + slug + "/community/messages/" + message.id()))
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(message);
	}

	@DeleteMapping("/{slug}/community/messages/{messageId}")
	public ResponseEntity<Void> deleteMessage(
			@PathVariable("slug") String slug,
			@PathVariable("messageId") Long messageId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		boolean isAdmin = user.role() == UserRole.ADMIN;
		domainCommunityService.deleteMessage(slug, messageId, user.id(), isAdmin);
		return ResponseEntity.noContent().build();
	}
}
