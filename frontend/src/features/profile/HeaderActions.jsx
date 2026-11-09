import { useRef, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { apiRequest } from "../../shared/api/client";
import { Button } from "../../shared/components/Button";
import { useToast } from "../../shared/components/useToast";
import { useAuthStore } from "../auth/authStore";

export function HeaderActions() {
  const user = useAuthStore((state) => state.user);
  const status = useAuthStore((state) => state.status);
  const setMode = useAuthStore((state) => state.setMode);
  const logout = useAuthStore((state) => state.logout);
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  const [switching, setSwitching] = useState(false);
  const [pendingMode, setPendingMode] = useState(null);
  const switchingRef = useRef(false);
  const activeRequestIdRef = useRef(0);

  // The mode switch is visible only when the authenticated user is eligible
  const isEligible = Boolean(user && status === "authenticated");
  if (!isEligible) return null;

  const mode = user.lastMode ?? "STUDENT";

  async function changeMode(nextMode) {
    if (nextMode === mode || switchingRef.current) return;
    const previousMode = mode;
    const currentPath = location.pathname;
    const requestId = ++activeRequestIdRef.current;

    switchingRef.current = true;
    setSwitching(true);
    setPendingMode(nextMode);

    // Optimistically update frontend state
    setMode(nextMode);

    // Context-aware navigation so the user is not stranded on an incompatible view
    let didNavigate = false;
    if (nextMode === "INSTRUCTOR" && currentPath.startsWith("/learning")) {
      navigate("/instructor/courses");
      didNavigate = true;
    } else if (
      nextMode === "STUDENT" &&
      currentPath.startsWith("/instructor")
    ) {
      navigate("/learning");
      didNavigate = true;
    }

    try {
      const profile = await apiRequest("users/me/mode", {
        method: "PATCH",
        body: { mode: nextMode },
      });

      if (requestId === activeRequestIdRef.current) {
        setMode(profile?.lastMode ?? nextMode);
      }
    } catch {
      if (requestId === activeRequestIdRef.current) {
        setMode(previousMode);
        if (didNavigate) {
          navigate(currentPath);
        }
        toast.notify({
          title: "Mode not changed",
          description: "Your previous mode is still active.",
        });
      }
    } finally {
      if (requestId === activeRequestIdRef.current) {
        switchingRef.current = false;
        setSwitching(false);
        setPendingMode(null);
      }
    }
  }

  function handleKeyDown(event) {
    if (switchingRef.current) return;
    if (event.key === "ArrowRight" || event.key === "ArrowLeft") {
      event.preventDefault();
      const targetMode = mode === "STUDENT" ? "INSTRUCTOR" : "STUDENT";
      void changeMode(targetMode);
    }
  }

  async function signOut() {
    try {
      await logout();
    } catch {
      toast.notify({
        title: "Signed out on this device",
        description: "The server could not confirm the sign-out request.",
      });
    }
    navigate("/", { replace: true });
  }

  return (
    <div className="flex flex-wrap items-center gap-3">
      {/* Unified Segmented Mode Switch Control */}
      <div
        aria-busy={switching}
        aria-label="Interface mode"
        className="relative inline-flex items-center rounded-lg border border-neutral-700 bg-neutral-800/80 p-0.5 text-xs font-medium shadow-xs"
        role="group"
      >
        <span aria-live="polite" className="sr-only">
          {switching
            ? `Switching to ${pendingMode === "INSTRUCTOR" ? "Instructor" : "Student"} mode…`
            : `${mode === "INSTRUCTOR" ? "Instructor mode" : "Student mode"} active`}
        </span>

        <button
          aria-label="Student mode"
          aria-pressed={mode === "STUDENT"}
          className={`relative inline-flex min-h-8 items-center justify-center gap-1.5 rounded-md px-3 py-1 font-medium transition-all focus-visible:outline-2 focus-visible:outline-[#A31C1B] disabled:cursor-not-allowed ${
            mode === "STUDENT"
              ? "bg-white font-semibold text-neutral-900 shadow-xs"
              : "text-neutral-300 hover:bg-neutral-700/60 hover:text-white"
          }`}
          disabled={switching}
          onClick={() => changeMode("STUDENT")}
          onKeyDown={handleKeyDown}
          type="button"
        >
          {switching && pendingMode === "STUDENT" && (
            <svg
              aria-hidden="true"
              className="h-3 w-3 animate-spin text-[#A31C1B]"
              fill="none"
              viewBox="0 0 24 24"
            >
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
              />
              <path
                className="opacity-75"
                d="M4 12a8 8 0 018-8v8H4z"
                fill="currentColor"
              />
            </svg>
          )}
          <span>Student mode</span>
        </button>

        <button
          aria-label="Instructor mode"
          aria-pressed={mode === "INSTRUCTOR"}
          className={`relative inline-flex min-h-8 items-center justify-center gap-1.5 rounded-md px-3 py-1 font-medium transition-all focus-visible:outline-2 focus-visible:outline-[#A31C1B] disabled:cursor-not-allowed ${
            mode === "INSTRUCTOR"
              ? "bg-white font-semibold text-neutral-900 shadow-xs"
              : "text-neutral-300 hover:bg-neutral-700/60 hover:text-white"
          }`}
          disabled={switching}
          onClick={() => changeMode("INSTRUCTOR")}
          onKeyDown={handleKeyDown}
          type="button"
        >
          {switching && pendingMode === "INSTRUCTOR" && (
            <svg
              aria-hidden="true"
              className="h-3 w-3 animate-spin text-[#A31C1B]"
              fill="none"
              viewBox="0 0 24 24"
            >
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
              />
              <path
                className="opacity-75"
                d="M4 12a8 8 0 018-8v8H4z"
                fill="currentColor"
              />
            </svg>
          )}
          <span>Instructor mode</span>
        </button>
      </div>

      <details className="relative">
        <summary className="cursor-pointer rounded px-2 py-1 text-sm font-medium text-white hover:text-neutral-200 focus:outline-2 focus:outline-[#A31C1B]">
          {user.displayName}
          {user.username && (
            <span className="ml-1 text-xs font-normal text-neutral-400">
              (@{user.username})
            </span>
          )}
        </summary>
        <div className="absolute right-0 z-20 mt-2 grid min-w-36 gap-2 rounded-md border border-neutral-700 bg-[#171717] p-3 shadow-xl">
          <Link className="text-sm text-neutral-200 hover:text-white hover:underline" to="/profile">
            Profile
          </Link>
          <Button onClick={signOut} variant="secondary">
            Log out
          </Button>
        </div>
      </details>
    </div>
  );
}
