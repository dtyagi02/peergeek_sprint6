import { useState } from "react";
import { Link } from "react-router-dom";

const DIFFICULTY_LABELS = {
  BEGINNER: "Beginner",
  INTERMEDIATE: "Intermediate",
  ADVANCED: "Advanced",
};

const DIFFICULTY_STYLES = {
  BEGINNER: "bg-emerald-50 text-emerald-700 border-emerald-200",
  INTERMEDIATE: "bg-blue-50 text-blue-700 border-blue-200",
  ADVANCED: "bg-purple-50 text-purple-700 border-purple-200",
};

export function CourseCard({ course }) {
  const [imgError, setImgError] = useState(false);
  const difficultyLabel =
    DIFFICULTY_LABELS[course.difficulty] ?? course.difficulty;
  const difficultyStyle =
    DIFFICULTY_STYLES[course.difficulty] ??
    "bg-surface-100 text-ink-700 border-surface-200";

  const hasRating =
    course.stats?.ratingCount != null && course.stats.ratingCount > 0;
  const ratingAvg =
    course.stats?.ratingAverage != null
      ? Number(course.stats.ratingAverage).toFixed(1)
      : null;

  return (
    <article className="h-full">
      <Link
        aria-label={`${course.title} by ${course.instructor?.displayName ?? "Instructor"}`}
        className="group flex h-full flex-col overflow-hidden rounded-xl border border-surface-200 bg-white transition-all duration-200 hover:border-[#06110C]/60 hover:shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-[#A31C1B] focus-visible:ring-offset-2"
        to={`/courses/${course.id}`}
      >
        {/* Thumbnail or graceful placeholder */}
        <div className="relative aspect-video w-full overflow-hidden bg-slate-100">
          {course.thumbnailUrl && !imgError ? (
            <img
              alt={course.title}
              className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
              loading="lazy"
              onError={() => setImgError(true)}
              src={course.thumbnailUrl}
            />
          ) : (
            <div className="flex h-full w-full flex-col items-center justify-center bg-gradient-to-br from-[#06110C] to-[#171717] p-4 text-center">
              <span className="text-2xl font-bold tracking-wider text-white">
                {course.title ? course.title.charAt(0).toUpperCase() : "P"}
              </span>
              <span className="mt-1 line-clamp-1 text-xs font-medium text-neutral-300">
                {course.category?.name ?? "PeerGeek"}
              </span>
            </div>
          )}
          <span
            className={`absolute right-3 top-3 rounded-full border px-2.5 py-0.5 text-xs font-semibold backdrop-blur-sm ${difficultyStyle}`}
          >
            {difficultyLabel}
          </span>
        </div>

        {/* Card Body */}
        <div className="flex flex-1 flex-col p-5">
          {course.category?.name && (
            <p className="text-xs font-bold uppercase tracking-wider text-[#A31C1B]">
              {course.category.name}
            </p>
          )}

          <h3 className="mt-1 line-clamp-2 text-base font-bold text-ink-900 group-hover:text-[#A31C1B] transition-colors">
            {course.title}
          </h3>

          <p className="mt-2 line-clamp-2 text-sm text-ink-700">
            {course.shortDescription}
          </p>

          <p className="mt-3 text-xs font-medium text-slate-600">
            By {course.instructor?.displayName ?? "Instructor"}
            {course.instructor?.username && (
              <span className="text-slate-500 font-normal"> (@{course.instructor.username})</span>
            )}
          </p>

          {/* Counts & Stats footer */}
          <div className="mt-auto pt-4">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500">
              <span>{course.moduleCount} modules</span>
              <span>•</span>
              <span>{course.lessonCount} lessons</span>
            </div>

            {/* Rating and Social Stats */}
            <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs">
              {hasRating && (
                <div
                  aria-label={`Rated ${ratingAvg} out of 5 stars based on ${course.stats.ratingCount} reviews`}
                  className="flex items-center gap-1 font-semibold text-amber-700"
                >
                  <span aria-hidden="true" className="text-[#A31C1B]">
                    ★
                  </span>
                  <span>{ratingAvg}</span>
                  <span className="font-normal text-slate-500">
                    ({course.stats.ratingCount})
                  </span>
                </div>
              )}

              {course.stats?.enrollmentCount != null && (
                <span className="text-slate-600">
                  {course.stats.enrollmentCount.toLocaleString()} enrolled
                </span>
              )}

              {course.stats?.completionRate != null && (
                <span className="inline-flex items-center gap-1 font-semibold text-[#06110C]">
                  <span
                    aria-hidden="true"
                    className="h-1.5 w-1.5 rounded-full bg-[#06110C]"
                  />
                  <span>{course.stats.completionRate}% completion</span>
                </span>
              )}
            </div>
          </div>
        </div>
      </Link>
    </article>
  );
}

export function CourseCardSkeleton() {
  return (
    <div className="flex h-full animate-pulse flex-col overflow-hidden rounded-xl border border-surface-200 bg-white">
      <div className="aspect-video w-full bg-slate-200" />
      <div className="flex flex-1 flex-col p-5">
        <div className="h-3 w-1/4 rounded bg-slate-200" />
        <div className="mt-3 h-5 w-3/4 rounded bg-slate-200" />
        <div className="mt-2 h-4 w-full rounded bg-slate-100" />
        <div className="mt-1 h-4 w-2/3 rounded bg-slate-100" />
        <div className="mt-4 h-3 w-1/3 rounded bg-slate-200" />
        <div className="mt-auto pt-4">
          <div className="h-3 w-1/2 rounded bg-slate-200" />
        </div>
      </div>
    </div>
  );
}
