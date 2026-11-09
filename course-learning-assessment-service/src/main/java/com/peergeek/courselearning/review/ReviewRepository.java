package com.peergeek.courselearning.review;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {

    Optional<ReviewEntity> findByCourseIdAndUserIdAndDeletedAtIsNull(Long courseId, Long userId);

    Optional<ReviewEntity> findByCourseIdAndUserId(Long courseId, Long userId);

    Page<ReviewEntity> findByCourseIdAndDeletedAtIsNull(Long courseId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM ReviewEntity r WHERE r.courseId = :courseId AND r.deletedAt IS NULL")
    Double getAverageRating(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(r) FROM ReviewEntity r WHERE r.courseId = :courseId AND r.deletedAt IS NULL")
    int countActiveReviews(@Param("courseId") Long courseId);

    @Query("SELECT AVG(r.rating) FROM ReviewEntity r WHERE r.courseId IN :courseIds AND r.deletedAt IS NULL")
    Double getAverageRatingForCourses(@Param("courseIds") java.util.Collection<Long> courseIds);

    @Query("SELECT COUNT(r) FROM ReviewEntity r WHERE r.courseId IN :courseIds AND r.deletedAt IS NULL")
    int countActiveReviewsForCourses(@Param("courseIds") java.util.Collection<Long> courseIds);

    @Query("SELECT r.courseId, AVG(r.rating), COUNT(r) FROM ReviewEntity r WHERE r.courseId IN :courseIds AND r.deletedAt IS NULL GROUP BY r.courseId")
    List<Object[]> getCourseRatingSummaries(@Param("courseIds") java.util.Collection<Long> courseIds);

    @Query("SELECT r.rating, COUNT(r) FROM ReviewEntity r WHERE r.courseId = :courseId AND r.deletedAt IS NULL GROUP BY r.rating")
    List<Object[]> getRatingCountsRaw(@Param("courseId") Long courseId);

    @Query(
            value = "SELECT r FROM ReviewEntity r LEFT JOIN com.peergeek.courselearning.profile.LearningUserProfileEntity u ON r.userId = u.id "
                    + "WHERE r.courseId = :courseId AND r.deletedAt IS NULL "
                    + "AND (:rating IS NULL OR r.rating = :rating) "
                    + "AND (:search IS NULL OR :search = '' OR LOWER(r.reviewText) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')))",
            countQuery = "SELECT COUNT(r) FROM ReviewEntity r LEFT JOIN com.peergeek.courselearning.profile.LearningUserProfileEntity u ON r.userId = u.id "
                    + "WHERE r.courseId = :courseId AND r.deletedAt IS NULL "
                    + "AND (:rating IS NULL OR r.rating = :rating) "
                    + "AND (:search IS NULL OR :search = '' OR LOWER(r.reviewText) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ReviewEntity> findReviews(
            @Param("courseId") Long courseId,
            @Param("rating") Integer rating,
            @Param("search") String search,
            Pageable pageable);
}
