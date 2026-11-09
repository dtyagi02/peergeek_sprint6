package com.peergeek.community;

import com.peergeek.common.PageResponse;
import com.peergeek.community.dto.CommunityCommentDto;
import com.peergeek.community.dto.CommunityPostDto;
import com.peergeek.community.dto.CreateCommentRequest;
import com.peergeek.community.dto.CreatePostRequest;
import com.peergeek.community.dto.MentionSuggestionDto;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import com.peergeek.user.UserRole;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
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
@RequestMapping("/api/v1/community")
public class CommunityController {

	private final CommunityService communityService;
	private final CurrentUserProvider currentUserProvider;

	public CommunityController(CommunityService communityService, CurrentUserProvider currentUserProvider) {
		this.communityService = communityService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/posts")
	public ResponseEntity<PageResponse<CommunityPostDto>> getPosts(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		int safePage = Math.max(0, page);
		int safeSize = Math.min(50, Math.max(1, size));
		Pageable pageable = PageRequest.of(safePage, safeSize);

		CurrentUser user = currentUserProvider.currentUser().orElse(null);
		Long currentUserId = user != null ? user.id() : null;
		boolean isAdmin = user != null && user.role() == UserRole.ADMIN;

		PageResponse<CommunityPostDto> response = communityService.getPosts(pageable, currentUserId, isAdmin);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(response);
	}

	@PostMapping("/posts")
	public ResponseEntity<CommunityPostDto> createPost(@Valid @RequestBody CreatePostRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		CommunityPostDto post = communityService.createPost(user.id(), request);
		return ResponseEntity.created(URI.create("/api/v1/community/posts/" + post.id()))
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(post);
	}

	@GetMapping("/posts/{postId}")
	public ResponseEntity<CommunityPostDto> getPost(@PathVariable("postId") Long postId) {
		CurrentUser user = currentUserProvider.currentUser().orElse(null);
		Long currentUserId = user != null ? user.id() : null;
		boolean isAdmin = user != null && user.role() == UserRole.ADMIN;

		CommunityPostDto post = communityService.getPost(postId, currentUserId, isAdmin);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(post);
	}

	@DeleteMapping("/posts/{postId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public ResponseEntity<Void> deletePost(@PathVariable("postId") Long postId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		boolean isAdmin = user.role() == UserRole.ADMIN;
		communityService.deletePost(postId, user.id(), isAdmin);
		return ResponseEntity.noContent()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.build();
	}

	@GetMapping("/posts/{postId}/comments")
	public ResponseEntity<List<CommunityCommentDto>> getComments(@PathVariable("postId") Long postId) {
		CurrentUser user = currentUserProvider.currentUser().orElse(null);
		Long currentUserId = user != null ? user.id() : null;
		boolean isAdmin = user != null && user.role() == UserRole.ADMIN;

		List<CommunityCommentDto> comments = communityService.getComments(postId, currentUserId, isAdmin);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(comments);
	}

	@PostMapping("/posts/{postId}/comments")
	public ResponseEntity<CommunityCommentDto> createComment(
			@PathVariable("postId") Long postId,
			@Valid @RequestBody CreateCommentRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		CommunityCommentDto comment = communityService.createComment(postId, user.id(), request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(comment);
	}

	@DeleteMapping("/comments/{commentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public ResponseEntity<Void> deleteComment(@PathVariable("commentId") Long commentId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		boolean isAdmin = user.role() == UserRole.ADMIN;
		communityService.deleteComment(commentId, user.id(), isAdmin);
		return ResponseEntity.noContent()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.build();
	}

	@GetMapping("/users/mention-suggestions")
	public ResponseEntity<List<MentionSuggestionDto>> getMentionSuggestions(
			@RequestParam(name = "q", defaultValue = "") String query) {
		currentUserProvider.requireCurrentUser();
		List<MentionSuggestionDto> suggestions = communityService.getMentionSuggestions(query);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(suggestions);
	}
}
