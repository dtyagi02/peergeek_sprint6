import { createElement, lazy, Suspense } from "react";
import { Spinner } from "../../shared/components/Spinner";

const InstructorAnalyticsPage = lazy(() =>
  import("./InstructorAnalyticsPage").then((module) => ({
    default: module.InstructorAnalyticsPage,
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
    path: "/instructor/courses/:courseId/analytics",
    element: suspended(InstructorAnalyticsPage, "Loading analytics dashboard"),
    visibility: "protected",
  },
];
