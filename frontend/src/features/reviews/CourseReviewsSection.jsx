import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "../auth/authStore";
import {
  getCourseReviews,
  getMyReview,
  submitReview,
  deleteReview,
} from "./reviewsApi";
import { getEnrollmentStatus } from "../learn/learningApi";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";

export function CourseReviewsSection({ courseId }) {
  const queryClient = useQueryClient();
  const authStatus = useAuthStore((state) => state.status);
  const isAuthenticated = authStatus === "authenticated";

  const [rating, setRating] = useState(5);
  const [reviewText, setReviewText] = useState("");
  const [formOpen, setFormOpen] = useState(false);

  // Load public reviews
  const reviewsQuery = useQuery({
    queryKey: ["reviews", courseId],
    queryFn: () => getCourseReviews(courseId, { page: 0, size: 10 }),
    enabled: Boolean(courseId),
  });

  // Load enrollment status to check if user can review
  const enrollmentQuery = useQuery({
    queryKey: ["learning", "enrollment", courseId],
    queryFn: () => getEnrollmentStatus(courseId),
    enabled: isAuthenticated && Boolean(courseId),
  });

  // Load user's existing review if authenticated
  const myReviewQuery = useQuery({
    queryKey: ["reviews", "my", courseId],
    queryFn: () => getMyReview(courseId),
    enabled: isAuthenticated && Boolean(courseId),
  });

  const submitMutation = useMutation({
    mutationFn: (data) => submitReview(courseId, data),
    onSuccess: () => {
      setFormOpen(false);
      queryClient.invalidateQueries({ queryKey: ["reviews", courseId] });
      queryClient.invalidateQueries({ queryKey: ["reviews", "summary", courseId] });
      queryClient.invalidateQueries({ queryKey: ["reviews", "my", courseId] });
      queryClient.invalidateQueries({ queryKey: ["public-course", courseId] });
      queryClient.invalidateQueries({ queryKey: ["public-course-preview", courseId] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteReview(courseId),
    onSuccess: () => {
      setFormOpen(false);
      setRating(5);
      setReviewText("");
      queryClient.invalidateQueries({ queryKey: ["reviews", courseId] });
      queryClient.invalidateQueries({ queryKey: ["reviews", "summary", courseId] });
      queryClient.invalidateQueries({ queryKey: ["reviews", "my", courseId] });
      queryClient.invalidateQueries({ queryKey: ["public-course", courseId] });
      queryClient.invalidateQueries({ queryKey: ["public-course-preview", courseId] });
    },
  });

  const isEnrolled =
    enrollmentQuery.data?.enrolled && enrollmentQuery.data?.status === "ACTIVE";
  const isOwner = enrollmentQuery.data?.isOwner;
  const canReview = isAuthenticated && isEnrolled && !isOwner;
  const myReview = myReviewQuery.data;

  const reviews = Array.isArray(reviewsQuery.data?.content)
    ? reviewsQuery.data.content
    : [];
  const totalElements = reviewsQuery.data?.totalElements ?? 0;

  function handleStartEdit() {
    if (myReview) {
      setRating(myReview.rating);
      setReviewText(myReview.reviewText || "");
    }
    setFormOpen(true);
  }

  function handleSubmit(e) {
    e.preventDefault();
    submitMutation.mutate({ rating, reviewText: reviewText.trim() });
  }

  return (
    <section
      aria-labelledby="reviews-heading"
      className="space-y-6 pt-6 border-t border-surface-200"
    >
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2
            id="reviews-heading"
            className="text-2xl font-bold tracking-tight text-ink-900"
          >
            Learner Reviews & Ratings
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            {totalElements > 0
              ? `${totalElements} ${totalElements === 1 ? "review" : "reviews"} from enrolled peers`
              : "No reviews yet. Enrolled learners can share feedback."}
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Link
            to={`/courses/${courseId}/reviews`}
            className="text-xs font-semibold text-brand-700 hover:text-brand-800 transition underline underline-offset-4"
          >
            See all reviews & breakdown →
          </Link>

          {canReview && !formOpen && (
            <Button
              type="button"
              variant="secondary"
              onClick={handleStartEdit}
              className="text-xs"
            >
              {myReview ? "Edit Your Review" : "Write a Review"}
            </Button>
          )}
        </div>
      </div>

      {/* Review Submission / Edit Form */}
      {formOpen && canReview && (
        <form
          onSubmit={handleSubmit}
          className="rounded-xl border border-brand-200 bg-brand-50/30 p-5 space-y-4 shadow-xs"
        >
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-ink-900">
              {myReview
                ? "Update Your Course Review"
                : "Rate & Review this Course"}
            </h3>
            <button
              type="button"
              onClick={() => setFormOpen(false)}
              className="text-xs font-semibold text-slate-500 hover:text-slate-800"
            >
              Cancel
            </button>
          </div>

          {/* Star picker */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Your Rating (1 to 5 stars)
            </label>
            <div
              className="flex gap-1"
              role="radiogroup"
              aria-label="Select star rating"
            >
              {[1, 2, 3, 4, 5].map((star) => (
                <button
                  key={star}
                  type="button"
                  onClick={() => setRating(star)}
                  className={`text-2xl transition hover:scale-110 ${
                    star <= rating ? "text-amber-500" : "text-slate-300"
                  }`}
                  aria-label={`${star} star${star > 1 ? "s" : ""}`}
                >
                  ★
                </button>
              ))}
            </div>
          </div>

          {/* Review Textarea */}
          <div>
            <label
              htmlFor="review-text"
              className="block text-xs font-semibold text-slate-700 mb-1"
            >
              Your Feedback (Optional)
            </label>
            <textarea
              id="review-text"
              rows={3}
              value={reviewText}
              onChange={(e) => setReviewText(e.target.value)}
              placeholder="What did you think of the course content, pacing, and assignments?"
              maxLength={2000}
              className="w-full rounded-lg border border-surface-300 p-2.5 text-sm text-ink-900 placeholder:text-slate-400 focus:border-brand-500 focus:outline-hidden focus:ring-1 focus:ring-brand-500"
            />
          </div>

          {submitMutation.isError && (
            <p className="text-xs font-medium text-danger-600">
              Failed to submit review:{" "}
              {submitMutation.error?.message || "Please try again."}
            </p>
          )}

          <div className="flex items-center justify-between pt-1">
            {myReview ? (
              <Button
                type="button"
                variant="danger"
                onClick={() => deleteMutation.mutate()}
                disabled={deleteMutation.isPending}
                className="text-xs"
              >
                {deleteMutation.isPending ? "Deleting..." : "Delete Review"}
              </Button>
            ) : (
              <span />
            )}

            <Button
              type="submit"
              disabled={submitMutation.isPending}
              className="text-xs"
            >
              {submitMutation.isPending
                ? "Saving..."
                : myReview
                  ? "Save Changes"
                  : "Submit Review"}
            </Button>
          </div>
        </form>
      )}

      {/* Reviews List */}
      {reviewsQuery.isLoading ? (
        <div className="flex justify-center py-8">
          <Spinner label="Loading reviews..." />
        </div>
      ) : reviews.length === 0 ? (
        <div className="rounded-xl border border-surface-200 bg-surface-50 p-8 text-center text-slate-500 text-sm">
          No learner reviews submitted yet.
        </div>
      ) : (
        <div className="space-y-4">
          {reviews.map((rev) => (
            <article
              key={rev.id}
              className="rounded-xl border border-surface-200 bg-white p-5 shadow-xs space-y-2"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  {rev.reviewerAvatarUrl ? (
                    <img
                      src={rev.reviewerAvatarUrl}
                      alt={rev.reviewerName || "Reviewer"}
                      className="h-8 w-8 rounded-full object-cover border border-surface-200"
                    />
                  ) : (
                    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-100 text-xs font-bold text-brand-700">
                      {rev.reviewerName
                        ? rev.reviewerName.charAt(0).toUpperCase()
                        : "L"}
                    </div>
                  )}
                  <div>
                    <div className="flex items-center gap-1.5">
                      <h4 className="text-sm font-semibold text-ink-900">
                        {rev.reviewerName}
                      </h4>
                      {rev.reviewerUsername && (
                        <Link
                          to={`/profile/${rev.reviewerUsername}`}
                          className="text-xs text-brand-600 hover:underline"
                        >
                          @{rev.reviewerUsername}
                        </Link>
                      )}
                    </div>
                    <span className="text-xs text-slate-400">
                      {new Date(rev.createdAt).toLocaleDateString()}
                    </span>
                  </div>
                </div>

                <div
                  className="flex text-amber-500 text-sm"
                  aria-label={`${rev.rating} out of 5 stars`}
                >
                  {"★".repeat(rev.rating)}
                  {"☆".repeat(5 - rev.rating)}
                </div>
              </div>

              {rev.reviewText && (
                <p className="text-sm text-ink-700 whitespace-pre-wrap pt-1">
                  {rev.reviewText}
                </p>
              )}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
