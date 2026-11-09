package com.peergeek.review;

import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/reviews")
public class ReviewController {

	private final ReviewService reviewService;
	private final CurrentUserProvider currentUserProvider;

	public ReviewController(ReviewService reviewService, CurrentUserProvider currentUserProvider) {
		this.reviewService = reviewService;
		this.currentUserProvider = currentUserProvider;
	}

	@PostMapping
	public ResponseEntity<ReviewDto> submitReview(
			@PathVariable("courseId") long courseId,
			@Valid @RequestBody CreateReviewRequest request) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		ReviewDto dto = reviewService.submitReview(courseId, user.id(), request);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(dto);
	}

	@GetMapping("/my")
	public ResponseEntity<ReviewDto> getMyReview(@PathVariable("courseId") long courseId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		return reviewService.getMyReview(courseId, user.id())
				.map(dto -> ResponseEntity.ok()
						.header(HttpHeaders.CACHE_CONTROL, "no-store")
						.body(dto))
				.orElseGet(() -> ResponseEntity.noContent()
						.header(HttpHeaders.CACHE_CONTROL, "no-store")
						.build());
	}

	@DeleteMapping
	public ResponseEntity<Void> deleteReview(@PathVariable("courseId") long courseId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		reviewService.deleteReview(courseId, user.id());
		return ResponseEntity.noContent()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.build();
	}
}
