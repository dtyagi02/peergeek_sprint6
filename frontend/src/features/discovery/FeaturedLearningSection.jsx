import { useRef } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getPopularCourses } from "./discoveryApi";

export function FeaturedLearningSection() {
  const scrollRef = useRef(null);

  const popularQuery = useQuery({
    queryKey: ["public-popular-courses"],
    queryFn: getPopularCourses,
    staleTime: 2 * 60 * 1000,
    retry: false,
  });

  const courses = Array.isArray(popularQuery.data) ? popularQuery.data : [];

  const handleScroll = (direction) => {
    if (!scrollRef.current) return;
    const scrollAmount = direction === "left" ? -280 : 280;
    scrollRef.current.scrollBy({ left: scrollAmount, behavior: "smooth" });
  };

  return (
    <section
      aria-labelledby="featured-path-heading"
      className="relative overflow-hidden rounded-2xl border border-[#262626] bg-gradient-to-br from-[#06110C] via-[#0b1c14] to-[#171717] p-6 lg:p-8 text-white shadow-xl"
    >
      {/* Decorative subtle ambient glows */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -right-20 -top-20 h-72 w-72 rounded-full bg-[#A31C1B]/10 blur-3xl"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -bottom-20 -left-20 h-72 w-72 rounded-full bg-[#0d281c] blur-3xl"
      />

      <div className="relative z-10 flex flex-col gap-8 lg:flex-row lg:items-center">
        {/* Left Column: Introduction & CTA */}
        <div className="flex flex-col justify-between lg:w-5/12 xl:w-4/12">
          <div>
            <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-[#A31C1B]/40 bg-[#A31C1B]/15 px-3 py-1 text-xs font-semibold text-red-200">
              <span className="h-1.5 w-1.5 rounded-full bg-[#A31C1B] animate-pulse" />
              <span>Featured Learning</span>
            </div>

            <h2
              className="text-2xl sm:text-3xl lg:text-4xl font-extrabold tracking-tight text-white leading-tight"
              id="featured-path-heading"
            >
              Discover Your Next Learning Path
            </h2>

            <p className="mt-3 text-sm sm:text-base text-neutral-300 leading-relaxed">
              Explore courses, build practical skills, and learn from the
              PeerGeek community.
            </p>
          </div>

          <div className="mt-6 flex flex-wrap items-center gap-3">
            <Link
              className="inline-flex items-center gap-2 rounded-xl bg-[#A31C1B] px-5 py-2.5 text-sm font-bold text-white shadow-md transition-all hover:bg-[#881716] hover:shadow-lg focus:outline-none focus:ring-2 focus:ring-[#A31C1B] focus:ring-offset-2 focus:ring-offset-[#06110C]"
              to="/courses"
            >
              <span>Explore all courses</span>
              <span aria-hidden="true">→</span>
            </Link>

            {courses.length > 2 && (
              <div className="hidden sm:flex items-center gap-1.5 ml-2">
                <button
                  aria-label="Scroll featured courses left"
                  className="flex h-8 w-8 items-center justify-center rounded-full border border-neutral-700 bg-[#06110C] text-neutral-200 hover:border-emerald-600/60 hover:text-white transition-colors"
                  onClick={() => handleScroll("left")}
                  type="button"
                >
                  ‹
                </button>
                <button
                  aria-label="Scroll featured courses right"
                  className="flex h-8 w-8 items-center justify-center rounded-full border border-neutral-700 bg-[#06110C] text-neutral-200 hover:border-emerald-600/60 hover:text-white transition-colors"
                  onClick={() => handleScroll("right")}
                  type="button"
                >
                  ›
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Right Column: Featured Cards Horizontal Carousel */}
        <div className="lg:w-7/12 xl:w-8/12 min-w-0">
          {popularQuery.isLoading && (
            <div className="flex gap-4 overflow-x-hidden">
              {Array.from({ length: 3 }).map((_, i) => (
                <div
                  className="w-[260px] sm:w-[280px] shrink-0 animate-pulse rounded-xl border border-neutral-800 bg-[#171717] p-4"
                  key={i}
                >
                  <div className="aspect-video w-full rounded-lg bg-neutral-800" />
                  <div className="mt-3 h-4 w-3/4 rounded bg-neutral-800" />
                  <div className="mt-2 h-3 w-1/2 rounded bg-neutral-800" />
                </div>
              ))}
            </div>
          )}

          {!popularQuery.isLoading && courses.length === 0 && (
            <div className="rounded-xl border border-neutral-800 bg-[#171717]/80 p-6 text-center">
              <p className="text-sm text-neutral-400">
                New featured paths are coming soon. Check out our public courses
                catalogue.
              </p>
              <Link
                className="mt-3 inline-block text-xs font-semibold text-[#A31C1B] hover:underline"
                to="/courses"
              >
                Browse all courses →
              </Link>
            </div>
          )}

          {!popularQuery.isLoading && courses.length > 0 && (
            <div
              aria-label="Featured courses list"
              className="flex gap-4 overflow-x-auto pb-2 scrollbar-none snap-x snap-mandatory"
              ref={scrollRef}
              role="region"
              tabIndex={0}
            >
              {courses.slice(0, 6).map((course) => {
                const hasRating =
                  course.stats?.ratingCount != null &&
                  course.stats.ratingCount > 0;
                const ratingAvg =
                  course.stats?.ratingAverage != null
                    ? Number(course.stats.ratingAverage).toFixed(1)
                    : null;

                return (
                  <article
                    className="w-[260px] sm:w-[280px] shrink-0 snap-start"
                    key={course.id}
                  >
                    <Link
                      aria-label={`${course.title} by ${course.instructor?.displayName ?? "Instructor"}`}
                      className="group flex h-full flex-col overflow-hidden rounded-xl border border-neutral-800 bg-[#171717] transition-all duration-200 hover:border-[#A31C1B]/60 hover:shadow-lg focus:outline-none focus:ring-2 focus:ring-[#A31C1B]"
                      to={`/courses/${course.id}`}
                    >
                      {/* Thumbnail or fallback banner */}
                      <div className="relative aspect-video w-full overflow-hidden bg-neutral-900">
                        {course.thumbnailUrl ? (
                          <img
                            alt={course.title}
                            className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
                            loading="lazy"
                            src={course.thumbnailUrl}
                          />
                        ) : (
                          <div className="flex h-full w-full flex-col items-center justify-center bg-gradient-to-br from-[#06110C] to-[#1f1f1f] p-3 text-center">
                            <span className="text-2xl font-bold tracking-wider text-white">
                              {course.title
                                ? course.title.charAt(0).toUpperCase()
                                : "P"}
                            </span>
                            <span className="mt-1 line-clamp-1 text-xs text-neutral-400">
                              {course.category?.name ?? "PeerGeek"}
                            </span>
                          </div>
                        )}
                        {course.difficulty && (
                          <span className="absolute right-2.5 top-2.5 rounded-full border border-emerald-950/60 bg-[#06110C]/90 px-2.5 py-0.5 text-[10px] font-semibold text-white backdrop-blur-xs">
                            {course.difficulty}
                          </span>
                        )}
                      </div>

                      {/* Content */}
                      <div className="flex flex-1 flex-col p-4">
                        {course.category?.name && (
                          <p className="text-[11px] font-bold uppercase tracking-wider text-[#A31C1B]">
                            {course.category.name}
                          </p>
                        )}
                        <h3 className="mt-1 line-clamp-2 text-sm font-bold text-white group-hover:text-red-300 transition-colors">
                          {course.title}
                        </h3>
                        <p className="mt-1 line-clamp-2 text-xs text-neutral-400">
                          {course.shortDescription}
                        </p>

                        <div className="mt-auto pt-3 flex items-center justify-between text-xs text-neutral-400">
                          <span className="truncate">
                            By {course.instructor?.displayName ?? "Peer"}
                          </span>
                          {hasRating && (
                            <span className="flex items-center gap-1 font-semibold text-amber-400 shrink-0">
                              <span aria-hidden="true">★</span>
                              <span>{ratingAvg}</span>
                            </span>
                          )}
                        </div>
                      </div>
                    </Link>
                  </article>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
