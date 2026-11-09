import { createElement } from "react";
import { NotificationsPage } from "./NotificationsPage";

export default [
  {
    path: "/notifications",
    element: createElement(NotificationsPage),
    visibility: "protected",
  },
];
