import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "../auth/authStore";
import { getPublicCoursePreview } from "../discovery/discoveryApi";
import { getEnrollmentStatus } from "../learn/learningApi";
import {
  deleteReview,
  getCourseReviews,
  getCourseReviewSummary,
  getMyReview,
  submitReview,
} from "./reviewsApi";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";

export function CourseReviewsPage() {
  const { courseId } = useParams();
  const parsedCourseId = Number(courseId);
  const queryClient = useQueryClient();

  const authStatus = useAuthStore((state) => state.status);
  const currentUser = useAuthStore((state) => state.user);
  const isAuthenticated = authStatus === "authenticated";

  // Filter & Search states
  const [selectedRating, setSelectedRating] = useState(null);
  const [sortOrder, setSortOrder] = useState("recent");
  const [searchQuery, setSearchQuery] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [page, setPage] = useState(0);

  // Form states
  const [formOpen, setFormOpen] = useState(false);
  const [formRating, setFormRating] = useState(5);
  const [formText, setFormText] = useState("");
  const [formError, setFormError] = useState(null);

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchQuery);
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  // Load course details
  const courseQuery = useQuery({
    queryKey: ["public-course-preview", parsedCourseId],
    queryFn: () => getPublicCoursePreview(parsedCourseId),
    enabled: Boolean(parsedCourseId),
    staleTime: 60 * 1000,
  });

  // Load review summary
  const summaryQuery = useQuery({
    queryKey: ["reviews", "summary", parsedCourseId],
    queryFn: () => getCourseReviewSummary(parsedCourseId),
    enabled: Boolean(parsedCourseId),
  });

  // Load enrollment status
  const enrollmentQuery = useQuery({
    queryKey: ["learning", "enrollment", parsedCourseId],
    queryFn: () => getEnrollmentStatus(parsedCourseId),
    enabled: isAuthenticated && Boolean(parsedCourseId),
  });

  // Load user's review if authenticated
  const myReviewQuery = useQuery({
    queryKey: ["reviews", "my", parsedCourseId],
    queryFn: () => getMyReview(parsedCourseId),
    enabled: isAuthenticated && Boolean(parsedCourseId),
  });

  // Load paginated reviews
  const reviewsQuery = useQuery({
    queryKey: [
      "reviews",
      parsedCourseId,
      page,
      sortOrder,
      selectedRating,
      debouncedSearch,
    ],
    queryFn: () =>
      getCourseReviews(parsedCourseId, {
        page,
        size: 10,
        sort: sortOrder,
        rating: selectedRating,
        search: debouncedSearch,
      }),
    enabled: Boolean(parsedCourseId),
  });

  // Update browser document title
  useEffect(() => {
    if (courseQuery.data?.title) {
      document.title = `Reviews - ${courseQuery.data.title} | PeerGeek`;
    } else {
      document.title = "Course Reviews | PeerGeek";
    }
    return () => {
      document.title = "PeerGeek";
    };
  }, [courseQuery.data?.title]);

  const course = courseQuery.data;
  const summary = summaryQuery.data ?? {
    averageRating: 0.0,
    totalReviews: 0,
    distribution: { 5: 0, 4: 0, 3: 0, 2: 0, 1: 0 },
    distributionPercentages: { 5: 0.0, 4: 0.0, 3: 0.0, 2: 0.0, 1: 0.0 },
  };

  const isEnrolled =
    enrollmentQuery.data?.enrolled && enrollmentQuery.data?.status === "ACTIVE";
  const isOwner = enrollmentQuery.data?.isOwner;
  const canReview = isAuthenticated && isEnrolled && !isOwner;
  const myReview = myReviewQuery.data;

  // Submit mutation
  const submitMutation = useMutation({
    mutationFn: (data) => submitReview(parsedCourseId, data),
    onSuccess: () => {
      setFormOpen(false);
      setFormError(null);
      queryClient.invalidateQueries({
        queryKey: ["reviews", "summary", parsedCourseId],
      });
      queryClient.invalidateQueries({ queryKey: ["reviews", parsedCourseId] });
      queryClient.invalidateQueries({
        queryKey: ["reviews", "my", parsedCourseId],
      });
      queryClient.invalidateQueries({
        queryKey: ["public-course-preview", parsedCourseId],
      });
    },
    onError: (err) => {
      setFormError(err.message || "Failed to submit review.");
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: () => deleteReview(parsedCourseId),
    onSuccess: () => {
      setFormOpen(false);
      setFormRating(5);
      setFormText("");
      setFormError(null);
      queryClient.invalidateQueries({
        queryKey: ["reviews", "summary", parsedCourseId],
      });
      queryClient.invalidateQueries({ queryKey: ["reviews", parsedCourseId] });
      queryClient.invalidateQueries({
        queryKey: ["reviews", "my", parsedCourseId],
      });
      queryClient.invalidateQueries({
        queryKey: ["public-course-preview", parsedCourseId],
      });
    },
    onError: (err) => {
      setFormError(err.message || "Failed to delete review.");
    },
  });

  function handleStartWrite() {
    if (myReview) {
      setFormRating(myReview.rating);
      setFormText(myReview.reviewText || "");
    } else {
      setFormRating(5);
      setFormText("");
    }
    setFormError(null);
    setFormOpen(true);
  }

  function handleSubmitReview(e) {
    e.preventDefault();
    setFormError(null);
    submitMutation.mutate({
      rating: formRating,
      reviewText: formText.trim(),
    });
  }

  function handleRatingBarClick(star) {
    setSelectedRating((prev) => (prev === star ? null : star));
    setPage(0);
  }

  if (courseQuery.isLoading) {
    return (
      <main className="flex min-h-[50vh] items-center justify-center">
        <Spinner label="Loading course reviews..." />
      </main>
    );
  }

  if (courseQuery.isError || !course) {
    return (
      <main className="mx-auto max-w-3xl px-4 py-16 text-center sm:px-6">
        <div className="rounded-2xl border border-surface-200 bg-white p-10 shadow-xs">
          <h1 className="text-2xl font-bold tracking-tight text-ink-900 sm:text-3xl">
            Course Not Found
          </h1>
          <p className="mx-auto mt-4 max-w-md text-base text-ink-700">
            The course you are looking for might not exist, is unpublished, or
            is unavailable.
          </p>
          <div className="mt-8">
            <Link to="/courses">
              <Button variant="primary">Explore Available Courses</Button>
            </Link>
          </div>
        </div>
      </main>
    );
  }

  const reviewsList = Array.isArray(reviewsQuery.data?.content)
    ? reviewsQuery.data.content
    : [];
  const totalReviewsCount = reviewsQuery.data?.totalElements ?? 0;
  const totalPages = reviewsQuery.data?.totalPages ?? 0;

  const averageDisplay =
    summary.totalReviews > 0 ? Number(summary.averageRating).toFixed(1) : "0.0";

  return (
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:py-12">
      {/* Breadcrumb Navigation */}
      <nav aria-label="Breadcrumb" className="mb-6">
        <ol className="flex flex-wrap items-center gap-2 text-sm text-ink-500">
          <li>
            <Link className="hover:text-brand-700 transition" to="/courses">
              Courses
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          {course.category?.name && (
            <>
              <li>
                <Link
                  className="hover:text-brand-700 transition"
                  to={`/courses?category=${course.category.id}`}
                >
                  {course.category.name}
                </Link>
              </li>
              <li aria-hidden="true">/</li>
            </>
          )}
          <li>
            <Link
              className="hover:text-brand-700 transition font-medium"
              to={`/courses/${parsedCourseId}`}
            >
              {course.title}
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          <li className="font-semibold text-ink-900" aria-current="page">
            Reviews
          </li>
        </ol>
      </nav>

      {/* Course Context Header & Quick Actions */}
      <div className="mb-8 flex flex-col justify-between gap-4 rounded-2xl border border-surface-200 bg-white p-6 shadow-xs sm:flex-row sm:items-center">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-brand-50 px-2.5 py-0.5 text-xs font-semibold text-brand-700 border border-brand-100">
              Course Reviews
            </span>
            {isEnrolled && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700 border border-emerald-200">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                Enrolled Student
              </span>
            )}
            {isOwner && (
              <span className="rounded-full bg-purple-50 px-2.5 py-0.5 text-xs font-semibold text-purple-700 border border-purple-200">
                Course Instructor
              </span>
            )}
          </div>

          <h1 className="mt-2 text-2xl font-bold tracking-tight text-ink-900 sm:text-3xl">
            {course.title}
          </h1>

          <p className="mt-1 text-sm text-ink-600">
            Taught by{" "}
            {course.instructor?.username ? (
              <Link
                to={`/profile/${course.instructor.username}`}
                className="font-medium text-brand-700 hover:underline"
              >
                {course.instructor.displayName || course.instructor.username}
              </Link>
            ) : (
              <span className="font-medium text-ink-800">
                {course.instructor?.displayName || "Instructor"}
              </span>
            )}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <Link
            to={`/courses/${parsedCourseId}`}
            className="inline-flex items-center justify-center rounded-lg border border-surface-300 bg-white px-4 py-2 text-sm font-semibold text-ink-700 shadow-xs transition hover:bg-surface-50"
          >
            ← Back to Course Page
          </Link>

          {isEnrolled && (
            <Link
              to={`/learning/courses/${parsedCourseId}`}
              className="inline-flex items-center justify-center rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white shadow-xs transition hover:bg-brand-700"
            >
              Continue Learning Player →
            </Link>
          )}
        </div>
      </div>

      {/* Reference Review Section: Rating Summary Card */}
      <section
        aria-labelledby="rating-summary-heading"
        className="rounded-2xl border border-surface-200 bg-white p-6 sm:p-8 shadow-xs mb-8"
      >
        <div className="flex items-center justify-between pb-6 border-b border-surface-100">
          <div>
            <h2
              id="rating-summary-heading"
              className="text-xl font-bold tracking-tight text-ink-900 sm:text-2xl"
            >
              Learner Ratings & Reviews
            </h2>
            <p className="mt-1 text-xs sm:text-sm text-ink-500">
              Verified reviews from students enrolled in this course
            </p>
          </div>

          {canReview && !formOpen && (
            <Button
              type="button"
              variant="primary"
              onClick={handleStartWrite}
              className="text-sm font-semibold shadow-xs"
            >
              {myReview ? "Edit Your Review" : "Write a Review"}
            </Button>
          )}
        </div>

        {/* Rating Breakdown & Stats */}
        <div className="grid grid-cols-1 gap-8 pt-6 lg:grid-cols-12 lg:items-center">
          {/* Left Column: Overall Average & Big Stars */}
          <div className="flex flex-col items-center justify-center text-center lg:col-span-4 lg:border-r lg:border-surface-200 lg:pr-8">
            <div className="text-5xl font-black tracking-tight text-ink-900 sm:text-6xl">
              {averageDisplay}
            </div>

            <div
              className="mt-2 flex text-2xl text-amber-400"
              aria-label={`${averageDisplay} out of 5 stars`}
            >
              {[1, 2, 3, 4, 5].map((star) => {
                const filled = summary.averageRating >= star;
                const halfFilled =
                  !filled && summary.averageRating >= star - 0.5;
                return (
                  <span
                    key={star}
                    className={
                      filled || halfFilled ? "text-amber-400" : "text-surface-300"
                    }
                  >
                    ★
                  </span>
                );
              })}
            </div>

            <p className="mt-2 text-sm font-medium text-ink-600">
              {summary.totalReviews > 0
                ? `Based on ${summary.totalReviews} ${
                    summary.totalReviews === 1 ? "review" : "reviews"
                  }`
                : "No reviews yet"}
            </p>

            {canReview && (
              <p className="mt-3 text-xs text-ink-500">
                {myReview
                  ? "You have already reviewed this course."
                  : "You are enrolled and eligible to leave a review."}
              </p>
            )}
          </div>

          {/* Right Column: Horizontal Distribution Bars (5 to 1) */}
          <div className="space-y-3 lg:col-span-8 lg:pl-4">
            {[5, 4, 3, 2, 1].map((star) => {
              const count = summary.distribution?.[star] ?? 0;
              const percentage =
                summary.distributionPercentages?.[star] ?? 0.0;
              const isSelected = selectedRating === star;

              return (
                <button
                  key={star}
                  type="button"
                  onClick={() => handleRatingBarClick(star)}
                  className={`group flex w-full items-center gap-3 rounded-lg p-1.5 transition text-left ${
                    isSelected
                      ? "bg-brand-50/70 ring-1 ring-brand-300"
                      : "hover:bg-surface-50"
                  }`}
                  aria-label={`Filter by ${star} stars reviews`}
                >
                  <span className="w-16 text-xs font-semibold text-ink-700 flex items-center justify-between">
                    <span>{star} stars</span>
                    <span className="text-amber-400">★</span>
                  </span>

                  {/* Horizontal Bar */}
                  <div className="relative h-3 flex-1 overflow-hidden rounded-full bg-surface-100">
                    <div
                      className={`h-full rounded-full transition-all duration-300 ${
                        isSelected
                          ? "bg-brand-600"
                          : "bg-amber-400 group-hover:bg-amber-500"
                      }`}
                      style={{
                        width: `${Math.min(100, Math.max(0, percentage))}%`,
                      }}
                    />
                  </div>

                  <span className="w-20 text-right text-xs font-medium text-ink-600">
                    {count} ({percentage.toFixed(0)}%)
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Write / Edit Review Form */}
        {formOpen && canReview && (
          <form
            onSubmit={handleSubmitReview}
            className="mt-8 rounded-xl border border-brand-200 bg-brand-50/30 p-6 space-y-5"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-ink-900">
                {myReview ? "Edit Your Review" : "Write a Course Review"}
              </h3>
              <button
                type="button"
                onClick={() => setFormOpen(false)}
                className="text-xs font-semibold text-ink-500 hover:text-ink-800 transition"
              >
                ✕ Cancel
              </button>
            </div>

            {/* Star Selector */}
            <div>
              <label className="block text-xs font-semibold text-ink-700 mb-1.5">
                Overall Rating (Select 1 to 5 stars) *
              </label>
              <div
                className="flex items-center gap-1.5"
                role="radiogroup"
                aria-label="Select star rating"
              >
                {[1, 2, 3, 4, 5].map((star) => (
                  <button
                    key={star}
                    type="button"
                    onClick={() => setFormRating(star)}
                    className={`text-3xl transition-transform hover:scale-125 focus:outline-hidden ${
                      star <= formRating
                        ? "text-amber-400"
                        : "text-surface-300 hover:text-amber-200"
                    }`}
                    aria-label={`${star} star${star > 1 ? "s" : ""}`}
                  >
                    ★
                  </button>
                ))}
                <span className="ml-3 text-xs font-bold text-ink-700">
                  {formRating === 5
                    ? "5 Stars - Excellent"
                    : formRating === 4
                      ? "4 Stars - Very Good"
                      : formRating === 3
                        ? "3 Stars - Average"
                        : formRating === 2
                          ? "2 Stars - Poor"
                          : "1 Star - Terrible"}
                </span>
              </div>
            </div>

            {/* Written Review */}
            <div>
              <label
                htmlFor="review-feedback-input"
                className="block text-xs font-semibold text-ink-700 mb-1.5"
              >
                Written Review (Optional)
              </label>
              <textarea
                id="review-feedback-input"
                rows={4}
                value={formText}
                onChange={(e) => setFormText(e.target.value)}
                placeholder="What did you think of the course content, explanations, assignments, and learning pace?"
                maxLength={2000}
                className="w-full rounded-xl border border-surface-300 p-3 text-sm text-ink-900 placeholder:text-ink-400 focus:border-brand-500 focus:outline-hidden focus:ring-1 focus:ring-brand-500 bg-white"
              />
              <div className="mt-1 flex justify-end">
                <span className="text-xs text-ink-400">
                  {formText.length}/2000 characters
                </span>
              </div>
            </div>

            {formError && (
              <div className="rounded-lg border border-danger-200 bg-danger-50 p-3 text-xs font-medium text-danger-700">
                {formError}
              </div>
            )}

            <div className="flex items-center justify-between pt-2">
              {myReview ? (
                <Button
                  type="button"
                  variant="danger"
                  onClick={() => deleteMutation.mutate()}
                  disabled={deleteMutation.isPending || submitMutation.isPending}
                  className="text-xs"
                >
                  {deleteMutation.isPending
                    ? "Deleting Review..."
                    : "Delete Review"}
                </Button>
              ) : (
                <span />
              )}

              <div className="flex items-center gap-3">
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setFormOpen(false)}
                  disabled={submitMutation.isPending}
                  className="text-xs"
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  disabled={submitMutation.isPending || deleteMutation.isPending}
                  className="text-xs"
                >
                  {submitMutation.isPending
                    ? "Submitting..."
                    : myReview
                      ? "Save Changes"
                      : "Submit Review"}
                </Button>
              </div>
            </div>
          </form>
        )}
      </section>

      {/* Search, Filter Chips, and Sorting Toolbar */}
      <section
        aria-label="Filter and sort reviews"
        className="mb-6 space-y-4 rounded-2xl border border-surface-200 bg-white p-5 shadow-xs"
      >
        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          {/* Search Box */}
          <div className="relative flex-1 max-w-md">
            <span
              className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-ink-400"
              aria-hidden="true"
            >
              🔍
            </span>
            <input
              type="search"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search reviews by comment or reviewer name..."
              className="w-full rounded-xl border border-surface-300 py-2.5 pl-10 pr-4 text-sm text-ink-900 placeholder:text-ink-400 focus:border-brand-500 focus:outline-hidden focus:ring-1 focus:ring-brand-500"
              aria-label="Search reviews"
            />
          </div>

          {/* Sorting Options */}
          <div className="flex items-center gap-2">
            <label
              htmlFor="sort-reviews-select"
              className="text-xs font-semibold text-ink-600 whitespace-nowrap"
            >
              Sort by:
            </label>
            <select
              id="sort-reviews-select"
              value={sortOrder}
              onChange={(e) => {
                setSortOrder(e.target.value);
                setPage(0);
              }}
              className="rounded-lg border border-surface-300 bg-white px-3 py-2 text-xs font-medium text-ink-800 shadow-xs focus:border-brand-500 focus:outline-hidden focus:ring-1 focus:ring-brand-500"
            >
              <option value="recent">Most recent</option>
              <option value="highest">Highest rating</option>
              <option value="lowest">Lowest rating</option>
            </select>
          </div>
        </div>

        {/* Rating Filter Chips */}
        <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-surface-100">
          <span className="text-xs font-semibold text-ink-500 mr-1">
            Filter rating:
          </span>

          <button
            type="button"
            onClick={() => {
              setSelectedRating(null);
              setPage(0);
            }}
            className={`rounded-full px-3 py-1 text-xs font-semibold transition ${
              selectedRating === null
                ? "bg-brand-600 text-white shadow-xs"
                : "border border-surface-200 bg-surface-50 text-ink-700 hover:bg-surface-100"
            }`}
          >
            All Ratings ({summary.totalReviews})
          </button>

          {[5, 4, 3, 2, 1].map((star) => {
            const count = summary.distribution?.[star] ?? 0;
            const isSelected = selectedRating === star;
            return (
              <button
                key={star}
                type="button"
                onClick={() => {
                  setSelectedRating(isSelected ? null : star);
                  setPage(0);
                }}
                className={`rounded-full px-3 py-1 text-xs font-semibold transition flex items-center gap-1 ${
                  isSelected
                    ? "bg-brand-600 text-white shadow-xs"
                    : "border border-surface-200 bg-surface-50 text-ink-700 hover:bg-surface-100"
                }`}
              >
                <span>{star}</span>
                <span className={isSelected ? "text-amber-200" : "text-amber-500"}>
                  ★
                </span>
                <span>({count})</span>
              </button>
            );
          })}

          {(selectedRating !== null || searchQuery) && (
            <button
              type="button"
              onClick={() => {
                setSelectedRating(null);
                setSearchQuery("");
                setDebouncedSearch("");
                setPage(0);
              }}
              className="ml-auto text-xs font-semibold text-brand-700 hover:underline"
            >
              Clear filters
            </button>
          )}
        </div>
      </section>

      {/* Individual Review Cards List */}
      <section aria-label="Individual learner reviews" className="space-y-4">
        {reviewsQuery.isLoading ? (
          <div className="flex justify-center py-12">
            <Spinner label="Loading reviews..." />
          </div>
        ) : summary.totalReviews === 0 ? (
          <div className="rounded-2xl border border-surface-200 bg-white p-12 text-center shadow-xs">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-brand-50 text-2xl text-brand-600 mb-3">
              ★
            </div>
            <h3 className="text-lg font-bold text-ink-900">
              No Course Reviews Yet
            </h3>
            <p className="mx-auto mt-1 max-w-md text-sm text-ink-600">
              Enrolled students haven't submitted any reviews for this course yet.
              Be the first to share your learning feedback!
            </p>
            {canReview && !formOpen && (
              <div className="mt-5">
                <Button
                  type="button"
                  variant="primary"
                  onClick={handleStartWrite}
                >
                  Write the First Review
                </Button>
              </div>
            )}
          </div>
        ) : reviewsList.length === 0 ? (
          <div className="rounded-2xl border border-surface-200 bg-white p-12 text-center shadow-xs">
            <h3 className="text-base font-bold text-ink-900">
              No matching reviews found
            </h3>
            <p className="mt-1 text-sm text-ink-600">
              No reviews matched your current search term or star filter.
            </p>
            <div className="mt-4">
              <Button
                type="button"
                variant="secondary"
                onClick={() => {
                  setSelectedRating(null);
                  setSearchQuery("");
                  setDebouncedSearch("");
                  setPage(0);
                }}
              >
                Reset Filters
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center justify-between px-1">
              <span className="text-xs font-semibold text-ink-500">
                Showing {reviewsList.length} of {totalReviewsCount}{" "}
                {totalReviewsCount === 1 ? "review" : "reviews"}
              </span>
            </div>

            {reviewsList.map((rev) => {
              const isCurrentUserReview =
                currentUser && rev.userId === currentUser.id;

              return (
                <article
                  key={rev.id}
                  className="rounded-2xl border border-surface-200 bg-white p-6 shadow-xs transition space-y-3"
                >
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    {/* Reviewer Details */}
                    <div className="flex items-center gap-3">
                      {rev.reviewerAvatarUrl ? (
                        <img
                          src={rev.reviewerAvatarUrl}
                          alt={rev.reviewerName || "Reviewer"}
                          className="h-10 w-10 rounded-full object-cover border border-surface-200"
                        />
                      ) : (
                        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-100 text-sm font-bold text-brand-700">
                          {rev.reviewerName
                            ? rev.reviewerName.charAt(0).toUpperCase()
                            : "L"}
                        </div>
                      )}

                      <div>
                        <div className="flex items-center gap-2">
                          <h4 className="text-sm font-bold text-ink-900">
                            {rev.reviewerName || "Learner"}
                          </h4>
                          {isCurrentUserReview && (
                            <span className="rounded-md bg-brand-50 px-2 py-0.5 text-[10px] font-bold text-brand-700 border border-brand-200">
                              Your Review
                            </span>
                          )}
                        </div>

                        {rev.reviewerUsername && (
                          <Link
                            to={`/profile/${rev.reviewerUsername}`}
                            className="text-xs text-brand-600 hover:underline"
                          >
                            @{rev.reviewerUsername}
                          </Link>
                        )}
                      </div>
                    </div>

                    {/* Rating Stars & Date */}
                    <div className="flex flex-col items-end">
                      <div
                        className="flex text-amber-400 text-base"
                        aria-label={`${rev.rating} out of 5 stars`}
                      >
                        {[1, 2, 3, 4, 5].map((star) => (
                          <span
                            key={star}
                            className={
                              star <= rev.rating
                                ? "text-amber-400"
                                : "text-surface-200"
                            }
                          >
                            ★
                          </span>
                        ))}
                      </div>

                      <time
                        dateTime={rev.createdAt}
                        className="text-xs text-ink-400 mt-0.5"
                      >
                        {new Date(rev.createdAt).toLocaleDateString(undefined, {
                          year: "numeric",
                          month: "short",
                          day: "numeric",
                        })}
                      </time>
                    </div>
                  </div>

                  {/* Review Text */}
                  {rev.reviewText && (
                    <p className="text-sm leading-relaxed text-ink-800 whitespace-pre-wrap pt-1">
                      {rev.reviewText}
                    </p>
                  )}
                </article>
              );
            })}

            {/* Pagination Controls */}
            {totalPages > 1 && (
              <div className="flex items-center justify-center gap-3 pt-6">
                <Button
                  type="button"
                  variant="secondary"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="text-xs"
                >
                  ← Previous
                </Button>
                <span className="text-xs font-semibold text-ink-600">
                  Page {page + 1} of {totalPages}
                </span>
                <Button
                  type="button"
                  variant="secondary"
                  disabled={page + 1 >= totalPages}
                  onClick={() => setPage((p) => p + 1)}
                  className="text-xs"
                >
                  Next →
                </Button>
              </div>
            )}
          </div>
        )}
      </section>
    </main>
  );
}
