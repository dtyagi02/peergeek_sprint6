import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { deleteBookmark, listBookmarks } from "./bookmarksApi";
import { Button } from "../../shared/components/Button";

export function BookmarksPage() {
  const [filter, setFilter] = useState("ALL"); // ALL, COURSE, LESSON
  const queryClient = useQueryClient();

  const {
    data: bookmarks = [],
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: ["bookmarks", filter === "ALL" ? null : filter],
    queryFn: async () => {
      const res = await listBookmarks(filter === "ALL" ? null : filter);
      return res ?? [];
    },
    staleTime: 15_000,
  });

  const removeMutation = useMutation({
    mutationFn: (id) => deleteBookmark(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["bookmarks"] });
      queryClient.invalidateQueries({ queryKey: ["bookmark-status"] });
    },
  });

  const filteredBookmarks = bookmarks.filter((bmk) => {
    if (filter === "COURSE") return bmk.targetType === "COURSE";
    if (filter === "LESSON") return bmk.targetType === "LESSON";
    return true;
  });

  return (
    <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-surface-200 pb-5 mb-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-ink-900">
            My Bookmarks
          </h1>
          <p className="mt-1 text-sm text-ink-600">
            Quickly jump back to your saved courses and lessons.
          </p>
        </div>

        {/* Filter Tabs */}
        <div className="inline-flex rounded-lg border border-surface-200 bg-surface-50 p-1">
          <button
            type="button"
            onClick={() => setFilter("ALL")}
            className={`rounded-md px-3 py-1.5 text-xs font-semibold transition ${
              filter === "ALL"
                ? "bg-white text-ink-900 shadow-xs"
                : "text-ink-600 hover:text-ink-900"
            }`}
          >
            All ({bookmarks.length})
          </button>
          <button
            type="button"
            onClick={() => setFilter("COURSE")}
            className={`rounded-md px-3 py-1.5 text-xs font-semibold transition ${
              filter === "COURSE"
                ? "bg-white text-ink-900 shadow-xs"
                : "text-ink-600 hover:text-ink-900"
            }`}
          >
            Courses
          </button>
          <button
            type="button"
            onClick={() => setFilter("LESSON")}
            className={`rounded-md px-3 py-1.5 text-xs font-semibold transition ${
              filter === "LESSON"
                ? "bg-white text-ink-900 shadow-xs"
                : "text-ink-600 hover:text-ink-900"
            }`}
          >
            Lessons
          </button>
        </div>
      </div>

      {/* Loading Skeleton */}
      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <div
              key={i}
              className="animate-pulse rounded-xl border border-surface-200 bg-white p-5 shadow-xs"
            >
              <div className="h-4 w-20 rounded bg-surface-200 mb-3" />
              <div className="h-5 w-3/4 rounded bg-surface-200 mb-2" />
              <div className="h-4 w-1/2 rounded bg-surface-100 mb-4" />
              <div className="h-8 w-full rounded bg-surface-100" />
            </div>
          ))}
        </div>
      )}

      {/* Error State */}
      {!isLoading && isError && (
        <div className="rounded-xl border border-rose-200 bg-rose-50 p-6 text-center">
          <h2 className="text-base font-semibold text-rose-800">
            Unable to load bookmarks
          </h2>
          <p className="mt-1 text-sm text-rose-600">
            An error occurred while fetching your saved bookmarks.
          </p>
          <div className="mt-4">
            <Button variant="secondary" onClick={() => refetch()}>
              Try again
            </Button>
          </div>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && !isError && filteredBookmarks.length === 0 && (
        <div className="rounded-2xl border border-dashed border-surface-300 bg-surface-50/50 p-12 text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-surface-100 text-2xl text-ink-500 mb-4">
            🔖
          </div>
          <h2 className="text-base font-semibold text-ink-900">
            No bookmarks found
          </h2>
          <p className="mt-1 text-sm text-ink-500 max-w-sm mx-auto">
            {filter === "ALL"
              ? "You haven't bookmarked any courses or lessons yet. Use the bookmark icon on any course or lesson to save it here."
              : `You don't have any bookmarked ${filter.toLowerCase()}s.`}
          </p>
          <div className="mt-6">
            <Link
              to="/courses"
              className="inline-flex items-center justify-center rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white shadow-xs hover:bg-brand-700"
            >
              Explore Courses
            </Link>
          </div>
        </div>
      )}

      {/* Bookmarks Grid */}
      {!isLoading && !isError && filteredBookmarks.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filteredBookmarks.map((bookmark) => {
            const isCourse = bookmark.targetType === "COURSE";
            const targetUrl = isCourse
              ? `/courses/${bookmark.courseId}`
              : `/learning/courses/${bookmark.courseId}/lessons/${bookmark.lessonId}`;

            return (
              <div
                key={bookmark.id}
                className="flex flex-col justify-between rounded-xl border border-surface-200 bg-white p-5 shadow-xs transition hover:shadow-sm hover:border-surface-300"
              >
                <div>
                  {/* Badge & Target Type */}
                  <div className="flex items-center justify-between mb-3">
                    <span
                      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                        isCourse
                          ? "bg-brand-50 text-brand-700 border border-brand-200"
                          : "bg-indigo-50 text-indigo-700 border border-indigo-200"
                      }`}
                    >
                      {isCourse ? "Course" : "Lesson"}
                    </span>
                    <button
                      type="button"
                      onClick={() => removeMutation.mutate(bookmark.id)}
                      disabled={removeMutation.isPending}
                      aria-label="Remove bookmark"
                      title="Remove bookmark"
                      className="text-ink-400 hover:text-rose-600 transition"
                    >
                      <svg
                        className="h-4 w-4"
                        fill="currentColor"
                        viewBox="0 0 20 20"
                        aria-hidden="true"
                      >
                        <path
                          fillRule="evenodd"
                          d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                          clipRule="evenodd"
                        />
                      </svg>
                    </button>
                  </div>

                  {/* Title & Course reference */}
                  <h3 className="text-base font-bold text-ink-900 line-clamp-2">
                    {isCourse
                      ? bookmark.courseTitle || "Course"
                      : bookmark.lessonTitle || "Lesson"}
                  </h3>

                  {!isCourse && bookmark.courseTitle && (
                    <p className="mt-1 text-xs text-ink-500 line-clamp-1">
                      From: {bookmark.courseTitle}
                    </p>
                  )}
                </div>

                {/* Footer with timestamp and navigation button */}
                <div className="mt-6 pt-4 border-t border-surface-100 flex items-center justify-between">
                  <span className="text-[11px] text-ink-400">
                    {bookmark.createdAt
                      ? new Date(bookmark.createdAt).toLocaleDateString()
                      : ""}
                  </span>
                  <Link
                    to={targetUrl}
                    className="inline-flex items-center gap-1 text-xs font-semibold text-brand-600 hover:text-brand-700 transition"
                  >
                    <span>View {isCourse ? "course" : "lesson"}</span>
                    <span aria-hidden="true">→</span>
                  </Link>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </main>
  );
}
