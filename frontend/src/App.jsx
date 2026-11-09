import { BrowserRouter, Route, Routes } from "react-router-dom";
import { AppProviders } from "./app/providers/AppProviders";
import { AppShell } from "./app/shell/AppShell";
import { ErrorBoundary } from "./app/shell/ErrorBoundary";
import { HomePage } from "./app/shell/HomePage";
import { NotFoundPage } from "./app/shell/NotFoundPage";
import { protectedRoutes, publicRoutes } from "./app/registry/routes";
import { ProtectedRoute } from "./app/ProtectedRoute";

function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<HomePage />} />
        {publicRoutes.map((route) => (
          <Route key={route.path} path={route.path} element={route.element} />
        ))}
        {protectedRoutes.map((route) => (
          <Route
            key={route.path}
            path={route.path}
            element={<ProtectedRoute>{route.element}</ProtectedRoute>}
          />
        ))}
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export default function App() {
  return (
    <AppProviders>
      <BrowserRouter>
        <ErrorBoundary>
          <AppRoutes />
        </ErrorBoundary>
      </BrowserRouter>
    </AppProviders>
  );
}
