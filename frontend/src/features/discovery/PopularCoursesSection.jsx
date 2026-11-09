import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { CourseCard, CourseCardSkeleton } from "./CourseCard";
import { getPopularCourses } from "./discoveryApi";

export function PopularCoursesSection() {
  const popularQuery = useQuery({
    queryKey: ["public-popular-courses"],
    queryFn: getPopularCourses,
    staleTime: 2 * 60 * 1000,
    retry: false,
  });

  // Hides itself on error
  if (popularQuery.isError) {
    return null;
  }

  // Skeletons during initial loading
  if (popularQuery.isLoading) {
    return (
      <section aria-labelledby="popular-courses-title" className="py-8">
        <div className="flex items-center justify-between mb-6">
          <div className="h-6 w-48 rounded bg-slate-200 animate-pulse" />
        </div>
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <CourseCardSkeleton key={i} />
          ))}
        </div>
      </section>
    );
  }

  const courses = popularQuery.data;

  // Hides itself when empty or invalid
  if (!Array.isArray(courses) || courses.length === 0) {
    return null;
  }

  // Display top courses (e.g. up to 4 on homepage grid)
  const displayCourses = courses.slice(0, 4);

  return (
    <section aria-labelledby="popular-courses-title" className="py-8">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2
            className="text-2xl font-bold tracking-tight text-[#171717]"
            id="popular-courses-title"
          >
            Popular Courses
          </h2>
          <p className="mt-1 text-sm text-neutral-600">
            Top-rated and most enrolled courses across the community.
          </p>
        </div>
        <Link
          className="text-sm font-semibold text-[#A31C1B] hover:text-[#881716] focus:outline-none focus-visible:underline"
          to="/courses"
        >
          View all courses →
        </Link>
      </div>

      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
        {displayCourses.map((course) => (
          <CourseCard course={course} key={course.id} />
        ))}
      </div>
    </section>
  );
}
