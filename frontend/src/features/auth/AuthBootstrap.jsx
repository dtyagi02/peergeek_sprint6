import { useEffect } from "react";
import { useAuthStore } from "./authStore";
import { Spinner } from "../../shared/components/Spinner";

export function AuthBootstrap({ children }) {
  const status = useAuthStore((state) => state.status);
  const restoreSession = useAuthStore((state) => state.restoreSession);

  useEffect(() => {
    void restoreSession();
  }, [restoreSession]);

  if (status === "unknown") {
    return (
      <main
        aria-busy="true"
        aria-live="polite"
        className="grid min-h-screen place-items-center"
      >
        <Spinner label="Restoring your session" />
      </main>
    );
  }
  return children;
}
