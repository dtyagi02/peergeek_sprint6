import { Navigate, useLocation } from "react-router-dom";
import { useAuthStore } from "../features/auth/authStore";
import { Spinner } from "../shared/components/Spinner";

export function ProtectedRoute({ children }) {
  const status = useAuthStore((state) => state.status);
  const location = useLocation();

  if (status === "unknown") {
    return <Spinner label="Restoring your session" />;
  }
  if (status === "anonymous") {
    return <Navigate replace state={{ from: location }} to="/login" />;
  }
  return children;
}
