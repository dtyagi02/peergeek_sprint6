import { useState } from "react";
import { apiRequest } from "../../shared/api/client";
import { Button } from "../../shared/components/Button";
import { useToast } from "../../shared/components/useToast";
import { useAuthStore } from "../auth/authStore";

export function InstructorModeBanner() {
  const user = useAuthStore((state) => state.user);
  const setMode = useAuthStore((state) => state.setMode);
  const [switching, setSwitching] = useState(false);
  const toast = useToast();

  if (!user || user.lastMode === "INSTRUCTOR") return null;

  async function switchMode() {
    if (switching) return;
    setSwitching(true);
    setMode("INSTRUCTOR");
    try {
      const profile = await apiRequest("users/me/mode", {
        method: "PATCH",
        body: { mode: "INSTRUCTOR" },
      });
      setMode(profile?.lastMode ?? "INSTRUCTOR");
    } catch {
      setMode("STUDENT");
      toast.notify({
        title: "Could not switch modes",
        description: "Your current mode is unchanged.",
      });
    } finally {
      setSwitching(false);
    }
  }

  return (
    <aside
      aria-label="Instructor mode"
      className="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-brand-200 bg-brand-50 p-4 text-sm text-ink-900"
    >
      <p>
        You are viewing instructor tools in Student mode. Mode affects the
        interface only.
      </p>
      <Button disabled={switching} onClick={switchMode} variant="secondary">
        {switching ? "Switching…" : "Switch to Instructor mode"}
      </Button>
    </aside>
  );
}
