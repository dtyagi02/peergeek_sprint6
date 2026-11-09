package com.peergeek.review;

import com.peergeek.common.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/courses")
public class PublicReviewController {

	private final ReviewService reviewService;

	public PublicReviewController(ReviewService reviewService) {
		this.reviewService = reviewService;
	}

	@GetMapping("/{courseId}/reviews/summary")
	public ResponseEntity<CourseReviewSummaryDto> getCourseReviewSummary(
			@PathVariable("courseId") long courseId) {
		CourseReviewSummaryDto summary = reviewService.getCourseReviewSummary(courseId);
		return ResponseEntity.ok(summary);
	}

	@GetMapping("/{courseId}/reviews")
	public ResponseEntity<PageResponse<ReviewDto>> getCourseReviews(
			@PathVariable("courseId") long courseId,
			@RequestParam(value = "page", required = false, defaultValue = "0") int page,
			@RequestParam(value = "size", required = false, defaultValue = "10") int size,
			@RequestParam(value = "sort", required = false, defaultValue = "recent") String sort,
			@RequestParam(value = "rating", required = false) Integer rating,
			@RequestParam(value = "search", required = false) String search) {
		PageResponse<ReviewDto> response = reviewService.getCourseReviews(courseId, page, size, sort, rating, search);
		return ResponseEntity.ok(response);
	}
}
