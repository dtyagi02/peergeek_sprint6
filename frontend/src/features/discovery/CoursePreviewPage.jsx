import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Slot } from "../../app/registry/slots";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { getPublicCoursePreview } from "./discoveryApi";
import { CourseReviewsSection } from "../reviews/CourseReviewsSection";

const DIFFICULTY_LABELS = {
  BEGINNER: "Beginner",
  INTERMEDIATE: "Intermediate",
  ADVANCED: "Advanced",
};

export function CoursePreviewPage() {
  const { courseId } = useParams();
  const [imgError, setImgError] = useState(false);
  const [openModules, setOpenModules] = useState({ 0: true });

  const courseQuery = useQuery({
    queryKey: ["public-course-preview", courseId],
    queryFn: () => getPublicCoursePreview(courseId),
    retry: false,
    staleTime: 60 * 1000,
  });

  const course = courseQuery.data;

  // Set document title
  useEffect(() => {
    if (course?.title) {
      document.title = `${course.title} | PeerGeek`;
    }
    return () => {
      document.title = "PeerGeek";
    };
  }, [course?.title]);

  function toggleModule(index) {
    setOpenModules((prev) => ({
      ...prev,
      [index]: !prev[index],
    }));
  }

  // Loading state
  if (courseQuery.isLoading) {
    return (
      <main className="flex min-h-[50vh] items-center justify-center">
        <Spinner label="Loading course preview..." />
      </main>
    );
  }

  // Friendly 404 / unavailable state
  if (courseQuery.isError || !course) {
    return (
      <main className="mx-auto max-w-3xl px-4 py-16 text-center sm:px-6">
        <div className="rounded-2xl border border-surface-200 bg-white p-10 shadow-xs">
          <h1 className="text-2xl font-bold tracking-tight text-ink-900 sm:text-3xl">
            This course isn't available
          </h1>
          <p className="mx-auto mt-4 max-w-md text-base text-ink-700">
            The course you're looking for might have been unpublished, moved, or
            does not exist.
          </p>
          <div className="mt-8">
            <Link to="/courses">
              <Button variant="primary">Explore available courses</Button>
            </Link>
          </div>
        </div>
      </main>
    );
  }

  const hasRating =
    course.stats?.ratingCount != null && course.stats.ratingCount > 0;
  const ratingAvg =
    course.stats?.ratingAverage != null
      ? Number(course.stats.ratingAverage).toFixed(1)
      : null;

  const parsedCourseId = Number(courseId);

  return (
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:py-12">
      {/* Breadcrumb Navigation */}
      <nav aria-label="Breadcrumb" className="mb-6">
        <ol className="flex items-center gap-2 text-sm text-slate-500">
          <li>
            <Link className="hover:text-brand-700" to="/courses">
              Courses
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          {course.category?.name && (
            <>
              <li>
                <Link
                  className="hover:text-brand-700"
                  to={`/courses?category=${course.category.slug}`}
                >
                  {course.category.name}
                </Link>
              </li>
              <li aria-hidden="true">/</li>
            </>
          )}
          <li aria-current="page" className="truncate font-medium text-ink-900">
            {course.title}
          </li>
        </ol>
      </nav>

      {/* Main 2-Column Layout */}
      <div className="grid grid-cols-1 gap-12 lg:grid-cols-3">
        {/* Left Column: Course Details */}
        <div className="space-y-10 lg:col-span-2">
          {/* Header Section */}
          <header className="space-y-4">
            <div className="flex flex-wrap items-center gap-2">
              {course.category?.name && (
                <span className="rounded-full bg-brand-50 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-brand-700">
                  {course.category.name}
                </span>
              )}
              {course.difficulty && (
                <span className="rounded-full border border-surface-200 bg-white px-3 py-1 text-xs font-semibold text-slate-700">
                  {DIFFICULTY_LABELS[course.difficulty] ?? course.difficulty}
                </span>
              )}
            </div>

            <h1 className="text-3xl font-extrabold tracking-tight text-ink-900 sm:text-4xl lg:text-5xl">
              {course.title}
            </h1>

            <p className="text-base font-medium text-slate-700">
              Instructed by{" "}
              <span className="font-semibold text-ink-900">
                {course.instructor?.displayName ?? "Instructor"}
              </span>
            </p>

            {/* Social stats & ratings header bar */}
            <div className="flex flex-wrap items-center gap-x-6 gap-y-2 border-y border-surface-200 py-3 text-sm">
              {hasRating && (
                <div
                  aria-label={`Rated ${ratingAvg} out of 5 stars based on ${course.stats.ratingCount} reviews`}
                  className="flex items-center gap-1.5 font-bold text-amber-700"
                >
                  <span aria-hidden="true" className="text-amber-500">
                    ★
                  </span>
                  <span>{ratingAvg}</span>
                  <span className="font-normal text-slate-500">
                    ({course.stats.ratingCount.toLocaleString()} reviews)
                  </span>
                </div>
              )}

              {course.stats?.enrollmentCount != null && (
                <span className="text-slate-600">
                  <strong className="text-ink-900">
                    {course.stats.enrollmentCount.toLocaleString()}
                  </strong>{" "}
                  learners enrolled
                </span>
              )}

              {course.stats?.completionRate != null && (
                <span className="text-emerald-700 font-medium">
                  {course.stats.completionRate}% completion rate
                </span>
              )}

              <span className="text-slate-500">
                {course.moduleCount} modules • {course.lessonCount} lessons
              </span>
            </div>
          </header>

          {/* Thumbnail preview */}
          {course.thumbnailUrl && !imgError && (
            <div className="overflow-hidden rounded-2xl border border-surface-200 bg-slate-100 shadow-xs">
              <img
                alt={course.title}
                className="aspect-video w-full object-cover"
                onError={() => setImgError(true)}
                src={course.thumbnailUrl}
              />
            </div>
          )}

          {/* Description Section: strictly plain text with preserved line breaks */}
          <section aria-labelledby="about-course-heading" className="space-y-4">
            <h2
              className="text-2xl font-bold tracking-tight text-ink-900"
              id="about-course-heading"
            >
              About this course
            </h2>
            <div className="whitespace-pre-wrap leading-relaxed text-ink-700 text-base">
              {course.description}
            </div>
          </section>

          {/* Topics */}
          {Array.isArray(course.topics) && course.topics.length > 0 && (
            <section aria-labelledby="topics-heading" className="space-y-3">
              <h2
                className="text-lg font-bold text-ink-900"
                id="topics-heading"
              >
                Topics covered
              </h2>
              <div className="flex flex-wrap gap-2">
                {course.topics.map((topic, i) => (
                  <span
                    className="rounded-lg bg-surface-100 px-3 py-1 text-sm font-medium text-slate-700 border border-surface-200"
                    key={i}
                  >
                    #{topic}
                  </span>
                ))}
              </div>
            </section>
          )}

          {/* Course Outline Accordion */}
          <section aria-labelledby="outline-heading" className="space-y-4">
            <div className="flex flex-col gap-1 sm:flex-row sm:items-baseline sm:justify-between">
              <h2
                className="text-2xl font-bold tracking-tight text-ink-900"
                id="outline-heading"
              >
                Course outline
              </h2>
              <span className="text-sm font-medium text-slate-500">
                {course.moduleCount} modules • {course.lessonCount} lessons
              </span>
            </div>

            <p className="rounded-lg bg-brand-50 p-3 text-sm text-brand-800 border border-brand-100">
              💡{" "}
              <em>
                Full lesson content and assignments unlock after enrolling.
              </em>
            </p>

            <div className="divide-y divide-surface-200 rounded-xl border border-surface-200 bg-white shadow-xs">
              {Array.isArray(course.outline) &&
                course.outline.map((mod, modIdx) => {
                  const isOpen = !!openModules[modIdx];
                  const contentId = `module-content-${modIdx}`;
                  const triggerId = `module-trigger-${modIdx}`;

                  return (
                    <div key={modIdx}>
                      <button
                        aria-controls={contentId}
                        aria-expanded={isOpen}
                        className="flex w-full items-center justify-between p-5 text-left transition-colors hover:bg-surface-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-600 focus-visible:ring-inset"
                        id={triggerId}
                        onClick={() => toggleModule(modIdx)}
                        type="button"
                      >
                        <div className="flex items-center gap-3">
                          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-slate-100 text-xs font-bold text-slate-700">
                            {modIdx + 1}
                          </span>
                          <span className="text-base font-semibold text-ink-900">
                            {mod.moduleTitle}
                          </span>
                        </div>
                        <div className="flex items-center gap-3 text-sm text-slate-500">
                          <span>
                            {mod.lessons?.length ?? 0}{" "}
                            {(mod.lessons?.length ?? 0) === 1
                              ? "lesson"
                              : "lessons"}
                          </span>
                          <span
                            aria-hidden="true"
                            className={`text-slate-400 transition-transform duration-200 ${isOpen ? "rotate-180" : ""}`}
                          >
                            ▼
                          </span>
                        </div>
                      </button>

                      {isOpen && (
                        <div
                          aria-labelledby={triggerId}
                          className="border-t border-surface-100 bg-surface-50/50 px-5 py-3"
                          id={contentId}
                          role="region"
                        >
                          <ul className="space-y-2.5">
                            {Array.isArray(mod.lessons) &&
                              mod.lessons.map((lesson, lessonIdx) => (
                                <li
                                  className="flex items-center gap-3 text-sm text-slate-700 py-1"
                                  key={lessonIdx}
                                >
                                  <span
                                    aria-hidden="true"
                                    className="text-xs text-slate-400"
                                  >
                                    📄
                                  </span>
                                  <span>{lesson.title}</span>
                                </li>
                              ))}
                          </ul>
                        </div>
                      )}
                    </div>
                  );
                })}
            </div>
          </section>

          {/* Instructor Card */}
          <section aria-labelledby="instructor-heading" className="space-y-4">
            <h2
              className="text-2xl font-bold tracking-tight text-ink-900"
              id="instructor-heading"
            >
              About the instructor
            </h2>
            <div className="rounded-xl border border-surface-200 bg-white p-6 shadow-xs">
              <div className="flex items-start gap-4">
                <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-brand-100 text-xl font-bold text-brand-700">
                  {course.instructor?.displayName
                    ? course.instructor.displayName.charAt(0).toUpperCase()
                    : "I"}
                </div>
                <div className="space-y-1">
                  <h3 className="text-lg font-bold text-ink-900">
                    {course.instructor?.displayName ?? "Instructor"}
                    {course.instructor?.username && (
                      <span className="ml-2 text-sm font-normal text-ink-500">
                        @{course.instructor.username}
                      </span>
                    )}
                  </h3>
                  {course.instructor?.headline && (
                    <p className="text-sm font-medium text-brand-700">
                      {course.instructor.headline}
                    </p>
                  )}
                  {course.instructor?.bio && (
                    <p className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-ink-700">
                      {course.instructor.bio}
                    </p>
                  )}
                </div>
              </div>
            </div>
          </section>

          {/* Reviews & Ratings Section */}
          <CourseReviewsSection courseId={parsedCourseId} />
        </div>

        {/* Right Column: Side panel for Phase 6 enroll area */}
        <div className="lg:col-span-1">
          <div className="sticky top-6">
            <Slot courseId={parsedCourseId} name="course-page.enroll-area" />
          </div>
        </div>
      </div>
    </main>
  );
}
