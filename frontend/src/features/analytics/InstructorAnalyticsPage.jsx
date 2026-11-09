import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getCourseAnalytics } from "./analyticsApi";
import { Button } from "../../shared/components/Button";
import { ErrorState } from "../../shared/components/ErrorState";

export function InstructorAnalyticsPage() {
  const { courseId } = useParams();
  const [isRefreshing, setIsRefreshing] = useState(false);

  const {
    data: analytics,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ["instructor", "analytics", courseId],
    queryFn: () => getCourseAnalytics(courseId),
    enabled: Boolean(courseId),
    staleTime: 60 * 1000, // 1 minute
  });

  const handleRefresh = async () => {
    setIsRefreshing(true);
    try {
      await refetch();
    } finally {
      setIsRefreshing(false);
    }
  };

  if (isLoading) {
    return (
      <div
        className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8"
        aria-busy="true"
        aria-label="Loading analytics"
      >
        <div className="mb-8 animate-pulse space-y-3">
          <div className="h-4 w-36 rounded bg-surface-200" />
          <div className="h-8 w-64 rounded bg-surface-200" />
          <div className="h-4 w-96 rounded bg-surface-200" />
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[...Array(7)].map((_, i) => (
            <div
              key={i}
              className="h-32 animate-pulse rounded-xl border border-surface-200 bg-surface-100 p-5"
            />
          ))}
        </div>
        <div className="mt-8 grid grid-cols-1 gap-6 lg:grid-cols-2">
          <div className="h-80 animate-pulse rounded-xl border border-surface-200 bg-surface-100 p-6" />
          <div className="h-80 animate-pulse rounded-xl border border-surface-200 bg-surface-100 p-6" />
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <Link
          to={`/instructor/courses/${courseId}`}
          className="text-sm font-medium text-brand-600 hover:text-brand-700"
        >
          ← Back to Course Editor
        </Link>
        <div className="mt-6">
          <ErrorState
            title="Failed to Load Course Analytics"
            description={
              error?.message ||
              "Unable to retrieve analytics for this course. Please verify that you are the course instructor and try again."
            }
          />
          <div className="mt-4">
            <Button onClick={() => refetch()} variant="secondary">
              Retry
            </Button>
          </div>
        </div>
      </div>
    );
  }

  const {
    courseTitle = `Course #${courseId}`,
    totalEnrollments = 0,
    activeLearners = 0,
    completionRate = 0,
    averageAssessmentScore,
    averageRating,
    ratingCount = 0,
    assignmentSubmissionRate = 0,
    pendingSubmissions = 0,
    lessonDropOff = [],
    assignmentStats = [],
    scoreDistribution = [],
  } = analytics || {};

  const isBrandNewCourse =
    totalEnrollments === 0 &&
    lessonDropOff.length === 0 &&
    assignmentStats.length === 0;

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      {/* Header & Navigation */}
      <div className="mb-8 flex flex-wrap items-center justify-between gap-4">
        <div>
          <Link
            to={`/instructor/courses/${courseId}`}
            className="text-sm font-medium text-brand-600 hover:text-brand-700"
          >
            ← Back to Course Editor
          </Link>
          <div className="mt-2 flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-ink-900 sm:text-3xl">
              Course Analytics
            </h1>
            <span className="rounded-full bg-brand-50 px-2.5 py-0.5 text-xs font-semibold text-brand-700 border border-brand-200">
              {courseTitle}
            </span>
          </div>
          <p className="mt-1 text-sm text-ink-600">
            Real-time enrollment, curriculum progression, and assessment
            performance overview.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={handleRefresh}
            disabled={isRefreshing}
            className="inline-flex items-center gap-2 rounded-lg border border-surface-300 bg-white px-3.5 py-2 text-xs font-semibold text-ink-700 shadow-sm transition hover:bg-surface-50 disabled:opacity-50"
            aria-label="Refresh analytics data"
          >
            <svg
              className={`h-4 w-4 text-ink-600 ${isRefreshing ? "animate-spin" : ""}`}
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
              />
            </svg>
            <span>{isRefreshing ? "Refreshing..." : "Refresh"}</span>
          </button>

          <Link
            to={`/instructor/courses/${courseId}/assignments`}
            className="inline-flex items-center gap-1.5 rounded-lg border border-surface-300 bg-white px-3.5 py-2 text-xs font-semibold text-ink-700 shadow-sm transition hover:bg-surface-50"
          >
            Manage Assignments
          </Link>
        </div>
      </div>

      {isBrandNewCourse && (
        <div
          data-testid="empty-course-banner"
          className="mb-8 rounded-xl border border-dashed border-surface-300 bg-surface-50 p-6 text-center"
        >
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-brand-100 text-brand-600">
            <svg
              className="h-6 w-6"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
          </div>
          <h2 className="mt-3 text-base font-semibold text-ink-900">
            No Learner Activity Yet
          </h2>
          <p className="mt-1 text-sm text-ink-600 max-w-md mx-auto">
            Once students enroll, take lessons, and submit assignments, their
            progress and grading metrics will automatically populate this
            dashboard.
          </p>
        </div>
      )}

      {/* 7 KPI Cards */}
      <section
        aria-label="Key Performance Indicators"
        className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4"
      >
        {/* 1. Total Enrollments */}
        <div
          data-testid="kpi-total-enrollments"
          className="rounded-xl border border-surface-200 bg-white p-5 shadow-sm transition hover:shadow"
        >
          <div className="flex items-center justify-between text-ink-500">
            <span className="text-xs font-medium uppercase tracking-wider text-ink-600">
              Total Enrollments
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-surface-100 text-ink-600">
              <svg
                className="h-4 w-4"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"
                />
              </svg>
            </div>
          </div>
          <div className="mt-3">
            <span className="text-3xl font-extrabold tracking-tight text-ink-900">
              {totalEnrollments.toLocaleString()}
            </span>
          </div>
          <p className="mt-2 text-2xs text-ink-500">
            All-time enrolled learners
          </p>
        </div>

        {/* 2. Active Learners */}
        <div
          data-testid="kpi-active-learners"
          className="rounded-xl border border-surface-200 bg-white p-5 shadow-sm transition hover:shadow"
        >
          <div className="flex items-center justify-between text-ink-500">
            <span className="text-xs font-medium uppercase tracking-wider text-ink-600">
              Active Learners
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600">
              <svg
                className="h-4 w-4"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                />
              </svg>
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold tracking-tight text-ink-900">
              {activeLearners.toLocaleString()}
            </span>
            {totalEnrollments > 0 && (
              <span className="text-xs font-semibold text-emerald-600">
                ({Math.round((activeLearners / totalEnrollments) * 100)}%)
              </span>
            )}
          </div>
          <p className="mt-2 text-2xs text-ink-500">
            Currently active enrolled students
          </p>
        </div>

        {/* 3. Completion Rate */}
        <div
          data-testid="kpi-completion-rate"
          className="rounded-xl border border-surface-200 bg-white p-5 shadow-sm transition hover:shadow"
        >
          <div className="flex items-center justify-between text-ink-500">
            <span className="text-xs font-medium uppercase tracking-wider text-ink-600">
              Completion Rate
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600">
              <svg
                className="h-4 w-4"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M9 12l2 2 4-4M7.835 4.697a3.42 3.42 0 001.946-.806 3.42 3.42 0 014.438 0 3.42 3.42 0 001.946.806 3.42 3.42 0 013.138 3.138 3.42 3.42 0 00.806 1.946 3.42 3.42 0 010 4.438 3.42 3.42 0 00-.806 1.946 3.42 3.42 0 01-3.138 3.138 3.42 3.42 0 00-1.946.806 3.42 3.42 0 01-4.438 0 3.42 3.42 0 00-1.946-.806 3.42 3.42 0 01-3.138-3.138 3.42 3.42 0 00-.806-1.946 3.42 3.42 0 010-4.438 3.42 3.42 0 00.806-1.946 3.42 3.42 0 013.138-3.138z"
                />
              </svg>
            </div>
          </div>
          <div className="mt-3">
            <span className="text-3xl font-extrabold tracking-tight text-ink-900">
              {completionRate}%
            </span>
          </div>
          <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-surface-100">
            <div
              className="h-full rounded-full bg-indigo-500 transition-all duration-500"
              style={{
                width: `${Math.min(100, Math.max(0, completionRate))}%`,
              }}
              role="progressbar"
              aria-valuenow={completionRate}
              aria-valuemin="0"
              aria-valuemax="100"
            />
          </div>
          <p className="mt-2 text-2xs text-ink-500">
            Learners completing all lessons
          </p>
        </div>

        {/* 4. Average Assessment Score */}
        <div
          data-testid="kpi-average-score"
          className="rounded-xl border border-surface-200 bg-white p-5 shadow-sm transition hover:shadow"
        >
          <div className="flex items-center justify-between text-ink-500">
            <span className="text-xs font-medium uppercase tracking-wider text-ink-600">
              Avg Assessment Score
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber-50 text-amber-600">
              <svg
                className="h-4 w-4"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4"
                />
              </svg>
            </div>
          </div>
          <div className="mt-3">
            <span className="text-3xl font-extrabold tracking-tight text-ink-900">
              {averageAssessmentScore != null
                ? `${averageAssessmentScore}%`
                : "N/A"}
            </span>
          </div>
          <p className="mt-2 text-2xs text-ink-500">
            {averageAssessmentScore != null
              ? "Across all graded submissions"
              : "No graded submissions yet"}
          </p>
        </div>

        {/* 5. Average Rating */}
        <div
          data-testid="kpi-average-rating"
          className="rounded-xl border border-surface-200 bg-white p-5 shadow-sm transition hover:shadow"
        >
          <div className="flex items-center justify-between text-ink-500">
            <span className="text-xs font-medium uppercase tracking-wider text-ink-600">
              Course Rating
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber-100 text-amber-500">
              <svg className="h-4 w-4 fill-amber-400" viewBox="0 0 20 20">
                <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
              </svg>
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold tracking-tight text-ink-900">
              {averageRating != null ? averageRating.toFixed(1) : "N/A"}
            </span>
            {ratingCount > 0 && (
              <span className="text-xs font-semibold text-ink-500">/ 5.0</span>
            )}
          </div>
          <p className="mt-2 text-2xs text-ink-500">
            {ratingCount > 0
              ? `${ratingCount} learner ${ratingCount === 1 ? "review" : "reviews"}`
              : "No reviews submitted"}
          </p>
        </div>

        {/* 6. Assignment Submission Rate */}
        <div
          data-testid="kpi-submission-rate"
          className="rounded-xl border border-surface-200 bg-white p-5 shadow-sm transition hover:shadow"
        >
          <div className="flex items-center justify-between text-ink-500">
            <span className="text-xs font-medium uppercase tracking-wider text-ink-600">
              Submission Rate
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-sky-50 text-sky-600">
              <svg
                className="h-4 w-4"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
                />
              </svg>
            </div>
          </div>
          <div className="mt-3">
            <span className="text-3xl font-extrabold tracking-tight text-ink-900">
              {assignmentSubmissionRate}%
            </span>
          </div>
          <p className="mt-2 text-2xs text-ink-500">
            Submitted vs expected assignments
          </p>
        </div>

        {/* 7. Pending Submissions */}
        <div
          data-testid="kpi-pending-submissions"
          className={`rounded-xl border p-5 shadow-sm transition hover:shadow ${
            pendingSubmissions > 0
              ? "border-amber-200 bg-amber-50/50"
              : "border-surface-200 bg-white"
          }`}
        >
          <div className="flex items-center justify-between text-ink-500">
            <span className="text-xs font-medium uppercase tracking-wider text-ink-600">
              Pending Reviews
            </span>
            <div
              className={`flex h-8 w-8 items-center justify-center rounded-lg ${
                pendingSubmissions > 0
                  ? "bg-amber-100 text-amber-700"
                  : "bg-emerald-50 text-emerald-600"
              }`}
            >
              <svg
                className="h-4 w-4"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
            </div>
          </div>
          <div className="mt-3 flex items-baseline justify-between">
            <span className="text-3xl font-extrabold tracking-tight text-ink-900">
              {pendingSubmissions}
            </span>
            {pendingSubmissions > 0 && (
              <Link
                to={`/instructor/courses/${courseId}/submissions?status=SUBMITTED`}
                className="text-xs font-semibold text-brand-600 hover:text-brand-700 hover:underline"
              >
                Review queue →
              </Link>
            )}
          </div>
          <p className="mt-2 text-2xs text-ink-500">
            {pendingSubmissions > 0
              ? "Submissions awaiting instructor evaluation"
              : "All submissions graded"}
          </p>
        </div>
      </section>

      {/* Visualizations Section */}
      <div className="mt-8 grid grid-cols-1 gap-8 lg:grid-cols-2">
        {/* Chart 1: Lesson-Level Drop-Off Funnel */}
        <div
          data-testid="chart-lesson-drop-off"
          className="rounded-xl border border-surface-200 bg-white p-6 shadow-sm"
        >
          <div className="mb-6 flex items-center justify-between">
            <div>
              <h2 className="text-base font-bold text-ink-900">
                Lesson-Level Drop-Off Funnel
              </h2>
              <p className="mt-1 text-xs text-ink-600">
                Sequential completion retention and drop-off rate by lesson
              </p>
            </div>
            <span className="rounded bg-surface-100 px-2 py-1 text-2xs font-semibold text-ink-600">
              {lessonDropOff.length} Lessons
            </span>
          </div>

          {lessonDropOff.length === 0 ? (
            <div className="py-12 text-center text-sm text-ink-500">
              No lessons configured in this course.
            </div>
          ) : (
            <div className="space-y-4">
              {lessonDropOff.map((lesson) => (
                <div key={lesson.lessonId} className="group">
                  <div className="mb-1 flex items-center justify-between text-xs">
                    <div className="flex items-center gap-2 truncate pr-2">
                      <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-surface-100 text-2xs font-bold text-ink-700">
                        {lesson.orderIndex}
                      </span>
                      <span
                        className="font-medium text-ink-900 truncate"
                        title={lesson.lessonTitle}
                      >
                        {lesson.lessonTitle}
                      </span>
                      {lesson.moduleTitle && (
                        <span className="hidden sm:inline rounded bg-surface-100 px-1.5 py-0.5 text-2xs text-ink-500">
                          {lesson.moduleTitle}
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <span className="font-semibold text-ink-800">
                        {lesson.completedCount} ({lesson.completionPercent}%)
                      </span>
                      {lesson.dropOffRate > 0 ? (
                        <span className="rounded-full bg-rose-50 px-2 py-0.5 text-2xs font-bold text-rose-600 border border-rose-200">
                          -{lesson.dropOffRate}% drop
                        </span>
                      ) : (
                        <span className="rounded-full bg-surface-100 px-2 py-0.5 text-2xs font-medium text-ink-500">
                          0% drop
                        </span>
                      )}
                    </div>
                  </div>
                  <div className="h-3 w-full overflow-hidden rounded-full bg-surface-100">
                    <div
                      className="h-full rounded-full bg-gradient-to-r from-brand-500 to-indigo-600 transition-all duration-500"
                      style={{
                        width: `${Math.min(100, Math.max(0, lesson.completionPercent))}%`,
                      }}
                      role="progressbar"
                      aria-valuenow={lesson.completionPercent}
                      aria-valuemin="0"
                      aria-valuemax="100"
                      aria-label={`${lesson.lessonTitle} completion ${lesson.completionPercent}%`}
                    />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Chart 2: Assessment Score Distribution */}
        <div
          data-testid="chart-score-distribution"
          className="rounded-xl border border-surface-200 bg-white p-6 shadow-sm"
        >
          <div className="mb-6 flex items-center justify-between">
            <div>
              <h2 className="text-base font-bold text-ink-900">
                Assessment Score Distribution
              </h2>
              <p className="mt-1 text-xs text-ink-600">
                Learner score distribution across 5 performance tiers
              </p>
            </div>
            <span className="rounded bg-surface-100 px-2 py-1 text-2xs font-semibold text-ink-600">
              5 Buckets
            </span>
          </div>

          <div className="space-y-4 pt-2">
            {scoreDistribution.map((bucket, index) => {
              const bucketColors = [
                "from-rose-500 to-rose-600",
                "from-amber-500 to-amber-600",
                "from-yellow-500 to-yellow-600",
                "from-sky-500 to-sky-600",
                "from-emerald-500 to-emerald-600",
              ];
              const color = bucketColors[index % bucketColors.length];

              return (
                <div key={bucket.bucket} className="space-y-1">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-ink-800">
                      {bucket.bucket}
                    </span>
                    <span className="text-ink-600 font-medium">
                      {bucket.count} submissions ({bucket.percentage}%)
                    </span>
                  </div>
                  <div className="h-4 w-full overflow-hidden rounded-md bg-surface-100">
                    <div
                      className={`h-full rounded-md bg-gradient-to-r ${color} transition-all duration-500`}
                      style={{
                        width: `${Math.min(100, Math.max(0, bucket.percentage))}%`,
                      }}
                      role="progressbar"
                      aria-valuenow={bucket.percentage}
                      aria-valuemin="0"
                      aria-valuemax="100"
                      aria-label={`${bucket.bucket} score bucket: ${bucket.count} submissions (${bucket.percentage}%)`}
                    />
                  </div>
                </div>
              );
            })}
          </div>

          <div className="mt-6 border-t border-surface-100 pt-4 text-2xs text-ink-500 flex justify-between items-center">
            <span>Scores calculated from finalized graded submissions</span>
            <span className="font-medium text-ink-700">
              Avg:{" "}
              {averageAssessmentScore != null
                ? `${averageAssessmentScore}%`
                : "N/A"}
            </span>
          </div>
        </div>
      </div>

      {/* Chart 3: Assignment Performance Breakdown */}
      <div
        data-testid="chart-assignment-performance"
        className="mt-8 rounded-xl border border-surface-200 bg-white p-6 shadow-sm"
      >
        <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
          <div>
            <h2 className="text-base font-bold text-ink-900">
              Assignment Performance & Submission Breakdown
            </h2>
            <p className="mt-1 text-xs text-ink-600">
              Submission rates and average score by individual assignment
            </p>
          </div>
          <Link
            to={`/instructor/courses/${courseId}/assignments`}
            className="text-xs font-semibold text-brand-600 hover:text-brand-700"
          >
            Manage Assignments →
          </Link>
        </div>

        {assignmentStats.length === 0 ? (
          <div className="py-8 text-center text-sm text-ink-500">
            No assignments published for this course yet.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-ink-700">
              <thead className="border-b border-surface-200 bg-surface-50 text-2xs font-semibold uppercase text-ink-600">
                <tr>
                  <th scope="col" className="px-4 py-3">
                    Assignment
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Status
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Max Points
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Submitted
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Graded
                  </th>
                  <th scope="col" className="px-4 py-3 min-w-[140px]">
                    Submission Rate
                  </th>
                  <th scope="col" className="px-4 py-3">
                    Avg Score
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-surface-100">
                {assignmentStats.map((item) => (
                  <tr key={item.id} className="hover:bg-surface-50/70">
                    <td className="px-4 py-3.5 font-medium text-ink-900 max-w-xs truncate">
                      <Link
                        to={`/instructor/courses/${courseId}/assignments/${item.id}`}
                        className="hover:text-brand-600 hover:underline"
                      >
                        {item.title}
                      </Link>
                    </td>
                    <td className="px-4 py-3.5">
                      <span
                        className={`inline-flex rounded-full px-2 py-0.5 text-2xs font-bold uppercase tracking-wider ${
                          item.status === "PUBLISHED"
                            ? "bg-emerald-50 text-emerald-700 border border-emerald-200"
                            : "bg-surface-100 text-ink-600"
                        }`}
                      >
                        {item.status}
                      </span>
                    </td>
                    <td className="px-4 py-3.5 font-mono">
                      {item.maxPoints} pts
                    </td>
                    <td className="px-4 py-3.5 font-semibold text-ink-900">
                      {item.submittedCount}
                    </td>
                    <td className="px-4 py-3.5 text-ink-600">
                      {item.gradedCount}
                    </td>
                    <td className="px-4 py-3.5">
                      <div className="flex items-center gap-2">
                        <div className="h-2 w-20 overflow-hidden rounded-full bg-surface-100">
                          <div
                            className="h-full rounded-full bg-sky-500"
                            style={{
                              width: `${Math.min(100, Math.max(0, item.submissionRate))}%`,
                            }}
                          />
                        </div>
                        <span className="font-semibold text-ink-800">
                          {item.submissionRate}%
                        </span>
                      </div>
                    </td>
                    <td className="px-4 py-3.5">
                      {item.averageScore != null ? (
                        <span
                          className={`font-semibold ${
                            item.averageScore >= 80
                              ? "text-emerald-600"
                              : item.averageScore >= 60
                                ? "text-amber-600"
                                : "text-rose-600"
                          }`}
                        >
                          {item.averageScore}%
                        </span>
                      ) : (
                        <span className="text-ink-400">N/A</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
