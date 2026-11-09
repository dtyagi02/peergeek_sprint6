import { createElement, lazy, Suspense } from "react";
import { Spinner } from "../../shared/components/Spinner";

const CourseDashboardPage = lazy(() =>
  import("./CoursePages").then((module) => ({
    default: module.CourseDashboardPage,
  })),
);
const NewCoursePage = lazy(() =>
  import("./CoursePages").then((module) => ({ default: module.NewCoursePage })),
);
const CourseEditorPage = lazy(() =>
  import("./CoursePages").then((module) => ({
    default: module.CourseEditorPage,
  })),
);
const LessonEditorPage = lazy(() =>
  import("./LessonEditorPage").then((module) => ({
    default: module.LessonEditorPage,
  })),
);
const InstructorPreviewPage = lazy(() =>
  import("../instructor-publish/InstructorPreviewPage").then((module) => ({
    default: module.InstructorPreviewPage,
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
    path: "/instructor/courses",
    element: suspended(CourseDashboardPage, "Loading courses"),
    visibility: "protected",
  },
  {
    path: "/instructor/courses/new",
    element: suspended(NewCoursePage, "Loading course form"),
    visibility: "protected",
  },
  {
    path: "/instructor/courses/:courseId",
    element: suspended(CourseEditorPage, "Loading course editor"),
    visibility: "protected",
  },
  {
    path: "/instructor/courses/:courseId/lessons/:lessonId",
    element: suspended(LessonEditorPage, "Loading lesson editor"),
    visibility: "protected",
  },
  {
    path: "/instructor/courses/:courseId/preview",
    element: suspended(InstructorPreviewPage, "Loading course preview"),
    visibility: "protected",
  },
];
