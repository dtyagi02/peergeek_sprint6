import { createElement, lazy, Suspense } from "react";
import { Spinner } from "../../shared/components/Spinner";

const DoubtSessionRoomPage = lazy(() =>
  import("./DoubtSessionRoomPage").then((module) => ({
    default: module.DoubtSessionRoomPage,
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
    path: "/doubt-sessions/:sessionId",
    element: suspended(DoubtSessionRoomPage, "Connecting to live doubt room..."),
    visibility: "protected",
  },
];
