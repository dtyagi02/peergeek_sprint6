import { createElement } from "react";
import { CourseThumbnailManager } from "./CourseThumbnailManager";
import { InstructorResourcePanel } from "./InstructorResourcePanel";

export default [
  {
    id: "instructor-resource-panel",
    name: "lesson-editor.side-panel",
    order: 10,
    element: createElement(InstructorResourcePanel),
  },
  {
    id: "course-thumbnail-manager",
    name: "course-details.thumbnail",
    order: 10,
    element: createElement(CourseThumbnailManager),
  },
];
