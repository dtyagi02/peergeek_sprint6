import { Link, useLocation } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "../auth/authStore";
import {
  enrollInCourse,
  getEnrollmentStatus,
} from "./learningApi";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";

export function EnrollArea({ courseId }) {
  const location = useLocation();
  const queryClient = useQueryClient();
  const authStatus = useAuthStore((state) => state.status);
  const isAuthenticated = authStatus === "authenticated";

  const {
    data: statusData,
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: ["learning", "enrollment", courseId],
    queryFn: () => getEnrollmentStatus(courseId),
    enabled: isAuthenticated && Boolean(courseId),
  });

  const enrollMutation = useMutation({
    mutationFn: () => enrollInCourse(courseId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["learning", "enrollment", courseId],
      });
      queryClient.invalidateQueries({ queryKey: ["learning", "courses"] });
    },
  });

  // Anonymous State
  if (!isAuthenticated) {
    const returnTo = encodeURIComponent(
      location.pathname + location.search + location.hash,
    );
    return (
      <div className="rounded-xl border border-surface-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-bold text-ink-900">Enrollment</h2>
        <p className="mt-1 text-sm text-ink-700">
          Sign in to your PeerGeek account to enroll in this course for free and
          track your progress.
        </p>
        <div className="mt-5">
          <Link
            to={`/login?returnTo=${returnTo}`}
            className="inline-flex w-full items-center justify-center rounded-lg bg-brand-600 px-4 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700"
          >
            Log in to enroll
          </Link>
        </div>
      </div>
    );
  }

  // Loading State
  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center rounded-xl border border-surface-200 bg-white p-8 shadow-sm">
        <Spinner label="Loading enrollment status..." />
        <span className="mt-2 text-xs text-ink-500">
          Checking enrollment...
        </span>
      </div>
    );
  }

  // Error State - Small retry, never blocks preview
  if (isError) {
    return (
      <div className="rounded-xl border border-amber-200 bg-amber-50/50 p-5">
        <p className="text-sm font-medium text-amber-900">
          Unable to check enrollment status.
        </p>
        <button
          type="button"
          onClick={() => refetch()}
          className="mt-2 rounded-md border border-amber-300 bg-white px-3 py-1.5 text-xs font-semibold text-amber-950 shadow-xs hover:bg-amber-100"
        >
          Retry
        </button>
      </div>
    );
  }

  // Owner State
  if (statusData?.isOwner) {
    return (
      <div className="rounded-xl border border-brand-200 bg-brand-50/40 p-6 shadow-sm">
        <span className="inline-block rounded-full bg-brand-100 px-2.5 py-1 text-xs font-semibold text-brand-800">
          Course Instructor
        </span>
        <h2 className="mt-2 text-lg font-bold text-ink-900">
          This is your course
        </h2>
        <p className="mt-1 text-sm text-ink-700">
          You are the author of this course. As the instructor, you can edit
          modules, lessons, and resources in your instructor dashboard.
        </p>
        <div className="mt-5">
          <Link
            to={`/instructor/courses/${courseId}`}
            className="inline-flex w-full items-center justify-center rounded-lg border border-brand-600 bg-white px-4 py-2.5 text-sm font-semibold text-brand-700 shadow-sm transition hover:bg-brand-50"
          >
            Manage Course
          </Link>
        </div>
      </div>
    );
  }

  // Enrolled State
  if (statusData?.enrolled && statusData?.status === "ACTIVE") {
    const progress = statusData.progress ?? {
      completedLessons: 0,
      totalLessons: 0,
      percent: 0,
    };

    return (
      <div className="rounded-xl border border-surface-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700 border border-emerald-200">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
            Enrolled
          </span>
          <span className="text-xs font-semibold text-ink-600">
            {progress.completedLessons} / {progress.totalLessons} lessons
          </span>
        </div>

        <div className="mt-4">
          <div className="flex items-center justify-between text-xs font-medium text-ink-700 mb-1.5">
            <span>Course Progress</span>
            <span className="font-bold text-ink-900">{progress.percent}%</span>
          </div>
          <div className="h-2 w-full overflow-hidden rounded-full bg-surface-100">
            <div
              className="h-full bg-brand-600 transition-all duration-300"
              style={{ width: `${Math.min(100, progress.percent)}%` }}
            />
          </div>
        </div>

        <div className="mt-6 flex flex-col gap-2.5">
          <Link
            to={`/courses/${courseId}/reviews`}
            className="inline-flex w-full items-center justify-center rounded-lg bg-brand-600 px-4 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700"
          >
            Continue learning
          </Link>

          <Link
            to={`/courses/${courseId}/reviews`}
            className="inline-flex w-full items-center justify-center rounded-lg border border-surface-300 bg-white px-4 py-2.5 text-sm font-semibold text-ink-700 shadow-xs transition hover:bg-surface-50"
          >
            Reviews
          </Link>
        </div>
      </div>
    );
  }

  // Not Enrolled State
  return (
    <div className="rounded-xl border border-surface-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-bold text-ink-900">Start Learning Today</h2>
      <p className="mt-1 text-sm text-ink-700">
        Free and instant access to all course modules, lessons, downloadable
        resources, and community progress tracking.
      </p>

      {enrollMutation.isError && (
        <p className="mt-3 text-xs font-medium text-danger-600">
          Enrollment failed. Please try again.
        </p>
      )}

      <div className="mt-6">
        <Button
          type="button"
          onClick={() => enrollMutation.mutate()}
          disabled={enrollMutation.isPending || !statusData?.canEnroll}
          className="w-full py-3"
        >
          {enrollMutation.isPending ? "Enrolling..." : "Enroll in Course"}
        </Button>
      </div>
    </div>
  );
}
