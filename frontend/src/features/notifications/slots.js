import { createElement } from "react";
import { NotificationBell } from "./NotificationBell";

export default [
  {
    id: "notifications-bell-action",
    name: "header.actions",
    order: 5,
    element: createElement(NotificationBell),
  },
];
