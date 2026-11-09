import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getMyCourses } from "./learningApi";
import { InstructorModeBanner } from "./InstructorModeBanner";
import { Spinner } from "../../shared/components/Spinner";
import { EmptyState } from "../../shared/components/EmptyState";
import { ErrorState } from "../../shared/components/ErrorState";
import { Button } from "../../shared/components/Button";
import { DomainCommunityEntryButton } from "../community/DomainCommunityEntryButton";
import { getMyCertificates, getCertificateDownloadUrl, reconcileCertificates } from "../certificate/certificateApi";

const TABS = [
  { id: "all", label: "All Courses" },
  { id: "in-progress", label: "In Progress" },
  { id: "completed", label: "Completed" },
];

export function MyLearningPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const currentTab = searchParams.get("status") || "all";
  const currentQ = searchParams.get("q") || "";
  const currentPage = parseInt(searchParams.get("page") || "0", 10);

  const [searchInput, setSearchInput] = useState(currentQ);
  const [prevCurrentQ, setPrevCurrentQ] = useState(currentQ);
  if (currentQ !== prevCurrentQ) {
    setPrevCurrentQ(currentQ);
    setSearchInput(currentQ);
  }

  const { data: certificates = [] } = useQuery({
    queryKey: ["certificates", "mine"],
    queryFn: async () => {
      try {
        await reconcileCertificates();
      } catch (err) {
        console.warn("Certificate reconciliation warning:", err);
      }
      return getMyCertificates();
    },
  });
  const certificatesByCourse = useMemo(
    () => new Map(certificates.map((certificate) => [String(certificate.courseId), certificate])),
    [certificates],
  );

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["learning", "courses", currentTab, currentQ, currentPage],
    queryFn: () =>
      getMyCourses({
        status: currentTab,
        q: currentQ,
        page: currentPage,
        size: 12,
        sort: "lastAccessedAt,desc",
      }),
  });

  function handleTabChange(tabId) {
    const next = new URLSearchParams(searchParams);
    if (tabId === "all") {
      next.delete("status");
    } else {
      next.set("status", tabId);
    }
    next.set("page", "0");
    setSearchParams(next);
  }

  function handleSearchSubmit(e) {
    e.preventDefault();
    const next = new URLSearchParams(searchParams);
    if (searchInput.trim()) {
      next.set("q", searchInput.trim());
    } else {
      next.delete("q");
    }
    next.set("page", "0");
    setSearchParams(next);
  }

  function handlePageChange(newPage) {
    const next = new URLSearchParams(searchParams);
    next.set("page", String(newPage));
    setSearchParams(next);
  }

  const courses = useMemo(
    () => (Array.isArray(data?.content) ? data.content : []),
    [data],
  );
  const totalPages = data?.totalPages ?? 0;

  // Group enrolled courses by their domain/category
  const domainSections = useMemo(() => {
    const groups = new Map();
    for (const course of courses) {
      const cat = course.category || {
        id: 0,
        name: "General Learning",
        slug: "general",
      };
      const key = cat.slug || "general";
      if (!groups.has(key)) {
        groups.set(key, {
          slug: cat.slug || "general",
          name: cat.name || "General Learning",
          courses: [],
        });
      }
      groups.get(key).courses.push(course);
    }
    return Array.from(groups.values());
  }, [courses]);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <InstructorModeBanner />

      <header className="mb-8">
        <h1 className="text-3xl font-extrabold tracking-tight text-ink-900">
          My Learning
        </h1>
        <p className="mt-2 text-base text-ink-600">
          Continue your journey, review completed modules, and track your
          learning achievements.
        </p>
      </header>

      {/* Tabs and Search Bar */}
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-surface-200 pb-4">
        {/* Tabs */}
        <nav className="flex space-x-2" aria-label="Learning status filter">
          {TABS.map((tab) => {
            const isActive = currentTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => handleTabChange(tab.id)}
                className={`rounded-lg px-4 py-2 text-sm font-semibold transition ${
                  isActive
                    ? "bg-brand-600 text-white shadow-xs"
                    : "text-ink-600 hover:bg-surface-100 hover:text-ink-900"
                }`}
              >
                {tab.label}
              </button>
            );
          })}
        </nav>

        {/* Search */}
        <form onSubmit={handleSearchSubmit} className="flex gap-2">
          <input
            type="search"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search enrolled courses..."
            className="w-full sm:w-64 rounded-lg border border-surface-300 px-3 py-1.5 text-sm text-ink-900 placeholder:text-ink-400 focus:border-brand-500 focus:outline-hidden focus:ring-1 focus:ring-brand-500"
          />
          <Button
            type="submit"
            variant="secondary"
            className="px-3 py-1.5 text-xs"
          >
            Search
          </Button>
        </form>
      </div>

      {/* Content Area */}
      {isLoading ? (
        <div className="flex flex-col items-center justify-center py-20">
          <Spinner label="Loading your courses..." />
        </div>
      ) : isError ? (
        <ErrorState
          title="Could not load your enrolled courses"
          description={
            error?.message ||
            "Please check your network connection and try again."
          }
          action={
            <Button
              variant="secondary"
              onClick={() => refetch()}
              className="mt-4"
            >
              Retry
            </Button>
          }
        />
      ) : courses.length === 0 ? (
        <EmptyState
          title={
            currentQ
              ? "No courses match your search"
              : currentTab === "completed"
                ? "No completed courses yet"
                : "You haven't enrolled in any courses yet"
          }
          description={
            currentQ
              ? "Try adjusting your search terms or filters to find what you're looking for."
              : "Discover free courses created by peers and industry practitioners to begin learning."
          }
          action={
            <Link
              to="/courses"
              className="mt-4 inline-flex items-center justify-center rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700"
            >
              Explore courses
            </Link>
          }
        />
      ) : (
        <>
          <div className="space-y-12">
            {domainSections.map((section) => (
              <section
                key={section.slug}
                aria-labelledby={`domain-heading-${section.slug}`}
                className="space-y-4"
              >
                {/* Domain Section Header */}
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-surface-200 pb-3">
                  <div className="flex items-center gap-2">
                    <span
                      aria-hidden="true"
                      className="h-4 w-1 rounded-full bg-[#06110C] shrink-0"
                    />
                    <h2
                      id={`domain-heading-${section.slug}`}
                      className="text-xl font-bold tracking-tight text-[#171717]"
                    >
                      {section.name}
                    </h2>
                    <span className="text-xs font-semibold text-white bg-[#06110C] px-2.5 py-0.5 rounded-full shadow-2xs">
                      {section.courses.length} course{section.courses.length === 1 ? "" : "s"}
                    </span>
                  </div>

                  <DomainCommunityEntryButton
                    domainSlug={section.slug}
                    domainName={section.name}
                    label="Open Community"
                  />
                </div>

                {/* Course Grid for this Domain */}
                <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
                  {section.courses.map((course) => {
                    const progress = course.progress ?? {
                      percent: 0,
                      completedLessons: 0,
                      totalLessons: 0,
                    };
                    const isUnpublished = course.courseStatus === "UNPUBLISHED";
                    const certificate = certificatesByCourse.get(String(course.courseId));

                    return (
                      <article
                        key={course.courseId}
                        className="flex flex-col overflow-hidden rounded-xl border border-surface-200 bg-white shadow-xs transition hover:shadow-md"
                      >
                        {/* Thumbnail / Header graphic */}
                        <div className="relative h-44 w-full bg-surface-100 overflow-hidden">
                          {course.thumbnailUrl ? (
                            <img
                              src={course.thumbnailUrl}
                              alt={course.title}
                              className="h-full w-full object-cover"
                            />
                          ) : (
                            <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-brand-600 to-indigo-700 p-4 text-center">
                              <span className="text-xl font-bold text-white line-clamp-2">
                                {course.title}
                              </span>
                            </div>
                          )}

                          {isUnpublished && (
                            <span className="absolute top-2.5 right-2.5 rounded-md bg-amber-500/90 px-2 py-0.5 text-xs font-semibold text-white shadow-xs">
                              No longer accepting new learners
                            </span>
                          )}
                        </div>

                        {/* Body */}
                        <div className="flex flex-1 flex-col p-5">
                          <div className="flex items-center justify-between text-xs font-medium text-ink-500">
                            <span>
                              {course.instructor?.displayName || "Instructor"}
                            </span>
                            {course.difficulty && (
                              <span className="rounded-full bg-surface-100 px-2 py-0.5 font-semibold text-ink-600">
                                {course.difficulty}
                              </span>
                            )}
                          </div>

                          <h3 className="mt-2 text-lg font-bold text-ink-900 line-clamp-2">
                            {course.title}
                          </h3>

                          {/* Progress Bar */}
                          <div className="mt-auto pt-5">
                            <div className="flex items-center justify-between text-xs font-semibold text-ink-700 mb-1">
                              <span>Progress</span>
                              <span>{progress.percent}%</span>
                            </div>
                            <div className="h-2 w-full overflow-hidden rounded-full bg-surface-100">
                              <div
                                className="h-full bg-brand-600 transition-all duration-300"
                                style={{
                                  width: `${Math.min(100, progress.percent)}%`,
                                }}
                              />
                            </div>
                            <div className="mt-1.5 flex justify-between text-xs text-ink-500">
                              <span>
                                {progress.completedLessons} of {progress.totalLessons}{" "}
                                lessons completed
                              </span>
                            </div>

                            <div className="mt-4 flex flex-col gap-2">
                              <Link
                                to={`/learning/courses/${course.courseId}`}
                                className="inline-flex w-full items-center justify-center rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white shadow-xs transition hover:bg-brand-700"
                              >
                                Continue
                              </Link>
                              <Link
                                to={`/courses/${course.courseId}/reviews`}
                                className="inline-flex w-full items-center justify-center rounded-lg border border-surface-300 bg-white px-4 py-2 text-sm font-semibold text-brand-600 shadow-xs transition hover:bg-brand-50 hover:border-brand-300"
                              >
                                Reviews
                              </Link>
                              {certificate?.status === "ISSUED" && (
                                <button
                                  type="button"
                                  onClick={async () => {
                                    const url = await getCertificateDownloadUrl(certificate.id);
                                    window.open(url, "_blank", "noopener,noreferrer");
                                  }}
                                  className="inline-flex w-full items-center justify-center rounded-lg border border-[#a31c1b] px-4 py-2 text-sm font-semibold text-[#a31c1b]"
                                >
                                  Download Certificate
                                </button>
                              )}
                              {certificate?.status === "PENDING" && (
                                <div className="text-center text-xs font-semibold text-blue-700">
                                  Certificate generating...
                                </div>
                              )}
                              {certificate?.status === "FAILED" && (
                                <Link
                                  to="/certificates"
                                  className="text-center text-xs font-semibold text-amber-700"
                                >
                                  Certificate generation needs attention
                                </Link>
                              )}
                              {progress.percent === 100 && !certificate && (
                                <Link
                                  to="/certificates"
                                  className="text-center text-xs font-semibold text-emerald-700 underline"
                                >
                                  Course Completed — Claim Certificate
                                </Link>
                              )}
                            </div>
                          </div>
                        </div>
                      </article>
                    );
                  })}
                </div>
              </section>
            ))}
          </div>

          {/* Pagination Controls */}
          {totalPages > 1 && (
            <div className="mt-10 flex items-center justify-center gap-3">
              <Button
                variant="secondary"
                disabled={currentPage <= 0}
                onClick={() => handlePageChange(currentPage - 1)}
                className="text-xs"
              >
                Previous
              </Button>
              <span className="text-sm font-medium text-ink-700">
                Page {currentPage + 1} of {totalPages}
              </span>
              <Button
                variant="secondary"
                disabled={currentPage >= totalPages - 1}
                onClick={() => handlePageChange(currentPage + 1)}
                className="text-xs"
              >
                Next
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
