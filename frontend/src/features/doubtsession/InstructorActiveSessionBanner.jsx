import { useState, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { useAuthStore } from "../auth/authStore";
import {
  getActiveInstructorSession,
  acceptSession,
  rejectSession,
} from "./doubtSessionApi";

export function InstructorActiveSessionBanner() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);

  const isInstructor = user?.lastMode === "INSTRUCTOR";
  const [secondsRemaining, setSecondsRemaining] = useState(60);

  // Poll for active instructor session / incoming request every 3s
  const { data: activeSession } = useQuery({
    queryKey: ["active-instructor-doubt-session"],
    queryFn: getActiveInstructorSession,
    enabled: isInstructor,
    refetchInterval: 3000,
  });

  // Calculate remaining seconds for countdown
  useEffect(() => {
    if (activeSession && activeSession.status === "REQUESTED") {
      if (activeSession.expiresAt) {
        const expiresMs = new Date(activeSession.expiresAt).getTime();
        const rem = Math.max(0, Math.floor((expiresMs - Date.now()) / 1000));
        setSecondsRemaining(rem);
      }
    }
  }, [activeSession]);

  // Countdown timer interval
  useEffect(() => {
    if (!activeSession || activeSession.status !== "REQUESTED") return;
    const interval = setInterval(() => {
      setSecondsRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          queryClient.invalidateQueries({ queryKey: ["active-instructor-doubt-session"] });
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [activeSession, queryClient]);

  // Accept mutation
  const acceptMutation = useMutation({
    mutationFn: (id) => acceptSession(id),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["active-instructor-doubt-session"] });
      navigate(`/doubt-sessions/${data.sessionUid || data.id}`);
    },
  });

  // Reject mutation
  const rejectMutation = useMutation({
    mutationFn: (id) => rejectSession(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["active-instructor-doubt-session"] });
    },
  });

  if (!isInstructor || !activeSession) {
    return null;
  }

  const isAlreadyInRoom = location.pathname.startsWith("/doubt-sessions/");

  // 1. Incoming Request Modal
  if (activeSession.status === "REQUESTED") {
    const sessionId = activeSession.sessionUid || activeSession.id;
    return (
      <div
        data-testid="instructor-incoming-request-modal"
        className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs animate-in fade-in duration-200"
      >
        <div className="w-full max-w-md rounded-2xl border border-indigo-200 bg-white p-6 shadow-2xl">
          <div className="flex items-center gap-3">
            <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-100 text-2xl text-indigo-700 animate-bounce">
              📞
            </span>
            <div>
              <h3 className="text-lg font-bold text-slate-900">
                Incoming Doubt Session Request!
              </h3>
              <p className="text-xs text-slate-500">
                A student is waiting for your live guidance
              </p>
            </div>
          </div>

          <div className="mt-5 space-y-2 rounded-xl bg-slate-50 p-4 text-xs text-slate-700 border border-slate-200/60">
            <div className="flex justify-between">
              <span className="text-slate-500">Student:</span>
              <span className="font-semibold text-slate-900">
                {activeSession.studentDisplayName || "Student"}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Course:</span>
              <span className="font-semibold text-slate-900 truncate max-w-[200px]">
                {activeSession.courseTitle || "Your Course"}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Duration:</span>
              <span className="font-semibold text-slate-900">
                {activeSession.maxDurationMinutes} minutes
              </span>
            </div>
            <div className="flex justify-between border-t border-slate-200/60 pt-2 font-medium">
              <span className="text-slate-600">Potential Earnings:</span>
              <span className="font-bold text-indigo-700">
                🪙 {activeSession.reservedAmount} PGC
              </span>
            </div>
          </div>

          {/* Countdown indicator */}
          <div className="mt-4">
            <div className="flex justify-between text-xs text-slate-500">
              <span>Time to respond:</span>
              <span className="font-bold text-indigo-600">{secondsRemaining}s</span>
            </div>
            <div className="mt-1 h-2 w-full overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full bg-indigo-600 transition-all duration-1000 ease-linear"
                style={{ width: `${(secondsRemaining / 60) * 100}%` }}
              />
            </div>
          </div>

          {/* Action buttons */}
          <div className="mt-6 flex gap-3">
            <Button
              variant="secondary"
              disabled={rejectMutation.isPending || acceptMutation.isPending}
              onClick={() => rejectMutation.mutate(sessionId)}
              className="flex-1 text-slate-600 hover:bg-slate-100"
            >
              {rejectMutation.isPending ? "Declining..." : "Decline"}
            </Button>
            <Button
              variant="primary"
              disabled={acceptMutation.isPending || rejectMutation.isPending}
              onClick={() => acceptMutation.mutate(sessionId)}
              className="flex-1 bg-indigo-600 hover:bg-indigo-700 shadow-md shadow-indigo-600/20"
            >
              {acceptMutation.isPending ? (
                <span className="flex items-center gap-2">
                  <Spinner size="sm" /> Connecting...
                </span>
              ) : (
                "Accept & Join 📹"
              )}
            </Button>
          </div>
        </div>
      </div>
    );
  }

  // 2. Persistent banner if call is in progress and instructor navigated away from the room
  if (
    !isAlreadyInRoom &&
    (activeSession.status === "ACCEPTED" || activeSession.status === "IN_PROGRESS")
  ) {
    const sessionId = activeSession.sessionUid || activeSession.id;
    return (
      <aside
        aria-label="Active doubt session banner"
        data-testid="instructor-active-call-banner"
        className="sticky top-0 z-40 flex items-center justify-between border-b border-emerald-300 bg-emerald-600 px-4 py-2.5 text-white shadow-md"
      >
        <div className="flex items-center gap-2 text-xs sm:text-sm font-medium">
          <span className="h-2.5 w-2.5 rounded-full bg-emerald-200 animate-ping" />
          <span>
            Live doubt session active with{" "}
            <strong>{activeSession.studentDisplayName || "Student"}</strong>
          </span>
        </div>
        <Button
          variant="secondary"
          onClick={() => navigate(`/doubt-sessions/${sessionId}`)}
          className="border-none bg-white py-1 px-3 text-xs font-bold text-emerald-800 shadow-xs hover:bg-emerald-50"
        >
          Return to Call 📹
        </Button>
      </aside>
    );
  }

  return null;
}
