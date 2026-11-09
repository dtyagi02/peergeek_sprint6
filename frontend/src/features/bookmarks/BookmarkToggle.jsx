import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "../auth/authStore";
import {
  bookmarkCourse,
  bookmarkLesson,
  getCourseBookmarkStatus,
  getLessonBookmarkStatus,
  removeCourseBookmark,
  removeLessonBookmark,
} from "./bookmarksApi";

export function BookmarkToggle({
  targetType = "COURSE",
  courseId,
  lessonId,
  showLabel = false,
  className = "",
}) {
  const user = useAuthStore((state) => state.user);
  const queryClient = useQueryClient();

  const isCourse = targetType === "COURSE";
  const numCourseId = courseId ? Number(courseId) : undefined;
  const numLessonId = lessonId ? Number(lessonId) : undefined;

  const queryKey = isCourse
    ? ["bookmark-status", "COURSE", numCourseId]
    : ["bookmark-status", "LESSON", numCourseId, numLessonId];

  const { data, isLoading } = useQuery({
    queryKey,
    queryFn: async () => {
      if (isCourse) {
        const res = await getCourseBookmarkStatus(numCourseId);
        return res ?? { bookmarked: false };
      }
      const res = await getLessonBookmarkStatus(numCourseId, numLessonId);
      return res ?? { bookmarked: false };
    },
    enabled: Boolean(user && numCourseId && (isCourse || numLessonId)),
    staleTime: 30_000,
  });

  const isBookmarked = Boolean(data?.bookmarked);

  const mutation = useMutation({
    mutationFn: async () => {
      if (isCourse) {
        return isBookmarked
          ? removeCourseBookmark(numCourseId)
          : bookmarkCourse(numCourseId);
      }
      return isBookmarked
        ? removeLessonBookmark(numCourseId, numLessonId)
        : bookmarkLesson(numCourseId, numLessonId);
    },
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData(queryKey);
      queryClient.setQueryData(queryKey, { bookmarked: !isBookmarked });
      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(queryKey, context.previous);
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey });
      queryClient.invalidateQueries({ queryKey: ["bookmarks"] });
    },
  });

  if (!user) {
    return null;
  }

  const label = isBookmarked
    ? `Remove ${isCourse ? "course" : "lesson"} bookmark`
    : `Bookmark this ${isCourse ? "course" : "lesson"}`;

  return (
    <button
      type="button"
      onClick={() => mutation.mutate()}
      disabled={isLoading || mutation.isPending}
      aria-label={label}
      aria-pressed={isBookmarked}
      title={label}
      className={`inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs font-semibold transition ${
        isBookmarked
          ? "border-amber-300 bg-amber-50 text-amber-800 hover:bg-amber-100"
          : "border-surface-200 bg-white text-ink-700 hover:bg-surface-50 hover:text-ink-900"
      } ${mutation.isPending ? "opacity-75 cursor-wait" : ""} ${className}`}
    >
      <svg
        className={`h-4 w-4 transition-colors ${
          isBookmarked ? "text-amber-500 fill-amber-500" : "text-ink-400"
        }`}
        fill={isBookmarked ? "currentColor" : "none"}
        stroke="currentColor"
        strokeWidth={isBookmarked ? 1 : 2}
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z"
        />
      </svg>
      {showLabel && <span>{isBookmarked ? "Bookmarked" : "Bookmark"}</span>}
    </button>
  );
}
