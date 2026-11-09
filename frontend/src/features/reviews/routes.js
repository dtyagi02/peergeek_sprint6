import { createElement, lazy, Suspense } from "react";
import { Spinner } from "../../shared/components/Spinner";

const CourseReviewsPage = lazy(() =>
  import("./CourseReviewsPage").then((module) => ({
    default: module.CourseReviewsPage,
  })),
);

function suspended(component, label) {
  return createElement(
    Suspense,
    { fallback: createElement(Spinner, { label }) },
    createElement(component),
  );
}

export default [
  {
    path: "/courses/:courseId/reviews",
    element: suspended(CourseReviewsPage, "Loading course reviews"),
    visibility: "public",
  },
];
