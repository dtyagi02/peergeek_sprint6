import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { useAuthStore } from "../../features/auth/authStore";
import { FeaturedLearningSection } from "../../features/discovery/FeaturedLearningSection";
import {
  CategorySection,
  CategorySectionSkeleton,
} from "../../features/discovery/CategorySection";
import {
  getPublicCategories,
  listPublishedCourses,
} from "../../features/discovery/discoveryApi";
import { Slot } from "../registry/slots";

const EXPLORE_ROLES = [
  { id: "fullstack", title: "Full-Stack Developer", color: "#A31C1B" },
  { id: "ml", title: "Machine Learning Engineer", color: "#06110C" },
  { id: "data", title: "Data Scientist", color: "#A31C1B" },
  { id: "cloud", title: "Cloud Architect", color: "#171717" },
  { id: "devops", title: "DevOps Specialist", color: "#06110C" },
  { id: "uiux", title: "UI/UX Designer", color: "#A31C1B" },
  { id: "database", title: "Database Administrator", color: "#171717" },
  { id: "cyber", title: "Cybersecurity Analyst", color: "#06110C" },
];

export function HomePage() {
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);
  const displayName = user?.displayName || user?.username;

  const [dismissRoleBanner, setDismissRoleBanner] = useState(false);

  // Fetch categories
  const categoriesQuery = useQuery({
    queryKey: ["public-categories"],
    queryFn: getPublicCategories,
    staleTime: 5 * 60 * 1000,
  });

  // Fetch published courses for category rows
  const coursesQuery = useQuery({
    queryKey: [
      "public-courses",
      { page: 0, size: 50, sort: "popularity,desc" },
    ],
    queryFn: () =>
      listPublishedCourses({
        page: 0,
        size: 50,
        sort: "popularity,desc",
      }),
    staleTime: 60 * 1000,
  });

  // Group published courses by their respective categories
  const categorySections = useMemo(() => {
    const courses = Array.isArray(coursesQuery.data?.content)
      ? coursesQuery.data.content
      : [];
    if (courses.length === 0) return [];

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
  }, [categoriesQuery.data, coursesQuery.data]);

  return (
    <div className="space-y-8 sm:space-y-12 pb-16">
      {/* 1. Welcome Section */}
      <section aria-labelledby="welcome-heading" className="pt-2">
        {displayName ? (
          <div>
            <div className="flex flex-wrap items-center gap-3">
              <h1
                className="text-2xl sm:text-3xl lg:text-4xl font-extrabold tracking-tight text-[#171717]"
                id="welcome-heading"
              >
                Welcome,{" "}
                <span className="text-[#A31C1B]">{displayName}</span>
              </h1>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-[#06110C] px-3 py-1 text-xs font-semibold text-white border border-[#06110C] shadow-2xs">
                <span
                  aria-hidden="true"
                  className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse"
                />
                <span>Community Learner</span>
              </span>
            </div>
            <p className="mt-1.5 text-sm sm:text-base text-neutral-600">
              Pick up where you left off or explore new learning paths from the
              community.
            </p>
          </div>
        ) : (
          <div>
            <h1
              className="text-2xl sm:text-3xl lg:text-4xl font-extrabold tracking-tight text-[#171717]"
              id="welcome-heading"
            >
              Learn together.{" "}
              <span className="text-[#A31C1B]">Grow together.</span>
            </h1>
            <p className="mt-1.5 text-sm sm:text-base text-neutral-600">
              PeerGeek is the foundation for a community where learners build
              practical skills and confidence.
            </p>
          </div>
        )}
      </section>

      {/* 2. Role Interests Recommendation Chips (Reference Image 1) */}
      {!dismissRoleBanner && (
        <section
          aria-label="Role recommendations"
          className="rounded-2xl border border-neutral-200 bg-gradient-to-r from-neutral-50 via-white to-neutral-50 p-4 sm:p-5 shadow-xs transition-all"
        >
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <h2 className="text-xs sm:text-sm font-bold text-[#171717]">
                Which role(s) are you interested in?
              </h2>
              <span className="rounded-full bg-[#06110C] px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-white">
                Curated
              </span>
            </div>
            <button
              aria-label="Dismiss role recommendations"
              className="rounded-full p-1 text-neutral-400 hover:text-neutral-700 hover:bg-neutral-100 transition-colors focus:outline-none focus:ring-2 focus:ring-[#A31C1B]"
              onClick={() => setDismissRoleBanner(true)}
              type="button"
            >
              <span aria-hidden="true" className="text-sm font-bold">
                ✕
              </span>
            </button>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {EXPLORE_ROLES.map((role) => (
              <button
                className="inline-flex items-center gap-2 rounded-full border border-neutral-300 bg-white px-3 py-1.5 text-xs font-semibold text-neutral-800 shadow-2xs hover:border-[#A31C1B] hover:text-[#A31C1B] transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-[#A31C1B]"
                key={role.id}
                onClick={() =>
                  navigate(`/courses?q=${encodeURIComponent(role.title)}`)
                }
                type="button"
              >
                <span
                  aria-hidden="true"
                  className="h-2 w-2 rounded-full"
                  style={{ backgroundColor: role.color }}
                />
                <span>{role.title}</span>
              </button>
            ))}

            <Link
              className="inline-flex items-center gap-1 text-xs font-semibold text-[#A31C1B] hover:text-[#881716] px-2 py-1 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-[#A31C1B]"
              to="/courses"
            >
              <span>+ View more</span>
            </Link>
          </div>
        </section>
      )}

      {/* 3. Featured Learning Section (Reference Image 1: Forest Green & Charcoal Black) */}
      <FeaturedLearningSection />

      {/* 4. Category-Based Course Discovery (Reference Image 2) */}
      <div className="space-y-12 sm:space-y-16">
        {coursesQuery.isLoading && (
          <div className="space-y-12">
            <CategorySectionSkeleton />
            <CategorySectionSkeleton />
          </div>
        )}

        {!coursesQuery.isLoading &&
          categorySections.map((section) => (
            <CategorySection
              key={section.id || section.slug}
              onCategoryClick={(slug) => navigate(`/courses?category=${slug}`)}
              onShowMore={(slug) => navigate(`/courses?category=${slug}`)}
              section={section}
            />
          ))}
      </div>

      {/* 5. Slot for plugin / extension contributions */}
      <Slot name="home.sections" />
    </div>
  );
}
