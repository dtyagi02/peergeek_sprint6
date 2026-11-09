import { useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Button } from "../../shared/components/Button";
import { Dialog } from "../../shared/components/Dialog";
import { EmptyState } from "../../shared/components/EmptyState";
import { ErrorState } from "../../shared/components/ErrorState";
import { Input } from "../../shared/components/Input";
import { CourseCard, CourseCardSkeleton } from "./CourseCard";
import { CategorySection, CategorySectionSkeleton } from "./CategorySection";
import { listPublishedCourses, getPublicCategories } from "./discoveryApi";

const VALID_DIFFICULTIES = ["BEGINNER", "INTERMEDIATE", "ADVANCED"];
const VALID_SORTS = [
  "popularity,desc",
  "publishedAt,desc",
  "rating,desc",
  "title,asc",
  "relevance",
];

function FilterControls({
  categorySelectId,
  urlCategory,
  categories,
  onCategoryChange,
  urlDifficulties,
  onDifficultyToggle,
  sortSelectId,
  sort,
  onSortChange,
  urlQ,
  onClearAll,
}) {
  return (
    <div className="space-y-6">
      {/* Category filter */}
      <div>
        <label
          className="block text-sm font-semibold text-[#171717]"
          htmlFor={categorySelectId}
        >
          Category
        </label>
        <select
          className="mt-2 block w-full rounded-md border border-surface-200 bg-white px-3 py-2 text-sm text-[#171717] focus:border-[#A31C1B] focus:outline-none focus:ring-1 focus:ring-[#A31C1B]"
          id={categorySelectId}
          onChange={(e) => onCategoryChange(e.target.value)}
          value={urlCategory}
        >
          <option value="">All categories</option>
          {categories?.map((cat) => (
            <option key={cat.id} value={cat.slug}>
              {cat.name} ({cat.courseCount})
            </option>
          ))}
        </select>
      </div>

      {/* Difficulty multi-select */}
      <div>
        <fieldset>
          <legend className="text-sm font-semibold text-[#171717]">
            Difficulty
          </legend>
          <div className="mt-2 space-y-2">
            {[
              { id: "BEGINNER", label: "Beginner" },
              { id: "INTERMEDIATE", label: "Intermediate" },
              { id: "ADVANCED", label: "Advanced" },
            ].map((diff) => {
              const checked = urlDifficulties.includes(diff.id);
              return (
                <label
                  className="flex items-center gap-2.5 text-sm text-ink-700 cursor-pointer"
                  key={diff.id}
                >
                  <input
                    checked={checked}
                    className="h-4 w-4 rounded border-surface-300 text-[#A31C1B] focus:ring-[#A31C1B]"
                    onChange={() => onDifficultyToggle(diff.id)}
                    type="checkbox"
                  />
                  <span>{diff.label}</span>
                </label>
              );
            })}
          </div>
        </fieldset>
      </div>

      {/* Sort select */}
      <div>
        <label
          className="block text-sm font-semibold text-[#171717]"
          htmlFor={sortSelectId}
        >
          Sort by
        </label>
        <select
          className="mt-2 block w-full rounded-md border border-surface-200 bg-white px-3 py-2 text-sm text-[#171717] focus:border-[#A31C1B] focus:outline-none focus:ring-1 focus:ring-[#A31C1B]"
          id={sortSelectId}
          onChange={(e) => onSortChange(e.target.value)}
          value={sort}
        >
          {urlQ.trim() && <option value="relevance">Most relevant</option>}
          <option value="popularity,desc">Most popular</option>
          <option value="publishedAt,desc">Newest</option>
          <option value="rating,desc">Highest rated</option>
          <option value="title,asc">Title (A-Z)</option>
        </select>
      </div>

      {(urlQ || urlCategory || urlDifficulties.length > 0) && (
        <Button className="w-full" onClick={onClearAll} variant="secondary">
          Clear all filters
        </Button>
      )}
    </div>
  );
}

