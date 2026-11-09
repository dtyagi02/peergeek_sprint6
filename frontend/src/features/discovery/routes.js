import { createElement, lazy, Suspense } from "react";
import { Spinner } from "../../shared/components/Spinner";

const DiscoverPage = lazy(() =>
  import("./DiscoverPage").then((module) => ({
    default: module.DiscoverPage,
  })),
);

const CoursePreviewPage = lazy(() =>
  import("./CoursePreviewPage").then((module) => ({
    default: module.CoursePreviewPage,
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
    path: "/courses",
    element: suspended(DiscoverPage, "Loading courses"),
    visibility: "public",
  },
  {
    path: "/courses/:courseId",
    element: suspended(CoursePreviewPage, "Loading course preview"),
    visibility: "public",
  },
];
