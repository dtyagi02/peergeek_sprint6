import { createElement, lazy, Suspense } from "react";
import { Spinner } from "../../shared/components/Spinner";
import { AdminRoute } from "./AdminRoute";

const AdminDashboardPage = lazy(() =>
  import("./AdminDashboardPage").then((module) => ({
    default: module.AdminDashboardPage,
  })),
);

export default [
  {
    path: "/admin",
    element: createElement(
      AdminRoute,
      null,
      createElement(
        Suspense,
        {
          fallback: createElement(Spinner, {
            label: "Loading admin dashboard",
          }),
        },
        createElement(AdminDashboardPage),
      ),
    ),
    visibility: "protected",
  },
];
