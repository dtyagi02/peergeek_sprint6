import { createElement } from "react";
import { ProfilePage } from "./ProfilePage";
import { InstructorStudentDetailPage } from "./InstructorStudentDetailPage";

export default [
  {
    path: "/profile",
    element: createElement(ProfilePage),
    visibility: "protected",
  },
  {
    path: "/profile/:username",
    element: createElement(ProfilePage),
    visibility: "public",
  },
  {
    path: "/instructor/courses/:courseId/students/:studentId",
    element: createElement(InstructorStudentDetailPage),
    visibility: "protected",
  },
];
