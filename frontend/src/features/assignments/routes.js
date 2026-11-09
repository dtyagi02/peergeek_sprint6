import { createElement, lazy, Suspense } from "react";
import { Spinner } from "../../shared/components/Spinner";

const InstructorAssignmentsListPage = lazy(() =>
  import("./InstructorAssignmentsListPage").then((module) => ({
    default: module.InstructorAssignmentsListPage,
  })),
);

const AssignmentBuilderPage = lazy(() =>
  import("./AssignmentBuilderPage").then((module) => ({
    default: module.AssignmentBuilderPage,
  })),
);

const SubmissionsQueuePage = lazy(() =>
  import("./SubmissionsQueuePage").then((module) => ({
    default: module.SubmissionsQueuePage,
  })),
);

const ManualGradingPage = lazy(() =>
  import("./ManualGradingPage").then((module) => ({
    default: module.ManualGradingPage,
  })),
);

const MyAssignmentsPage = lazy(() =>
  import("./MyAssignmentsPage").then((module) => ({
    default: module.MyAssignmentsPage,
  })),
);

const CourseAssignmentsPage = lazy(() =>
  import("./CourseAssignmentsPage").then((module) => ({
    default: module.CourseAssignmentsPage,
  })),
);

const TakeAssignmentPage = lazy(() =>
  import("./TakeAssignmentPage").then((module) => ({
    default: module.TakeAssignmentPage,
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
  // Instructor routes
  {
    path: "/instructor/courses/:courseId/assignments",
    element: suspended(InstructorAssignmentsListPage, "Loading assignments"),
    visibility: "protected",
  },
  {
    path: "/instructor/courses/:courseId/assignments/:assignmentId",
    element: suspended(AssignmentBuilderPage, "Loading assignment builder"),
    visibility: "protected",
  },
  {
    path: "/instructor/courses/:courseId/submissions",
    element: suspended(SubmissionsQueuePage, "Loading submissions queue"),
    visibility: "protected",
  },
  {
    path: "/instructor/submissions/:submissionId",
    element: suspended(ManualGradingPage, "Loading grading screen"),
    visibility: "protected",
  },

  // Learner routes
  {
    path: "/learning/assignments",
    element: suspended(MyAssignmentsPage, "Loading your assignments"),
    visibility: "protected",
  },
  {
    path: "/learning/courses/:courseId/assignments",
    element: suspended(CourseAssignmentsPage, "Loading course assignments"),
    visibility: "protected",
  },
  {
    path: "/learning/assignments/:assignmentId",
    element: suspended(TakeAssignmentPage, "Loading assignment"),
    visibility: "protected",
  },
];
