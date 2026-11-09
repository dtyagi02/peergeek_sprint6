import { useCallback, useEffect, useRef, useState } from "react";
import { CourseCard } from "./CourseCard";
import { DomainCommunityEntryButton } from "../community/DomainCommunityEntryButton";

export function CategorySection({ section, onShowMore, onCategoryClick }) {
  const rowRef = useRef(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const checkScroll = useCallback(() => {
    const el = rowRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 10);
    setCanScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 10);
  }, []);

  useEffect(() => {
    const el = rowRef.current;
    if (!el) return;
    checkScroll();
    el.addEventListener("scroll", checkScroll, { passive: true });
    window.addEventListener("resize", checkScroll);
    return () => {
      el.removeEventListener("scroll", checkScroll);
      window.removeEventListener("resize", checkScroll);
    };
  }, [checkScroll, section.courses]);

  const handleScroll = (direction) => {
    const el = rowRef.current;
    if (!el) return;
    const scrollAmount = direction === "left" ? -340 : 340;
    el.scrollBy({ left: scrollAmount, behavior: "smooth" });
  };

  const displayedCards = section.courses;
  const totalInCat = Math.max(
    section.courseCount ?? 0,
    section.courses.length,
  );
  // When a category contains more courses than initially displayed (4), show "Show more" button
  const hasMore = totalInCat > 4 || totalInCat > displayedCards.length;
  const remainingCount =
    totalInCat > 4 ? totalInCat - 4 : totalInCat - displayedCards.length;

  return (
    <section
      aria-labelledby={`category-heading-${section.slug}`}
      className="relative"
    >
      {/* Category Heading & Meta */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2
          className="text-xl sm:text-2xl font-bold tracking-tight text-[#171717]"
          id={`category-heading-${section.slug}`}
        >
          <button
            aria-label={`View all courses in ${section.name}`}
            className="group inline-flex items-center gap-2 hover:text-[#A31C1B] transition-colors text-left font-bold"
            onClick={() => onCategoryClick(section.slug)}
            type="button"
          >
            <span
              aria-hidden="true"
              className="h-4 w-1 rounded-full bg-[#06110C] shrink-0"
            />
            <span>{section.name}</span>
            <span
              aria-hidden="true"
              className="text-neutral-400 group-hover:text-[#A31C1B] text-base transition-transform group-hover:translate-x-1"
            >
              →
            </span>
          </button>
        </h2>

        <div className="flex items-center gap-2.5">
          <DomainCommunityEntryButton
            domainSlug={section.slug}
            domainName={section.name}
            label="Join Domain Chat"
          />
          <span className="text-xs sm:text-sm font-semibold text-white bg-[#06110C] px-2.5 py-0.5 rounded-full border border-[#06110C] shadow-2xs">
            {totalInCat} course{totalInCat === 1 ? "" : "s"}
          </span>
        </div>
      </div>

      {/* Horizontal Course Row with Carousel Navigation */}
      <div className="relative group/row">
        {/* Left scroll arrow (desktop) */}
        {canScrollLeft && (
          <button
            aria-label={`Scroll ${section.name} courses left`}
            className="hidden md:flex absolute -left-4 top-1/2 -translate-y-1/2 z-10 h-10 w-10 items-center justify-center rounded-full border border-neutral-200 bg-white shadow-md text-neutral-700 hover:text-[#A31C1B] hover:border-[#06110C] hover:shadow-lg transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-[#06110C]"
            onClick={() => handleScroll("left")}
            type="button"
          >
            ‹
          </button>
        )}

        {/* Scrollable Container */}
        <div
          aria-label={`${section.name} course list`}
          className="flex gap-6 overflow-x-auto pb-4 pt-1 px-1 -mx-1 scroll-smooth snap-x snap-mandatory scrollbar-none focus:outline-none focus-visible:ring-2 focus-visible:ring-[#A31C1B] rounded-lg"
          ref={rowRef}
          role="region"
          tabIndex={0}
        >
          {displayedCards.map((course) => (
            <div
              className="w-[280px] sm:w-[300px] md:w-[310px] shrink-0 snap-start flex flex-col"
              key={course.id}
            >
              <CourseCard course={course} />
            </div>
          ))}
        </div>

        {/* Right scroll arrow (desktop) */}
        {canScrollRight && (
          <button
            aria-label={`Scroll ${section.name} courses right`}
            className="hidden md:flex absolute -right-4 top-1/2 -translate-y-1/2 z-10 h-10 w-10 items-center justify-center rounded-full border border-neutral-200 bg-white shadow-md text-neutral-700 hover:text-[#A31C1B] hover:border-[#06110C] hover:shadow-lg transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-[#06110C]"
            onClick={() => handleScroll("right")}
            type="button"
          >
            ›
          </button>
        )}
      </div>

      {/* "Show more" button beneath row */}
      {hasMore && (
        <div className="mt-3">
          <button
            aria-label={`Show more courses in ${section.name}`}
            className="inline-flex items-center gap-1.5 rounded-lg border border-[#A31C1B] bg-white px-4 py-2 text-sm font-semibold text-[#A31C1B] transition-colors hover:bg-[#fdf2f2] hover:text-[#881716] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#A31C1B] focus-visible:ring-offset-2"
            onClick={() => onShowMore(section.slug)}
            type="button"
          >
            <span>
              {remainingCount > 0
                ? `Show ${remainingCount} more`
                : "Show more"}
            </span>
            <span aria-hidden="true">→</span>
          </button>
        </div>
      )}
    </section>
  );
}

export function CategorySectionSkeleton() {
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="h-6 w-48 rounded bg-neutral-200 animate-pulse" />
        <div className="h-4 w-16 rounded bg-neutral-200 animate-pulse" />
      </div>
      <div className="flex gap-6 overflow-x-hidden">
        {Array.from({ length: 4 }).map((_, i) => (
          <div
            className="w-[280px] sm:w-[300px] md:w-[310px] shrink-0"
            key={i}
          >
            <div className="flex h-72 animate-pulse flex-col overflow-hidden rounded-xl border border-neutral-200 bg-white">
              <div className="aspect-video w-full bg-neutral-200" />
              <div className="flex flex-1 flex-col p-5 space-y-3">
                <div className="h-3 w-1/4 rounded bg-neutral-200" />
                <div className="h-4 w-3/4 rounded bg-neutral-200" />
                <div className="h-3 w-1/2 rounded bg-neutral-100" />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
