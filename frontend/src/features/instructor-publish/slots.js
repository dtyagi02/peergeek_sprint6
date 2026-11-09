import { createElement } from "react";
import { PublishReadinessPanel } from "./PublishReadinessPanel";

export default [
  {
    id: "instructor-publish-readiness",
    name: "course-editor.header-actions",
    order: 10,
    element: createElement(PublishReadinessPanel),
  },
];
