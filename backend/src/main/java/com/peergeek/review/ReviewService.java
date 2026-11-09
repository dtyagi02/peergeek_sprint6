package com.peergeek.review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.peergeek.common.PageResponse;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStatus;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.discovery.CourseStatsRecorder;
import com.peergeek.enrollment.EnrollmentRepository;
import com.peergeek.enrollment.EnrollmentStatus;
import com.peergeek.exception.NotFoundException;
import com.peergeek.user.PublicProfileDto;
import com.peergeek.user.UserService;
import com.peergeek.audit.AuditAction;
import com.peergeek.audit.AuditEvent;
import com.peergeek.audit.AuditService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ReviewService {

	private final ReviewRepository reviewRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final CourseStructureQueryService courseStructureQueryService;
	private final CourseStatsRecorder courseStatsRecorder;
	private final UserService userService;
	private final ApplicationEventPublisher eventPublisher;
	private final AuditService auditService;

	public ReviewService(
			ReviewRepository reviewRepository,
			EnrollmentRepository enrollmentRepository,
			CourseStructureQueryService courseStructureQueryService,
			CourseStatsRecorder courseStatsRecorder,
			UserService userService,
			ApplicationEventPublisher eventPublisher,
			AuditService auditService) {
		this.reviewRepository = reviewRepository;
		this.enrollmentRepository = enrollmentRepository;
		this.courseStructureQueryService = courseStructureQueryService;
		this.courseStatsRecorder = courseStatsRecorder;
		this.userService = userService;
		this.eventPublisher = eventPublisher;
		this.auditService = auditService;
	}

	@Transactional
	public ReviewDto submitReview(long courseId, long userId, CreateReviewRequest request) {
		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("Course not found"));

		if (metadata.deleted() || metadata.status() != CourseStatus.PUBLISHED) {
			throw new NotFoundException("Course not found");
		}

		if (metadata.ownerId().equals(userId)) {
			throw new CannotReviewOwnCourseException();
		}

		boolean isEnrolled = enrollmentRepository.existsByUserIdAndCourseIdAndStatus(
				userId, courseId, EnrollmentStatus.ACTIVE);
		if (!isEnrolled) {
			throw new ReviewNotEnrolledException();
		}

		Optional<ReviewEntity> existingOpt = reviewRepository.findByCourseIdAndUserId(courseId, userId);
		ReviewEntity review;
		boolean isNew = false;

		if (existingOpt.isPresent()) {
			review = existingOpt.get();
			review.setRating(request.rating());
			review.setReviewText(request.reviewText());
			if (review.getDeletedAt() != null) {
				review.setDeletedAt(null);
				isNew = true;
			}
		} else {
			review = new ReviewEntity(courseId, userId, request.rating(), request.reviewText());
			isNew = true;
		}

		review = reviewRepository.save(review);

		updateStats(courseId);

		if (isNew) {
			ReviewCreatedEvent event = new ReviewCreatedEvent(
					review.getId(),
					courseId,
					userId,
					metadata.ownerId(),
					review.getRating(),
					review.getReviewText(),
					review.getCreatedAt());
			publishAfterCommit(event);

			auditService.record(new AuditEvent(
					userId,
					AuditAction.REVIEW_CREATED,
					"REVIEW",
					String.valueOf(review.getId()),
					Map.of(
							"courseId", courseId,
							"rating", request.rating(),
							"reviewText", request.reviewText() != null ? request.reviewText() : "")));
		}

		PublicProfileDto profile = userService.getPublicProfiles(List.of(userId)).get(userId);
		String reviewerName = profile != null ? profile.displayName() : "Learner";
		String reviewerUsername = profile != null ? profile.username() : null;
		String reviewerAvatarUrl = profile != null ? profile.avatarUrl() : null;

		return new ReviewDto(
				review.getId(),
				review.getCourseId(),
				review.getUserId(),
				reviewerName,
				reviewerUsername,
				reviewerAvatarUrl,
				review.getRating(),
				review.getReviewText(),
				review.getCreatedAt(),
				review.getUpdatedAt());
	}

	@Transactional(readOnly = true)
	public Optional<ReviewDto> getMyReview(long courseId, long userId) {
		return reviewRepository.findByCourseIdAndUserIdAndDeletedAtIsNull(courseId, userId)
				.map(review -> {
					PublicProfileDto profile = userService.getPublicProfiles(List.of(userId)).get(userId);
					String reviewerName = profile != null ? profile.displayName() : "Learner";
					String reviewerUsername = profile != null ? profile.username() : null;
					String reviewerAvatarUrl = profile != null ? profile.avatarUrl() : null;
					return new ReviewDto(
							review.getId(),
							review.getCourseId(),
							review.getUserId(),
							reviewerName,
							reviewerUsername,
							reviewerAvatarUrl,
							review.getRating(),
							review.getReviewText(),
							review.getCreatedAt(),
							review.getUpdatedAt());
				});
	}

	@Transactional(readOnly = true)
	public CourseReviewSummaryDto getCourseReviewSummary(long courseId) {
		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("Course not found"));

		if (metadata.deleted() || metadata.status() != CourseStatus.PUBLISHED) {
			throw new NotFoundException("Course not found");
		}

		Double avg = reviewRepository.getAverageRating(courseId);
		int total = reviewRepository.countActiveReviews(courseId);
		double roundedAvg = 0.0;
		if (avg != null && total > 0) {
			roundedAvg = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue();
		}

		Map<Integer, Integer> distribution = new LinkedHashMap<>();
		for (int star = 5; star >= 1; star--) {
			distribution.put(star, 0);
		}
		List<Object[]> rows = reviewRepository.getRatingCountsRaw(courseId);
		for (Object[] row : rows) {
			int rating = ((Number) row[0]).intValue();
			int count = ((Number) row[1]).intValue();
			if (rating >= 1 && rating <= 5) {
				distribution.put(rating, count);
			}
		}

		Map<Integer, Double> percentages = new LinkedHashMap<>();
		for (int star = 5; star >= 1; star--) {
			int count = distribution.get(star);
			double pct = total > 0 ? (count * 100.0) / total : 0.0;
			percentages.put(star, BigDecimal.valueOf(pct).setScale(1, RoundingMode.HALF_UP).doubleValue());
		}

		return new CourseReviewSummaryDto(courseId, roundedAvg, total, distribution, percentages);
	}

	@Transactional(readOnly = true)
	public PageResponse<ReviewDto> getCourseReviews(long courseId, int page, int size) {
		return getCourseReviews(courseId, page, size, "recent", null, null);
	}

	@Transactional(readOnly = true)
	public PageResponse<ReviewDto> getCourseReviews(
			long courseId, int page, int size, String sort, Integer rating, String search) {
		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("Course not found"));

		if (metadata.deleted() || metadata.status() != CourseStatus.PUBLISHED) {
			throw new NotFoundException("Course not found");
		}

		int safePage = Math.max(0, page);
		int safeSize = Math.clamp(size, 1, 50);

		Sort sortObj;
		if ("highest".equalsIgnoreCase(sort)) {
			sortObj = Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"));
		} else if ("lowest".equalsIgnoreCase(sort)) {
			sortObj = Sort.by(Sort.Direction.ASC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"));
		} else {
			sortObj = Sort.by(Sort.Direction.DESC, "createdAt");
		}

		String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

		Page<ReviewEntity> pageResult = reviewRepository.findReviews(
				courseId, rating, cleanSearch, PageRequest.of(safePage, safeSize, sortObj));

		Set<Long> userIds = pageResult.getContent().stream()
				.map(ReviewEntity::getUserId)
				.collect(Collectors.toSet());

		Map<Long, PublicProfileDto> profiles = userService.getPublicProfiles(userIds);

		return PageResponse.from(pageResult.map(review -> {
			PublicProfileDto profile = profiles.get(review.getUserId());
			String reviewerName = profile != null ? profile.displayName() : "Learner";
			String reviewerUsername = profile != null ? profile.username() : null;
			String reviewerAvatarUrl = profile != null ? profile.avatarUrl() : null;
			return new ReviewDto(
					review.getId(),
					review.getCourseId(),
					review.getUserId(),
					reviewerName,
					reviewerUsername,
					reviewerAvatarUrl,
					review.getRating(),
					review.getReviewText(),
					review.getCreatedAt(),
					review.getUpdatedAt());
		}));
	}

	@Transactional
	public void deleteReview(long courseId, long userId) {
		ReviewEntity review = reviewRepository.findByCourseIdAndUserIdAndDeletedAtIsNull(courseId, userId)
				.orElseThrow(() -> new NotFoundException("Review not found"));

		review.setDeletedAt(Instant.now());
		reviewRepository.save(review);

		updateStats(courseId);
	}

	private void updateStats(long courseId) {
		Double avg = reviewRepository.getAverageRating(courseId);
		int count = reviewRepository.countActiveReviews(courseId);
		BigDecimal avgBd = (avg != null && count > 0) ? BigDecimal.valueOf(avg) : null;
		courseStatsRecorder.recordRatingStats(courseId, avgBd, count);
	}

	private void publishAfterCommit(Object event) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					eventPublisher.publishEvent(event);
				}
			});
		} else {
			eventPublisher.publishEvent(event);
		}
	}
}