export function DiscoverPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  // URL state extraction with validation and fallbacks
  const urlQ = searchParams.get("q") ?? "";
  const urlCategory = searchParams.get("category") ?? "";
  const urlSort = searchParams.get("sort") ?? "";
  const rawPage = parseInt(searchParams.get("page") ?? "0", 10);
  const page = Number.isInteger(rawPage) && rawPage >= 0 ? rawPage : 0;

  // Difficulty can be comma-separated or multiple params in URL
  const urlDifficulties = useMemo(() => {
    const rawAll = searchParams.getAll("difficulty");
    const diffs = new Set();
    rawAll.forEach((val) => {
      val.split(",").forEach((d) => {
        const trimmed = d.trim().toUpperCase();
        if (VALID_DIFFICULTIES.includes(trimmed)) diffs.add(trimmed);
      });
    });
    return Array.from(diffs);
  }, [searchParams]);

  // Determine active sort with fallback
  const sort = useMemo(() => {
    if (urlSort && VALID_SORTS.includes(urlSort)) {
      if (urlSort === "relevance" && !urlQ.trim()) {
        return "popularity,desc";
      }
      return urlSort;
    }
    return urlQ.trim() ? "relevance" : "popularity,desc";
  }, [urlSort, urlQ]);

  // Local state for debounced search input with standard prop-sync
  const [inputValue, setInputValue] = useState(urlQ);
  const [prevUrlQ, setPrevUrlQ] = useState(urlQ);
  if (urlQ !== prevUrlQ) {
    setPrevUrlQ(urlQ);
    setInputValue(urlQ);
  }

  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false);

  // Debounce search input update to URL (~300ms)
  useEffect(() => {
    if (inputValue === urlQ) return;
    const timer = setTimeout(() => {
      const newParams = new URLSearchParams(searchParams);
      if (inputValue.trim()) {
        newParams.set("q", inputValue.trim());
      } else {
        newParams.delete("q");
      }
      newParams.delete("page");
      setSearchParams(newParams);
    }, 300);
    return () => clearTimeout(timer);
  }, [inputValue, urlQ, searchParams, setSearchParams]);

  // Categories query
  const categoriesQuery = useQuery({
    queryKey: ["public-categories"],
    queryFn: getPublicCategories,
    staleTime: 5 * 60 * 1000,
  });

  // Query size: when in a specific category, fetch standard page size (12);
  // when browsing all categories, fetch up to 50 courses to populate category rows in a single query.
  const querySize = urlCategory ? 12 : 50;

  // Main search query
  const coursesQuery = useQuery({
    queryKey: [
      "public-courses",
      {
        q: urlQ.trim(),
        category: urlCategory,
        difficulty: urlDifficulties.sort().join(","),
        page,
        size: querySize,
        sort,
      },
    ],
    queryFn: () =>
      listPublishedCourses({
        q: urlQ.trim() || undefined,
        category: urlCategory || undefined,
        difficulty: urlDifficulties.length > 0 ? urlDifficulties : undefined,
        page,
        size: querySize,
        sort,
      }),
    placeholderData: keepPreviousData,
    staleTime: 60 * 1000,
  });

  function updateFilterParams(updates) {
    const newParams = new URLSearchParams(searchParams);

    Object.entries(updates).forEach(([key, val]) => {
      if (val === null || val === undefined || val === "") {
        newParams.delete(key);
      } else if (Array.isArray(val)) {
        newParams.delete(key);
        val.forEach((item) => newParams.append(key, item));
      } else {
        newParams.set(key, String(val));
      }
    });

    setSearchParams(newParams);
  }

  function handleSearchSubmit(e) {
    e.preventDefault();
    updateFilterParams({ q: inputValue.trim() || null, page: 0 });
  }

  function handleClearSearch() {
    setInputValue("");
    updateFilterParams({ q: null, page: 0 });
  }

  function handleCategoryChange(categorySlug) {
    updateFilterParams({ category: categorySlug || null, page: 0 });
  }

  function handleDifficultyToggle(diff) {
    const next = urlDifficulties.includes(diff)
      ? urlDifficulties.filter((d) => d !== diff)
      : [...urlDifficulties, diff];
    updateFilterParams({ difficulty: next.length > 0 ? next : null, page: 0 });
  }

  function handleSortChange(newSort) {
    updateFilterParams({ sort: newSort, page: 0 });
  }

  function handleClearAllFilters() {
    setInputValue("");
    setSearchParams(new URLSearchParams());
  }

  function handlePageChange(newPage) {
    updateFilterParams({ page: newPage });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  const searchInputId = useId();
  const categorySelectId = useId();
  const sortSelectId = useId();

  const totalElements = coursesQuery.data?.totalElements ?? 0;
  const totalPages = coursesQuery.data?.totalPages ?? 0;
  const courses = Array.isArray(coursesQuery.data?.content)
    ? coursesQuery.data.content
    : [];

  // Active category object if urlCategory is present
  const activeCategory = useMemo(() => {
    if (!urlCategory) return null;
    const found = categoriesQuery.data?.find((c) => c.slug === urlCategory);
    if (found) return found;
    const fallbackTitle = urlCategory
      .split("-")
      .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
      .join(" ");
    return { name: fallbackTitle, slug: urlCategory };
  }, [urlCategory, categoriesQuery.data]);

  // Group courses by real categories for multi-category browsing view
  const categorySections = useMemo(() => {
    if (!courses || courses.length === 0) return [];

    const catList =
      Array.isArray(categoriesQuery.data) && categoriesQuery.data.length > 0
        ? categoriesQuery.data
        : [];

    const map = new Map();
    catList.forEach((cat) => {
      map.set(cat.slug, {
        id: cat.id,
        name: cat.name,
        slug: cat.slug,
        courseCount: cat.courseCount,
        courses: [],
      });
    });

    const uncategorized = [];

    courses.forEach((course) => {
      const slug = course.category?.slug;
      if (slug && map.has(slug)) {
        map.get(slug).courses.push(course);
      } else if (course.category?.name) {
        const found = catList.find(
          (c) =>
            c.name.toLowerCase() === course.category.name.toLowerCase() ||
            c.id === course.category.id,
        );
        if (found && map.has(found.slug)) {
          map.get(found.slug).courses.push(course);
        } else {
          // Dynamic category from course if not present in catList
          const autoSlug =
            course.category.slug ||
            course.category.name.toLowerCase().replace(/\s+/g, "-");
          if (!map.has(autoSlug)) {
            map.set(autoSlug, {
              id: course.category.id,
              name: course.category.name,
              slug: autoSlug,
              courseCount: 1,
              courses: [course],
            });
          } else {
            map.get(autoSlug).courses.push(course);
          }
        }
      } else {
        uncategorized.push(course);
      }
    });

    const sections = Array.from(map.values()).filter(
      (s) => s.courses.length > 0,
    );

    if (uncategorized.length > 0) {
      sections.push({
        id: "uncategorized",
        name: "Other Courses",
        slug: "other",
        courseCount: uncategorized.length,
        courses: uncategorized,
      });
    }

    return sections;
  }, [categoriesQuery.data, courses]);

  return (
    <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      {/* Page Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-extrabold tracking-tight text-ink-900 sm:text-4xl">
          Explore Courses
        </h1>
        <p className="mt-2 text-base text-ink-700">
          Discover high-quality courses designed and published by peers across
          the community.
        </p>
      </div>

      {/* Search Bar and Mobile Filter Toggle */}
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center">
        <form
          className="relative flex-1"
          onSubmit={handleSearchSubmit}
          role="search"
        >
          <label className="sr-only" htmlFor={searchInputId}>
            Search courses
          </label>
          <Input
            aria-label="Search courses"
            className="pl-4 pr-10 text-base"
            id={searchInputId}
            onChange={(e) => setInputValue(e.target.value)}
            placeholder="Search by title, topic, instructor, or keyword..."
            type="search"
            value={inputValue}
          />
          {inputValue && (
            <button
              aria-label="Clear search text"
              className="absolute right-3 top-1/2 -translate-y-1/2 rounded-full p-1 text-slate-400 hover:text-ink-700 focus:outline-none focus:ring-2 focus:ring-brand-500"
              onClick={handleClearSearch}
              type="button"
            >
              ✕
            </button>
          )}
        </form>

        {/* Mobile Filter Button */}
        <div className="sm:hidden">
          <Button
            className="w-full justify-between"
            onClick={() => setMobileFiltersOpen(true)}
            variant="secondary"
          >
            <span>Filters</span>
            {urlDifficulties.length + (urlCategory ? 1 : 0) > 0 && (
              <span className="rounded-full bg-[#06110C] px-2 py-0.5 text-xs font-semibold text-white">
                {urlDifficulties.length + (urlCategory ? 1 : 0)}
              </span>
            )}
          </Button>

          <Dialog
            description="Adjust category, difficulty, and sorting to filter courses."
            onOpenChange={setMobileFiltersOpen}
            open={mobileFiltersOpen}
            title="Filters"
          >
            <FilterControls
              categories={categoriesQuery.data}
              categorySelectId={categorySelectId}
              onCategoryChange={handleCategoryChange}
              onClearAll={handleClearAllFilters}
              onDifficultyToggle={handleDifficultyToggle}
              onSortChange={handleSortChange}
              sort={sort}
              sortSelectId={sortSelectId}
              urlCategory={urlCategory}
              urlDifficulties={urlDifficulties}
              urlQ={urlQ}
            />
            <div className="mt-6">
              <Button
                className="w-full"
                onClick={() => setMobileFiltersOpen(false)}
              >
                Done
              </Button>
            </div>
          </Dialog>
        </div>
      </div>

      {/* Main Layout: Sidebar Filters (desktop) + Course Results */}
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-4">
        {/* Desktop Sidebar */}
        <aside
          aria-label="Course filters"
          className="hidden lg:col-span-1 lg:block"
        >
          <div className="sticky top-6 rounded-xl border border-surface-200 bg-white p-5 shadow-xs">
            <h2 className="mb-5 text-base font-bold text-[#171717] flex items-center gap-2">
              <span
                aria-hidden="true"
                className="h-3.5 w-1 rounded-full bg-[#06110C]"
              />
              <span>Filters</span>
            </h2>
            <FilterControls
              categories={categoriesQuery.data}
              categorySelectId={categorySelectId}
              onCategoryChange={handleCategoryChange}
              onClearAll={handleClearAllFilters}
              onDifficultyToggle={handleDifficultyToggle}
              onSortChange={handleSortChange}
              sort={sort}
              sortSelectId={sortSelectId}
              urlCategory={urlCategory}
              urlDifficulties={urlDifficulties}
              urlQ={urlQ}
            />
          </div>
        </aside>

        {/* Results Area */}
        <div className="lg:col-span-3 min-w-0">
          {/* Result count and live region */}
          <div className="mb-4 flex items-center justify-between">
            <div
              aria-atomic="true"
              aria-live="polite"
              className="text-sm font-medium text-slate-600"
            >
              {coursesQuery.isLoading
                ? "Loading courses..."
                : `Showing ${totalElements} course${totalElements === 1 ? "" : "s"}`}
            </div>
          </div>

          {/* Loading Skeletons */}
          {coursesQuery.isLoading &&
            (urlCategory ? (
              <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 xl:grid-cols-3">
                {Array.from({ length: 6 }).map((_, i) => (
                  <CourseCardSkeleton key={i} />
                ))}
              </div>
            ) : (
              <div className="space-y-12">
                <CategorySectionSkeleton />
                <CategorySectionSkeleton />
              </div>
            ))}

          {/* Error State */}
          {coursesQuery.isError && (
            <div className="space-y-4">
              <ErrorState
                description="We encountered an issue fetching courses. Please verify your connection and try again."
                title="Failed to load courses"
              />
              <Button onClick={() => coursesQuery.refetch()} variant="primary">
                Retry
              </Button>
            </div>
          )}

          {/* Empty State */}
          {!coursesQuery.isLoading &&
            !coursesQuery.isError &&
            courses.length === 0 && (
              <EmptyState
                action={
                  <Button onClick={handleClearAllFilters} variant="secondary">
                    Clear filters
                  </Button>
                }
                description="Try adjusting your search terms or filters to find what you're looking for."
                title="No courses match"
              />
            )}

          {/* Course Results */}
          {!coursesQuery.isLoading &&
            !coursesQuery.isError &&
            courses.length > 0 &&
            (urlCategory ? (
              <div>
                {/* Active Category Header with Back Breadcrumb */}
                <div className="mb-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-surface-200 pb-4">
                  <div>
                    <nav aria-label="Category breadcrumb" className="mb-1">
                      <ol className="flex items-center gap-2 text-sm text-slate-500">
                        <li>
                          <button
                            className="text-[#06110C] hover:text-[#A31C1B] hover:underline flex items-center gap-1 font-semibold transition-colors"
                            onClick={() => handleCategoryChange("")}
                            type="button"
                          >
                            ← All categories
                          </button>
                        </li>
                        <li aria-hidden="true">/</li>
                        <li
                          aria-current="page"
                          className="font-semibold"
                        >
                          <span className="rounded-full bg-[#06110C] px-2.5 py-0.5 text-xs text-white">
                            {activeCategory.name}
                          </span>
                        </li>
                      </ol>
                    </nav>
                    <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-[#171717]">
                      {activeCategory.name}
                    </h2>
                  </div>
                  <Button
                    className="self-start sm:self-auto text-sm"
                    onClick={() => handleCategoryChange("")}
                    variant="secondary"
                  >
                    View all categories
                  </Button>
                </div>

                {/* Single-Category Course Grid */}
                <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 xl:grid-cols-3">
                  {courses.map((course) => (
                    <CourseCard course={course} key={course.id} />
                  ))}
                </div>

                {/* Pagination Controls */}
                {totalPages > 1 && (
                  <nav
                    aria-label="Course pagination"
                    className="mt-12 flex items-center justify-between border-t border-surface-200 pt-6"
                  >
                    <Button
                      aria-label="Go to previous page"
                      disabled={page <= 0 || coursesQuery.isFetching}
                      onClick={() => handlePageChange(page - 1)}
                      variant="secondary"
                    >
                      ← Previous
                    </Button>

                    <span className="text-sm font-medium text-slate-600">
                      Page {page + 1} of {totalPages}
                    </span>

                    <Button
                      aria-label="Go to next page"
                      disabled={
                        page >= totalPages - 1 || coursesQuery.isFetching
                      }
                      onClick={() => handlePageChange(page + 1)}
                      variant="secondary"
                    >
                      Next →
                    </Button>
                  </nav>
                )}
              </div>
            ) : (
              /* Multi-Category Browsing Experience */
              <div>
                <div className="space-y-12">
                  {categorySections.map((section) => (
                    <CategorySection
                      key={section.id || section.slug}
                      onCategoryClick={handleCategoryChange}
                      onShowMore={handleCategoryChange}
                      section={section}
                    />
                  ))}
                </div>

                {/* Pagination if more than 50 courses across categories */}
                {totalPages > 1 && (
                  <nav
                    aria-label="Course pagination"
                    className="mt-12 flex items-center justify-between border-t border-surface-200 pt-6"
                  >
                    <Button
                      aria-label="Go to previous page"
                      disabled={page <= 0 || coursesQuery.isFetching}
                      onClick={() => handlePageChange(page - 1)}
                      variant="secondary"
                    >
                      ← Previous
                    </Button>

                    <span className="text-sm font-medium text-slate-600">
                      Page {page + 1} of {totalPages}
                    </span>

                    <Button
                      aria-label="Go to next page"
                      disabled={
                        page >= totalPages - 1 || coursesQuery.isFetching
                      }
                      onClick={() => handlePageChange(page + 1)}
                      variant="secondary"
                    >
                      Next →
                    </Button>
                  </nav>
                )}
              </div>
            ))}
        </div>
      </div>
    </main>
  );
}
