import { useEffect } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getContinueLesson, getLearnerOutline } from "./learningApi";
import { Spinner } from "../../shared/components/Spinner";

export function CourseRedirectPage() {
  const { courseId } = useParams();
  const navigate = useNavigate();

  const continueQuery = useQuery({
    queryKey: ["learning", "continue", courseId],
    queryFn: () => getContinueLesson(courseId),
    enabled: Boolean(courseId),
  });

  const outlineQuery = useQuery({
    queryKey: ["learning", "outline", courseId],
    queryFn: () => getLearnerOutline(courseId),
    enabled: Boolean(courseId),
  });

  useEffect(() => {
    if (!continueQuery.data) return;

    const lessonId = continueQuery.data.lessonId;
    if (lessonId) {
      navigate(`/learning/courses/${courseId}/lessons/${lessonId}`, {
        replace: true,
      });
      return;
    }

    // If lessonId is null (e.g. COMPLETED or 0 lessons), pick the first lesson from outline if available
    if (outlineQuery.data && Array.isArray(outlineQuery.data.modules)) {
      const firstLesson = outlineQuery.data.modules
        .flatMap((m) => (Array.isArray(m?.lessons) ? m.lessons : []))
        .find((l) => Boolean(l?.id));

      if (firstLesson) {
        navigate(`/learning/courses/${courseId}/lessons/${firstLesson.id}`, {
          replace: true,
        });
      }
    }
  }, [continueQuery.data, outlineQuery.data, courseId, navigate]);

  if (continueQuery.isLoading || outlineQuery.isLoading) {
    return (
      <div className="flex h-[60vh] flex-col items-center justify-center">
        <Spinner label="Loading course player..." />
      </div>
    );
  }

  // Handle 403 ENROLLMENT_REQUIRED
  if (
    continueQuery.error?.status === 403 ||
    outlineQuery.error?.status === 403
  ) {
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center">
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-8 shadow-xs">
          <h1 className="text-xl font-bold text-amber-950">
            Enrollment Required
          </h1>
          <p className="mt-2 text-sm text-amber-900">
            You must be enrolled in this course to access its modules and lesson
            materials.
          </p>
          <div className="mt-6 flex justify-center gap-3">
            <Link
              to={`/courses/${courseId}`}
              className="inline-flex items-center justify-center rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white shadow-xs transition hover:bg-brand-700"
            >
              View course preview
            </Link>
            <Link
              to="/learning"
              className="inline-flex items-center justify-center rounded-lg border border-surface-300 bg-white px-4 py-2 text-sm font-semibold text-ink-900 shadow-xs transition hover:bg-surface-50"
            >
              My Learning
            </Link>
          </div>
        </div>
      </div>
    );
  }

  // Handle 404 or other errors
  if (continueQuery.isError || outlineQuery.isError) {
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center">
        <div className="rounded-xl border border-surface-200 bg-white p-8 shadow-xs">
          <h1 className="text-xl font-bold text-ink-900">Course Not Found</h1>
          <p className="mt-2 text-sm text-ink-600">
            This course may have been deleted or is not currently accessible.
          </p>
          <div className="mt-6">
            <Link
              to="/learning"
              className="inline-flex items-center justify-center rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white shadow-xs transition hover:bg-brand-700"
            >
              Back to My Learning
            </Link>
          </div>
        </div>
      </div>
    );
  }

  // Course has no lessons
  return (
    <div className="mx-auto max-w-lg px-4 py-20 text-center">
      <h1 className="text-2xl font-bold text-ink-900">
        {outlineQuery.data?.course?.title || "Course"}
      </h1>
      <p className="mt-2 text-ink-600">
        This course currently has no lessons published. Check back soon!
      </p>
      <div className="mt-6 flex justify-center gap-3">
        <Link
          to="/learning"
          className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white"
        >
          My Learning
        </Link>
      </div>
    </div>
  );
}
