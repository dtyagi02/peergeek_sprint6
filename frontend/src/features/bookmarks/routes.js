import { createElement } from "react";
import { BookmarksPage } from "./BookmarksPage";

export default [
  {
    path: "/learning/bookmarks",
    element: createElement(BookmarksPage),
    visibility: "protected",
  },
];
