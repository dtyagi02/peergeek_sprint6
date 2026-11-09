import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { useAuthStore } from "../auth/authStore";
import { getWalletBalance } from "../wallet/walletApi";
import {
  getCourseAvailability,
  requestSession,
  cancelSession,
  getActiveStudentSession,
  updateInstructorCourseSettings,
} from "./doubtSessionApi";

const DURATION_OPTIONS = [5, 10, 15, 30];

export function CourseDoubtSessionCard({ courseId, courseTitle, isEnrolled }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = !!user;

  const [selectedDuration, setSelectedDuration] = useState(15);
  const [requestError, setRequestError] = useState(null);
  const [pendingSession, setPendingSession] = useState(null);
  const [secondsRemaining, setSecondsRemaining] = useState(60);

  // 1. Fetch course doubt session availability
  const availabilityQuery = useQuery({
    queryKey: ["course-doubt-availability", courseId],
    queryFn: () => getCourseAvailability(courseId),
    refetchInterval: 5000,
  });

  // 2. Fetch user's wallet balance
  const walletQuery = useQuery({
    queryKey: ["wallet-balance"],
    queryFn: getWalletBalance,
    enabled: isAuthenticated,
    refetchInterval: 10000,
  });

  // 3. Fetch active student session
  const activeSessionQuery = useQuery({
    queryKey: ["active-student-doubt-session"],
    queryFn: getActiveStudentSession,
    enabled: isAuthenticated,
    refetchInterval: 4000,
  });

  const availability = availabilityQuery.data;
  const wallet = walletQuery.data;
  const activeSession = activeSessionQuery.data;

  // Sync activeSession to pendingSession if in REQUESTED state
  useEffect(() => {
    if (activeSession) {
      if (activeSession.status === "REQUESTED") {
        setPendingSession(activeSession);
        if (activeSession.expiresAt) {
          const expiresMs = new Date(activeSession.expiresAt).getTime();
          const rem = Math.max(0, Math.floor((expiresMs - Date.now()) / 1000));
          setSecondsRemaining(rem);
        }
      } else if (
        activeSession.status === "ACCEPTED" ||
        activeSession.status === "IN_PROGRESS"
      ) {
        setPendingSession(null);
        navigate(`/doubt-sessions/${activeSession.sessionUid || activeSession.id}`);
      }
    } else {
      if (pendingSession && pendingSession.status === "REQUESTED") {
        setPendingSession(null);
      }
    }
  }, [activeSession, navigate]);

  // Request countdown timer
  useEffect(() => {
    if (!pendingSession) return;
    const interval = setInterval(() => {
      setSecondsRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          setPendingSession(null);
          queryClient.invalidateQueries({ queryKey: ["active-student-doubt-session"] });
          queryClient.invalidateQueries({ queryKey: ["wallet-balance"] });
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [pendingSession, queryClient]);

  // Create request mutation
  const requestMutation = useMutation({
    mutationFn: () => requestSession(courseId, selectedDuration),
    onSuccess: (data) => {
      setRequestError(null);
      setPendingSession(data);
      setSecondsRemaining(60);
      queryClient.invalidateQueries({ queryKey: ["wallet-balance"] });
      queryClient.invalidateQueries({ queryKey: ["active-student-doubt-session"] });
    },
    onError: (err) => {
      setRequestError(err?.message || "Failed to request session. Please try again.");
    },
  });

  // Cancel request mutation
  const cancelMutation = useMutation({
    mutationFn: (sessionId) => cancelSession(sessionId),
    onSuccess: () => {
      setPendingSession(null);
      queryClient.invalidateQueries({ queryKey: ["wallet-balance"] });
      queryClient.invalidateQueries({ queryKey: ["active-student-doubt-session"] });
    },
  });

  // Instructor quick toggle mutation
  const toggleLiveMutation = useMutation({
    mutationFn: ({ enabled, available }) =>
      updateInstructorCourseSettings(courseId, { enabled, available }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["course-doubt-availability", courseId] });
      queryClient.invalidateQueries({ queryKey: ["instructor-doubt-settings", courseId] });
    },
  });

  if (availabilityQuery.isLoading) {
    return (
      <div className="mt-6 flex items-center justify-center rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
        <Spinner size="sm" label="Checking doubt clearing availability..." />
      </div>
    );
  }

  const isOwner = availability?.isOwner || (user && availability && user.id === availability.instructorId);
  const isEnabled = availability?.enabled || false;
  const isInstructorOnline = availability?.available || false;
  const isInstructorBusy = availability?.instructorHasActiveSession || false;
  const hourlyRate = availability?.hourlyRatePgc || 600;
  const maxCost = Math.ceil((selectedDuration / 60) * hourlyRate);
  const availableBalance = wallet ? Number(wallet.balance) : 0;
  const hasEnoughFunds = availableBalance >= maxCost;
  const isEnrolledEffective = isEnrolled ?? availability?.isEnrolled ?? false;

  // 1. INSTRUCTOR OWNER VIEW
  if (isOwner) {
    return (
      <div
        data-testid="course-doubt-session-card"
        className="mt-6 overflow-hidden rounded-2xl border border-indigo-200 bg-gradient-to-br from-indigo-50/90 via-white to-purple-50/70 p-6 shadow-sm"
      >
        <div className="flex items-center justify-between border-b border-indigo-100 pb-3">
          <div className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-indigo-600 text-lg text-white">
              📹
            </span>
            <div>
              <h3 className="text-sm font-bold text-slate-900">
                Live Doubts (Instructor View)
              </h3>
              <p className="text-[11px] text-slate-500">
                You own this course
              </p>
            </div>
          </div>
          <div>
            {isEnabled && isInstructorOnline ? (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-semibold text-emerald-800">
                <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
                Live Now
              </span>
            ) : isEnabled && !isInstructorOnline ? (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">
                <span className="h-2 w-2 rounded-full bg-slate-400" />
                Offline
              </span>
            ) : (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-800">
                Disabled
              </span>
            )}
          </div>
        </div>

        <div className="mt-3 text-xs text-slate-600">
          <p>
            Current Rate: <strong className="text-slate-900">🪙 {hourlyRate} PGC / hour</strong>
          </p>
        </div>

        {/* Quick Instructor Actions */}
        <div className="mt-4 space-y-2">
          {!isEnabled ? (
            <Button
              variant="primary"
              disabled={toggleLiveMutation.isPending}
              onClick={() => toggleLiveMutation.mutate({ enabled: true, available: true })}
              className="w-full bg-indigo-600 hover:bg-indigo-700 text-xs py-2 shadow-xs"
            >
              {toggleLiveMutation.isPending ? "Enabling..." : "Enable & Go Live Now 🟢"}
            </Button>
          ) : !isInstructorOnline ? (
            <Button
              variant="primary"
              disabled={toggleLiveMutation.isPending}
              onClick={() => toggleLiveMutation.mutate({ enabled: true, available: true })}
              className="w-full bg-emerald-600 hover:bg-emerald-700 text-xs py-2 shadow-xs"
            >
              {toggleLiveMutation.isPending ? "Switching..." : "Switch to Available (Go Live) 🟢"}
            </Button>
          ) : (
            <Button
              variant="secondary"
              disabled={toggleLiveMutation.isPending}
              onClick={() => toggleLiveMutation.mutate({ enabled: true, available: false })}
              className="w-full text-xs py-2"
            >
              {toggleLiveMutation.isPending ? "Switching..." : "Switch to Offline ⚪"}
            </Button>
          )}

          <Link
            to={`/instructor/courses/${courseId}?tab=doubts`}
            className="block text-center text-xs font-semibold text-indigo-600 hover:text-indigo-800 pt-1"
          >
            Configure Hourly Rate & Settings →
          </Link>
        </div>
      </div>
    );
  }

  // 2. STUDENT VIEW: If instructor has NOT enabled doubt clearing for this course
  if (!isEnabled) {
    return (
      <div
        data-testid="course-doubt-session-card"
        className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs"
      >
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-xl text-slate-500">
            💬
          </div>
          <div>
            <h3 className="text-sm font-bold text-slate-900">
              1-to-1 Live Doubt Clearing
            </h3>
            <span className="inline-block mt-0.5 rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-600">
              Not Currently Active
            </span>
          </div>
        </div>
        <p className="mt-3 text-xs leading-relaxed text-slate-500">
          Live 1-to-1 doubt clearing is not currently enabled by the instructor for this course.
        </p>
      </div>
    );
  }

  // 3. STUDENT VIEW: Doubt clearing is ENABLED
  return (
    <div
      data-testid="course-doubt-session-card"
      className="mt-6 overflow-hidden rounded-2xl border border-indigo-100 bg-gradient-to-br from-indigo-50/70 via-white to-purple-50/50 p-6 shadow-sm transition-all"
    >
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-indigo-100/70 pb-4">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-600 text-xl text-white shadow-xs">
            💬
          </div>
          <div>
            <h3 className="text-base font-bold text-slate-900">
              1-to-1 Live Doubt Clearing
            </h3>
            <p className="text-xs text-slate-500">
              With {availability?.instructorDisplayName || "Course Instructor"}
            </p>
          </div>
        </div>

        {/* Status Badge */}
        <div>
          {isInstructorOnline && !isInstructorBusy ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-100 px-3 py-1 text-xs font-semibold text-emerald-800">
              <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
              Instructor Live Now
            </span>
          ) : isInstructorOnline && isInstructorBusy ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-800">
              <span className="h-2 w-2 rounded-full bg-amber-500" />
              In Call (Busy)
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600">
              <span className="h-2 w-2 rounded-full bg-slate-400" />
              Instructor Offline
            </span>
          )}
        </div>
      </div>

      {/* Pricing and Details */}
      <div className="mt-4 flex items-center justify-between text-sm">
        <span className="text-slate-600">Instructor Rate:</span>
        <span className="font-bold text-slate-900">
          🪙 {hourlyRate} PGC <span className="font-normal text-slate-500">/ hour</span>
        </span>
      </div>

      {/* Duration Selection */}
      <div className="mt-4">
        <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500">
          Select Max Duration
        </label>
        <div className="mt-2 grid grid-cols-4 gap-2">
          {DURATION_OPTIONS.map((mins) => {
            const isSelected = selectedDuration === mins;
            const optionCost = Math.ceil((mins / 60) * hourlyRate);
            return (
              <button
                key={mins}
                type="button"
                disabled={!!pendingSession}
                onClick={() => setSelectedDuration(mins)}
                className={`flex flex-col items-center justify-center rounded-xl border p-2.5 text-center transition-all ${
                  isSelected
                    ? "border-indigo-600 bg-indigo-600 text-white shadow-xs"
                    : "border-slate-200 bg-white text-slate-700 hover:border-indigo-300 hover:bg-indigo-50/50"
                } disabled:cursor-not-allowed disabled:opacity-50`}
              >
                <span className="text-sm font-bold">{mins}m</span>
                <span className={`text-[10px] ${isSelected ? "text-indigo-100" : "text-slate-500"}`}>
                  🪙 {optionCost}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Cost & Refund Explanation */}
      <div className="mt-4 rounded-xl bg-white/80 p-3 text-xs text-slate-600 border border-slate-200/60">
        <div className="flex items-center justify-between">
          <span>Maximum Reserved:</span>
          <span className="font-bold text-indigo-700">🪙 {maxCost} PGC</span>
        </div>
        <p className="mt-1 text-[11px] text-slate-500">
          Only charged for actual call time (rounded up to nearest whole minute). All unused GeekCoins are refunded immediately upon call end.
        </p>
      </div>

      {/* Error Message */}
      {requestError && (
        <div className="mt-3 rounded-lg bg-red-50 p-2.5 text-xs text-red-700 border border-red-200">
          {requestError}
        </div>
      )}

      {/* Action / State Area */}
      <div className="mt-5">
        {!isAuthenticated ? (
          <Link to="/login" className="block">
            <Button variant="primary" className="w-full">
              Log In to Request Doubt Session
            </Button>
          </Link>
        ) : !isEnrolledEffective ? (
          <div className="rounded-xl bg-amber-50 p-3 text-center text-xs font-medium text-amber-800 border border-amber-200">
            You must be actively enrolled in this course to request live doubt sessions.
          </div>
        ) : pendingSession ? (
          /* Active Pending Request State */
          <div className="rounded-xl border border-indigo-200 bg-indigo-50/60 p-4 text-center">
            <div className="flex items-center justify-center gap-2 text-sm font-semibold text-indigo-900">
              <span className="h-2.5 w-2.5 rounded-full bg-indigo-600 animate-ping" />
              Waiting for instructor to accept...
            </div>
            <div className="mt-2 text-2xl font-black text-indigo-700">
              {secondsRemaining}s
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Reserved 🪙 {maxCost} PGC. If the instructor does not respond within 60s, coins are automatically released.
            </p>
            <div className="mt-3">
              <Button
                variant="secondary"
                disabled={cancelMutation.isPending}
                onClick={() => cancelMutation.mutate(pendingSession.sessionUid || pendingSession.id)}
                className="w-full text-xs"
              >
                {cancelMutation.isPending ? "Cancelling..." : "Cancel Request"}
              </Button>
            </div>
          </div>
        ) : activeSession && (activeSession.status === "ACCEPTED" || activeSession.status === "IN_PROGRESS") ? (
          /* Ongoing active session */
          <Link
            to={`/doubt-sessions/${activeSession.sessionUid || activeSession.id}`}
            className="block"
          >
            <Button variant="primary" className="w-full bg-emerald-600 hover:bg-emerald-700">
              Return to Live Session Room 📹
            </Button>
          </Link>
        ) : !isInstructorOnline ? (
          <Button disabled className="w-full">
            Instructor Currently Offline
          </Button>
        ) : isInstructorBusy ? (
          <Button disabled className="w-full">
            Instructor in Another Call
          </Button>
        ) : !hasEnoughFunds ? (
          <div className="space-y-2">
            <Button disabled className="w-full">
              Insufficient GeekCoins (Need 🪙 {maxCost} PGC)
            </Button>
            <p className="text-center text-xs text-slate-500">
              Your balance: <span className="font-semibold text-slate-800">🪙 {availableBalance.toFixed(2)} PGC</span>
            </p>
          </div>
        ) : (
          <Button
            variant="primary"
            disabled={requestMutation.isPending}
            onClick={() => requestMutation.mutate()}
            className="w-full shadow-md shadow-indigo-600/20"
          >
            {requestMutation.isPending ? (
              <span className="flex items-center gap-2">
                <Spinner size="sm" /> Requesting...
              </span>
            ) : (
              `Request Instant Session (${selectedDuration} min - 🪙 ${maxCost} PGC)`
            )}
          </Button>
        )}
      </div>
    </div>
  );
}
