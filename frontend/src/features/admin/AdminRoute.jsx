import { Navigate, useLocation } from "react-router-dom";
import { useAuthStore } from "../auth/authStore";
import { Spinner } from "../../shared/components/Spinner";

export function AdminRoute({ children }) {
  const status = useAuthStore((state) => state.status);
  const user = useAuthStore((state) => state.user);
  const location = useLocation();

  if (status === "unknown") {
    return <Spinner label="Restoring your session" />;
  }
  if (status === "anonymous") {
    return <Navigate replace state={{ from: location }} to="/login" />;
  }
  if (user?.role !== "ADMIN") {
    return <Navigate replace to="/" />;
  }
  return children;
}
