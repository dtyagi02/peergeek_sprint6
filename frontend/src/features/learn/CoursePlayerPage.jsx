import { useState, useEffect, useRef } from "react";
import { useParams, Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getLearnerOutline,
  getLearnerLesson,
  getFreshResourceUrl,
  visitLesson,
  setLessonCompletion,
} from "./learningApi";
import { InstructorModeBanner } from "./InstructorModeBanner";
import { LessonDocument } from "../../shared/lesson-renderer/LessonDocument";
import { Slot } from "../../app/registry/slots";
import { Spinner } from "../../shared/components/Spinner";
import { Button } from "../../shared/components/Button";

export function CoursePlayerPage() {
  const { courseId: courseIdParam, lessonId: lessonIdParam } = useParams();
  const courseId = Number(courseIdParam);
  const lessonId = Number(lessonIdParam);
  const queryClient = useQueryClient();

  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [announcement, setAnnouncement] = useState("");
  const [refreshedResourceUrls, setRefreshedResourceUrls] = useState({});
  const headingRef = useRef(null);

  // Fetch Course Outline
  const outlineQuery = useQuery({
    queryKey: ["learning", "outline", courseId],
    queryFn: () => getLearnerOutline(courseId),
    enabled: Boolean(courseId),
  });

  // Fetch Current Lesson
  const lessonQuery = useQuery({
    queryKey: ["learning", "lesson", courseId, lessonId],
    queryFn: () => getLearnerLesson(courseId, lessonId),
    enabled: Boolean(courseId && lessonId),
  });

  // Call /visit once per lesson view
  useEffect(() => {
    if (courseId && lessonId) {
      visitLesson(courseId, lessonId).catch(() => {
        // non-critical, ignore error
      });
    }
  }, [courseId, lessonId]);

  // Move focus to the lesson heading on lesson navigation
  useEffect(() => {
    if (headingRef.current) {
      headingRef.current.focus();
    }
  }, [lessonId]);

  // Lesson completion mutation
  const completionMutation = useMutation({
    mutationFn: (completed) =>
      setLessonCompletion(courseId, lessonId, completed),
    onSuccess: (newProgress, completed) => {
      setAnnouncement(
        completed
          ? "Lesson marked as completed."
          : "Lesson marked as incomplete.",
      );
      // Invalidate queries so outline checkmarks, progress bars, and continue lesson update
      queryClient.invalidateQueries({
        queryKey: ["learning", "outline", courseId],
      });
      queryClient.invalidateQueries({
        queryKey: ["learning", "lesson", courseId, lessonId],
      });
      queryClient.invalidateQueries({
        queryKey: ["learning", "continue", courseId],
      });
      queryClient.invalidateQueries({ queryKey: ["learning", "courses"] });
      queryClient.invalidateQueries({
        queryKey: ["learning", "enrollment", courseId],
      });
    },
  });

  // Expired resource URL refetch handler
  async function handleResourceError(resourceId, disposition = "inline") {
    if (refreshedResourceUrls[resourceId]) {
      return; // Already attempted refresh once
    }
    try {
      const fresh = await getFreshResourceUrl(resourceId, disposition);
      if (fresh?.url) {
        setRefreshedResourceUrls((prev) => ({
          ...prev,
          [resourceId]: fresh.url,
        }));
      }
    } catch {
      // Ignored if resource unavailable
    }
  }

  // Handle 403 Forbidden: Enrollment Required
  if (outlineQuery.error?.status === 403 || lessonQuery.error?.status === 403) {
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center">
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-8 shadow-xs">
          <h1 className="text-xl font-bold text-amber-950">
            Enrollment Required
          </h1>
          <p className="mt-2 text-sm text-amber-900">
            You must be enrolled in this course to access its modules and lesson
            content.
          </p>
          <div className="mt-6 flex justify-center gap-3">
            <Link
              to={`/courses/${courseId}`}
              className="inline-flex items-center justify-center rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white shadow-xs transition hover:bg-brand-700"
            >
              View Course Preview
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

  // Handle 404 Not Found
  if (outlineQuery.error?.status === 404 || lessonQuery.error?.status === 404) {
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center">
        <div className="rounded-xl border border-surface-200 bg-white p-8 shadow-xs">
          <h1 className="text-xl font-bold text-ink-900">
            Lesson or Course Not Found
          </h1>
          <p className="mt-2 text-sm text-ink-600">
            The requested lesson or course could not be found. It may have been
            removed or restructured.
          </p>
          <div className="mt-6 flex justify-center gap-3">
            <Link
              to={`/learning/courses/${courseId}`}
              className="inline-flex items-center justify-center rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white shadow-xs transition hover:bg-brand-700"
            >
              Continue Learning
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

  if (outlineQuery.isLoading || lessonQuery.isLoading) {
    return (
      <div className="flex h-[70vh] flex-col items-center justify-center">
        <Spinner label="Loading lesson..." />
      </div>
    );
  }

  const outline = outlineQuery.data;
  const lessonData = lessonQuery.data;
  const course = outline?.course;
  const progress = outline?.progress ?? {
    percent: 0,
    completedLessons: 0,
    totalLessons: 0,
  };

  const isCompleted = lessonData?.completed ?? false;

  return (
    <div className="flex min-h-[calc(100vh-4rem)] flex-col bg-surface-50">
      {/* Aria-live region for screen readers */}
      <div aria-live="polite" className="sr-only">
        {announcement}
      </div>

      {/* Top Header / Player Nav */}
      <header className="sticky top-0 z-20 flex items-center justify-between border-b border-surface-200 bg-white px-4 py-3 shadow-xs sm:px-6">
        <div className="flex items-center gap-3">
          {/* Sidebar Toggle Button (Desktop & Mobile) */}
          <button
            type="button"
            onClick={() => setSidebarOpen((prev) => !prev)}
            aria-label={sidebarOpen ? "Close outline" : "Open outline"}
            title={sidebarOpen ? "Hide course outline" : "Show course outline"}
            className="inline-flex items-center gap-1.5 rounded-lg border border-surface-300 bg-white px-2.5 py-1.5 text-xs font-semibold text-ink-700 shadow-xs hover:bg-surface-100 hover:text-ink-900 transition-colors"
          >
            <svg
              className="h-4 w-4 shrink-0 text-ink-600"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              {sidebarOpen ? (
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M11 19l-7-7 7-7m8 14l-7-7 7-7"
                />
              ) : (
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M13 5l7 7-7 7M5 5l7 7-7 7"
                />
              )}
            </svg>
            <span className="hidden sm:inline">
              {sidebarOpen ? "Hide Outline" : "Show Outline"}
            </span>
          </button>

          <div>
            <Link
              to="/learning"
              className="text-xs font-semibold text-brand-600 hover:text-brand-700"
            >
              ← Back to My Learning
            </Link>
            <h1 className="text-base font-bold text-ink-900 sm:text-lg line-clamp-1">
              {course?.title || "Course Player"}
            </h1>
          </div>
        </div>

        {/* Course Progress in Header */}
        <div className="flex items-center gap-4">
          <div className="hidden sm:flex flex-col items-end">
            <span className="text-xs font-semibold text-ink-700">
              {progress.percent}% completed
            </span>
            <div className="mt-1 h-1.5 w-32 overflow-hidden rounded-full bg-surface-100">
              <div
                className="h-full bg-brand-600 transition-all duration-300"
                style={{ width: `${Math.min(100, progress.percent)}%` }}
              />
            </div>
          </div>

          <Link
            to={`/learning/courses/${courseId}`}
            className="rounded-md border border-surface-300 bg-white px-3 py-1.5 text-xs font-semibold text-ink-800 shadow-xs hover:bg-surface-50"
          >
            Continue Learning
          </Link>

          <Slot name="player.header-actions" courseId={courseId} />
        </div>
      </header>

      {/* Main Layout: Sidebar + Lesson Viewer */}
      <div className="flex flex-1 relative">
        {/* Sidebar: Modules and Lessons Outline */}
        <aside
          className={`border-r border-surface-200 bg-white transition-all duration-300 ease-in-out ${
            sidebarOpen
              ? "w-80 shrink-0 opacity-100 translate-x-0"
              : "w-0 shrink-0 opacity-0 overflow-hidden border-r-0 pointer-events-none -translate-x-full lg:translate-x-0"
          } fixed inset-y-0 left-0 z-30 pt-16 shadow-xl lg:static lg:z-10 lg:pt-0 lg:shadow-none lg:h-[calc(100vh-8.5rem)] lg:sticky lg:top-[3.75rem]`}
          aria-label="Course outline"
        >
          <div className="flex h-full w-80 flex-col overflow-y-auto p-4">
            {/* Header inside sidebar with title and collapse button */}
            <div className="mb-3 pb-2 border-b border-surface-100 flex items-center justify-between">
              <div>
                <span className="text-xs font-bold uppercase tracking-wider text-ink-500">
                  Course Outline
                </span>
                <div className="flex items-center gap-2 mt-0.5 text-xs font-semibold text-ink-700">
                  <span>Progress</span>
                  <span className="text-brand-600">{progress.percent}%</span>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setSidebarOpen(false)}
                aria-label="Collapse outline"
                title="Collapse outline"
                className="rounded-lg p-1 text-ink-400 hover:bg-surface-100 hover:text-ink-600 transition-colors"
              >
                <svg
                  className="h-4 w-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M15 19l-7-7 7-7"
                  />
                </svg>
              </button>
            </div>

            {/* Progress Bar */}
            <div className="mb-4 pb-3 border-b border-surface-100">
              <div className="h-2 w-full overflow-hidden rounded-full bg-surface-100">
                <div
                  className="h-full bg-brand-600 transition-all duration-300"
                  style={{ width: `${Math.min(100, progress.percent)}%` }}
                />
              </div>
              <span className="mt-1 block text-right text-xs text-ink-500">
                {progress.completedLessons} of {progress.totalLessons} lessons
              </span>
            </div>

            {/* Modules and Lessons List */}
            <div className="space-y-4">
              {Array.isArray(outline?.modules) &&
                outline.modules.map((module, mIdx) => (
                  <div key={module.id} className="space-y-1">
                    <h2 className="px-2 text-xs font-bold uppercase tracking-wider text-ink-500">
                      Module {mIdx + 1}: {module.title}
                    </h2>
                    <ul className="space-y-0.5">
                      {Array.isArray(module.lessons) &&
                        module.lessons.map((l) => {
                          const isCurrent = l.id === lessonId;
                          return (
                            <li key={l.id}>
                              <Link
                                to={`/learning/courses/${courseId}/lessons/${l.id}`}
                                onClick={() => {
                                  // Close drawer on mobile
                                  if (window.innerWidth < 1024) {
                                    setSidebarOpen(false);
                                  }
                                }}
                                aria-current={isCurrent ? "page" : undefined}
                                className={`flex items-center justify-between rounded-lg px-2.5 py-2 text-xs font-medium transition ${
                                  isCurrent
                                    ? "bg-brand-50 text-brand-700 font-semibold border-l-4 border-brand-600 pl-2"
                                    : "text-ink-700 hover:bg-surface-100 hover:text-ink-900"
                                }`}
                              >
                                <span className="line-clamp-1">{l.title}</span>
                                {l.completed && (
                                  <svg
                                    className="h-4 w-4 shrink-0 text-emerald-600 ml-2"
                                    fill="currentColor"
                                    viewBox="0 0 20 20"
                                    aria-label="Completed"
                                  >
                                    <path
                                      fillRule="evenodd"
                                      d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                                      clipRule="evenodd"
                                    />
                                  </svg>
                                )}
                              </Link>
                            </li>
                          );
                        })}
                    </ul>
                  </div>
                ))}
            </div>
          </div>
        </aside>

        {/* Overlay for mobile drawer */}
        {sidebarOpen && (
          <div
            className="fixed inset-0 z-20 bg-slate-900/40 lg:hidden"
            onClick={() => setSidebarOpen(false)}
            aria-hidden="true"
          />
        )}

        {/* Main Lesson Viewport */}
        <main className="flex-1 overflow-y-auto p-4 sm:p-6 lg:p-10 transition-all duration-300">
          <div className={`mx-auto transition-all duration-300 ${sidebarOpen ? "max-w-4xl" : "max-w-5xl"}`}>
            <InstructorModeBanner />

            {/* Lesson Header Card */}
            <div className="rounded-xl border border-surface-200 bg-white p-6 sm:p-8 shadow-xs">
              <div className="flex flex-wrap items-center justify-between gap-4 border-b border-surface-100 pb-5">
                <div>
                  <h1
                    ref={headingRef}
                    tabIndex={-1}
                    className="text-2xl sm:text-3xl font-extrabold text-ink-900 focus:outline-hidden"
                  >
                    {lessonData?.lesson?.title}
                  </h1>
                  {lessonData?.lesson?.summary && (
                    <p className="mt-2 text-sm text-ink-600 max-w-2xl">
                      {lessonData.lesson.summary}
                    </p>
                  )}
                </div>

                {/* Mark Complete Toggle */}
                <div>
                  <Button
                    type="button"
                    onClick={() => completionMutation.mutate(!isCompleted)}
                    disabled={completionMutation.isPending}
                    variant={isCompleted ? "secondary" : "primary"}
                    className="gap-2 text-sm"
                  >
                    {isCompleted ? (
                      <>
                        <svg
                          className="h-4 w-4 text-emerald-600"
                          fill="currentColor"
                          viewBox="0 0 20 20"
                        >
                          <path
                            fillRule="evenodd"
                            d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                            clipRule="evenodd"
                          />
                        </svg>
                        <span>Mark incomplete</span>
                      </>
                    ) : (
                      <>
                        <svg
                          className="h-4 w-4"
                          fill="none"
                          stroke="currentColor"
                          viewBox="0 0 24 24"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M5 13l4 4L19 7"
                          />
                        </svg>
                        <span>Mark complete</span>
                      </>
                    )}
                  </Button>
                </div>
              </div>

              {/* Lesson Document Content */}
              <div className="mt-8 prose prose-slate max-w-none">
                {lessonData?.contentDocument ? (
                  <LessonDocument document={lessonData.contentDocument} />
                ) : (
                  <p className="italic text-ink-400">
                    This lesson has no written document content.
                  </p>
                )}
              </div>

              {/* Attached Lesson Resources */}
              {Array.isArray(lessonData?.resources) &&
                lessonData.resources.length > 0 && (
                  <section
                    className="mt-12 rounded-lg border border-surface-200 bg-surface-50/50 p-6"
                    aria-labelledby="lesson-resources-title"
                  >
                    <h2
                      id="lesson-resources-title"
                      className="text-lg font-bold text-ink-900 mb-4"
                    >
                      Lesson Materials & Resources
                    </h2>
                    <div className="space-y-6">
                      {lessonData.resources.map((res) => {
                        const effectiveUrl =
                          refreshedResourceUrls[res.id] || res.url;
                        const isPdf =
                          res.type === "PDF" ||
                          res.mimeType === "application/pdf";
                        const isImage =
                          res.type === "IMAGE" ||
                          res.mimeType?.startsWith("image/");
                        const isVideo =
                          res.type === "VIDEO" ||
                          res.mimeType?.startsWith("video/");
                        const isLink = res.type === "LINK";

                        if (isLink) {
                          return (
                            <div
                              key={res.id}
                              className="flex items-center justify-between rounded-lg border border-surface-200 bg-white p-4 shadow-xs"
                            >
                              <div className="flex items-center gap-3">
                                <span className="rounded-md bg-blue-50 p-2 text-blue-600">
                                  🔗
                                </span>
                                <div>
                                  <h3 className="text-sm font-semibold text-ink-900">
                                    {res.title}
                                  </h3>
                                  <span className="text-xs text-ink-500">
                                    External Resource Link
                                  </span>
                                </div>
                              </div>
                              <a
                                href={res.externalUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center rounded-md bg-brand-50 px-3 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-100"
                              >
                                Open link ↗
                              </a>
                            </div>
                          );
                        }

                        if (isPdf) {
                          return (
                            <div
                              key={res.id}
                              className="rounded-lg border border-surface-200 bg-white p-4 shadow-xs"
                            >
                              <div className="flex items-center justify-between mb-3">
                                <h3 className="text-sm font-semibold text-ink-900">
                                  {res.title}
                                </h3>
                                <a
                                  href={effectiveUrl}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="text-xs font-semibold text-brand-600 hover:underline"
                                >
                                  Open PDF in new tab ↗
                                </a>
                              </div>
                              <iframe
                                src={effectiveUrl}
                                title={res.title}
                                onError={() =>
                                  handleResourceError(res.id, "inline")
                                }
                                className="h-96 w-full rounded-md border border-surface-200"
                              />
                            </div>
                          );
                        }

                        if (isImage) {
                          return (
                            <div
                              key={res.id}
                              className="rounded-lg border border-surface-200 bg-white p-4 shadow-xs"
                            >
                              <h3 className="text-sm font-semibold text-ink-900 mb-2">
                                {res.title}
                              </h3>
                              <img
                                src={effectiveUrl}
                                alt={res.title}
                                onError={() =>
                                  handleResourceError(res.id, "inline")
                                }
                                className="max-h-96 rounded-md object-contain"
                              />
                            </div>
                          );
                        }

                        if (isVideo) {
                          return (
                            <div
                              key={res.id}
                              className="rounded-lg border border-surface-200 bg-white p-4 shadow-xs"
                            >
                              <h3 className="text-sm font-semibold text-ink-900 mb-2">
                                {res.title}
                              </h3>
                              <video
                                controls
                                preload="metadata"
                                src={effectiveUrl}
                                onError={() =>
                                  handleResourceError(res.id, "inline")
                                }
                                className="w-full rounded-md shadow-xs"
                              />
                            </div>
                          );
                        }

                        // Default: DOCUMENT download link
                        return (
                          <div
                            key={res.id}
                            className="flex items-center justify-between rounded-lg border border-surface-200 bg-white p-4 shadow-xs"
                          >
                            <div className="flex items-center gap-3">
                              <span className="rounded-md bg-surface-100 p-2 text-ink-600">
                                📄
                              </span>
                              <div>
                                <h3 className="text-sm font-semibold text-ink-900">
                                  {res.title}
                                </h3>
                                {res.sizeBytes && (
                                  <span className="text-xs text-ink-500">
                                    {(res.sizeBytes / 1024).toFixed(1)} KB
                                  </span>
                                )}
                              </div>
                            </div>
                            <a
                              href={effectiveUrl}
                              download
                              onClick={() =>
                                handleResourceError(res.id, "attachment")
                              }
                              className="inline-flex items-center rounded-md bg-brand-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-brand-700"
                            >
                              Download
                            </a>
                          </div>
                        );
                      })}
                    </div>
                  </section>
                )}

              {/* Lesson Actions Slot (Phase 8: Report issue, etc.) */}
              <div className="mt-8 border-t border-surface-100 pt-4">
                <Slot
                  name="player.lesson-actions"
                  courseId={courseId}
                  lessonId={lessonId}
                />
              </div>

              {/* Previous / Next Lesson Navigation Buttons */}
              <div className="mt-8 flex items-center justify-between border-t border-surface-200 pt-6">
                <div>
                  {lessonData?.previousLessonId ? (
                    <Link
                      to={`/learning/courses/${courseId}/lessons/${lessonData.previousLessonId}`}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-surface-300 bg-white px-4 py-2 text-sm font-semibold text-ink-800 shadow-xs hover:bg-surface-50"
                    >
                      ← Previous lesson
                    </Link>
                  ) : (
                    <span className="inline-flex items-center gap-1.5 rounded-lg border border-surface-200 bg-surface-100 px-4 py-2 text-sm font-semibold text-ink-400 cursor-not-allowed">
                      ← Previous lesson
                    </span>
                  )}
                </div>

                <div>
                  {lessonData?.nextLessonId ? (
                    <Link
                      to={`/learning/courses/${courseId}/lessons/${lessonData.nextLessonId}`}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white shadow-xs hover:bg-brand-700"
                    >
                      Next lesson →
                    </Link>
                  ) : (
                    <Link
                      to={`/learning/courses/${courseId}`}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow-xs hover:bg-emerald-700"
                    >
                      Course summary ✓
                    </Link>
                  )}
                </div>
              </div>
            </div>

            {/* Side-Panel Slot (Phase 11: Chat / Notes / etc.) */}
            <div className="mt-8">
              <Slot
                name="player.side-panel"
                courseId={courseId}
                lessonId={lessonId}
              />
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
