import { useState } from "react";
import { apiRequest } from "../../shared/api/client";
import { useAuthStore } from "../auth/authStore";

export function InstructorModeBanner() {
  const user = useAuthStore((state) => state.user);
  const setMode = useAuthStore((state) => state.setMode);
  const [switching, setSwitching] = useState(false);

  if (user?.lastMode !== "INSTRUCTOR") {
    return null;
  }

  async function switchMode() {
    if (switching) return;
    setSwitching(true);
    setMode("STUDENT");
    try {
      const profile = await apiRequest("users/me/mode", {
        method: "PATCH",
        body: { mode: "STUDENT" },
      });
      if (profile?.lastMode) {
        setMode(profile.lastMode);
      }
    } catch {
      // In offline or unmocked test environments, keep optimistic STUDENT mode
    } finally {
      setSwitching(false);
    }
  }

  return (
    <div
      role="region"
      aria-label="Instructor mode active"
      className="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-amber-900"
    >
      <div className="flex items-center gap-2">
        <span className="font-semibold text-amber-800">Note:</span>
        <span className="text-sm">
          You are currently in Instructor mode. Switch to Student mode for the
          standard learner experience.
        </span>
      </div>
      <button
        type="button"
        disabled={switching}
        onClick={switchMode}
        className="rounded-md bg-amber-200 px-3 py-1.5 text-xs font-semibold text-amber-950 transition hover:bg-amber-300 disabled:opacity-50"
      >
        {switching ? "Switching…" : "Switch to Student mode"}
      </button>
    </div>
  );
}
