import { createElement } from "react";
import { HeaderActions } from "./HeaderActions";

export default [
  {
    id: "profile-header-actions",
    name: "header.actions",
    order: 10,
    element: createElement(HeaderActions),
  },
];
