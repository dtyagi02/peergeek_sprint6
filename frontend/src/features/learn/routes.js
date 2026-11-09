import { createElement, lazy, Suspense } from "react";
import { Spinner } from "../../shared/components/Spinner";

const MyLearningPage = lazy(() =>
  import("./MyLearningPage").then((module) => ({
    default: module.MyLearningPage,
  })),
);

const CourseRedirectPage = lazy(() =>
  import("./CourseRedirectPage").then((module) => ({
    default: module.CourseRedirectPage,
  })),
);

const CoursePlayerPage = lazy(() =>
  import("./CoursePlayerPage").then((module) => ({
    default: module.CoursePlayerPage,
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
    path: "/learning",
    element: suspended(MyLearningPage, "Loading your courses"),
    visibility: "protected",
  },
  {
    path: "/learning/courses/:courseId",
    element: suspended(CourseRedirectPage, "Continuing course"),
    visibility: "protected",
  },
  {
    path: "/learning/courses/:courseId/lessons/:lessonId",
    element: suspended(CoursePlayerPage, "Loading lesson player"),
    visibility: "protected",
  },
];
