import { useState, useEffect, useRef } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  LiveKitRoom,
  VideoConference,
  RoomAudioRenderer,
  useRoomContext,
  useConnectionState,
} from "@livekit/components-react";
import "@livekit/components-styles";
import { ConnectionState } from "livekit-client";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { useAuthStore } from "../auth/authStore";
import { getSession, getSessionToken, endSession } from "./doubtSessionApi";

function RoomLiveStatus({ session, onEndCall }) {
  const room = useRoomContext();
  const connectionState = useConnectionState();
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  useEffect(() => {
    // Only increment elapsed seconds when connected
    if (connectionState !== ConnectionState.Connected) return;

    const timer = setInterval(() => {
      setElapsedSeconds((prev) => prev + 1);
    }, 1000);

    return () => clearInterval(timer);
  }, [connectionState]);

  const maxSeconds = (session?.maxDurationMinutes || 15) * 60;
  const remainingSeconds = Math.max(0, maxSeconds - elapsedSeconds);
  const billableMinutes = Math.max(1, Math.ceil(elapsedSeconds / 60));
  const hourlyRate = session?.hourlyRatePgc || 600;
  const currentCost = Math.min(
    session?.reservedAmount ? Number(session.reservedAmount) : 9999,
    Math.ceil((billableMinutes * hourlyRate) / 60)
  );

  const formatTime = (secs) => {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
  };

  return (
    <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 bg-slate-900 px-4 py-3 text-white">
      {/* Participant and Course info */}
      <div className="flex items-center gap-3">
        <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-indigo-600 text-lg">
          💬
        </span>
        <div>
          <h2 className="text-sm font-bold truncate max-w-[200px] sm:max-w-xs">
            {session.courseTitle || "Live Doubt Clearing"}
          </h2>
          <div className="flex items-center gap-2 text-xs text-slate-400">
            <span>Student: {session.studentDisplayName}</span>
            <span>•</span>
            <span>Instructor: {session.instructorDisplayName}</span>
          </div>
        </div>
      </div>

      {/* Connection & Timer stats */}
      <div className="flex items-center gap-6 text-xs sm:text-sm">
        {/* Connection status */}
        <div className="flex items-center gap-2">
          {connectionState === ConnectionState.Connected ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-950 px-2.5 py-0.5 text-xs font-semibold text-emerald-400 border border-emerald-800">
              <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
              Connected
            </span>
          ) : connectionState === ConnectionState.Reconnecting ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-950 px-2.5 py-0.5 text-xs font-semibold text-amber-400 border border-amber-800">
              <span className="h-2 w-2 rounded-full bg-amber-500 animate-ping" />
              Reconnecting...
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-800 px-2.5 py-0.5 text-xs font-medium text-slate-400">
              <span className="h-2 w-2 rounded-full bg-slate-500" />
              Connecting...
            </span>
          )}
        </div>

        {/* Live Call Duration */}
        <div className="flex flex-col items-center">
          <span className="text-[10px] uppercase tracking-wider text-slate-400">
            Remaining
          </span>
          <span className="font-mono font-bold text-white">
            {formatTime(remainingSeconds)}
          </span>
        </div>

        {/* Billable Estimate */}
        <div className="hidden sm:flex flex-col items-center">
          <span className="text-[10px] uppercase tracking-wider text-slate-400">
            Current Charge
          </span>
          <span className="font-bold text-indigo-400">
            🪙 {currentCost} PGC ({billableMinutes}m)
          </span>
        </div>

        {/* End Call Button */}
        <Button
          variant="danger"
          onClick={onEndCall}
          className="bg-red-600 hover:bg-red-700 text-xs px-3 py-1.5"
        >
          End Call ⏹
        </Button>
      </div>
    </div>
  );
}

export function DoubtSessionRoomPage() {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);

  const [showEndConfirm, setShowEndConfirm] = useState(false);
  const [completedSession, setCompletedSession] = useState(null);

  // 1. Fetch Session details
  const sessionQuery = useQuery({
    queryKey: ["doubt-session", sessionId],
    queryFn: () => getSession(sessionId),
    refetchInterval: (query) => {
      const data = query.state.data;
      if (data && (data.status === "COMPLETED" || data.status === "EXPIRED" || data.status === "CANCELLED" || data.status === "REJECTED")) {
        return false;
      }
      return 4000;
    },
  });

  // 2. Fetch LiveKit Token
  const tokenQuery = useQuery({
    queryKey: ["doubt-session-token", sessionId],
    queryFn: () => getSessionToken(sessionId),
    enabled: !!sessionQuery.data && (sessionQuery.data.status === "ACCEPTED" || sessionQuery.data.status === "IN_PROGRESS"),
    staleTime: 60 * 1000,
    retry: 2,
  });

  const session = sessionQuery.data;
  const tokenData = tokenQuery.data;

  // Detect session completion from backend polling
  useEffect(() => {
    if (session && (session.status === "COMPLETED" || session.status === "INTERRUPTED" || session.status === "EXPIRED")) {
      setCompletedSession(session);
      queryClient.invalidateQueries({ queryKey: ["wallet-balance"] });
    }
  }, [session, queryClient]);

  // End Session Mutation
  const endMutation = useMutation({
    mutationFn: () => endSession(sessionId),
    onSuccess: (data) => {
      setShowEndConfirm(false);
      setCompletedSession(data);
      queryClient.invalidateQueries({ queryKey: ["doubt-session", sessionId] });
      queryClient.invalidateQueries({ queryKey: ["wallet-balance"] });
    },
  });

  // Loading state
  if (sessionQuery.isLoading || (session && (session.status === "ACCEPTED" || session.status === "IN_PROGRESS") && tokenQuery.isLoading)) {
    return (
      <main className="flex min-h-[70vh] flex-col items-center justify-center bg-slate-950 text-white">
        <Spinner size="lg" label="Joining live doubt session room..." />
        <p className="mt-4 text-sm text-slate-400">
          Connecting to LiveKit Cloud...
        </p>
      </main>
    );
  }

  // Error state
  if (sessionQuery.isError || !session) {
    return (
      <main className="flex min-h-[70vh] items-center justify-center p-4">
        <div className="w-full max-w-md rounded-2xl border border-surface-200 bg-white p-8 text-center shadow-md">
          <h1 className="text-xl font-bold text-slate-900">Session Not Found</h1>
          <p className="mt-2 text-sm text-slate-600">
            This live doubt session does not exist or you do not have permission to join.
          </p>
          <div className="mt-6">
            <Link to="/courses">
              <Button variant="primary">Return to Courses</Button>
            </Link>
          </div>
        </div>
      </main>
    );
  }

  // If session is already completed or ended
  if (completedSession || session.status === "COMPLETED" || session.status === "INTERRUPTED" || session.status === "EXPIRED" || session.status === "CANCELLED" || session.status === "REJECTED") {
    const summary = completedSession || session;
    const isStudent = user?.id === summary.studentId;
    const billedMinutes = Math.max(0, Math.ceil((summary.billableSeconds || 0) / 60));

    return (
      <main className="flex min-h-[80vh] items-center justify-center bg-slate-50 p-4">
        <div className="w-full max-w-lg rounded-3xl border border-slate-200 bg-white p-8 shadow-xl">
          <div className="flex flex-col items-center text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-indigo-100 text-3xl text-indigo-600 shadow-xs">
              🎉
            </div>
            <h1 className="mt-4 text-2xl font-black text-slate-900">
              Session Concluded
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              {summary.courseTitle}
            </p>
          </div>

          <div className="mt-6 space-y-3 rounded-2xl bg-slate-50 p-5 text-sm border border-slate-200/60">
            <div className="flex justify-between">
              <span className="text-slate-500">Status:</span>
              <span className="font-semibold text-slate-900 uppercase">
                {summary.status}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Billable Time:</span>
              <span className="font-semibold text-slate-900">
                {billedMinutes} {billedMinutes === 1 ? "minute" : "minutes"} ({summary.billableSeconds || 0} seconds)
              </span>
            </div>
            <div className="flex justify-between border-t border-slate-200/60 pt-3">
              <span className="text-slate-700 font-medium">
                {isStudent ? "Total Charged:" : "Total Earned:"}
              </span>
              <span className="font-black text-indigo-700 text-base">
                🪙 {summary.finalCharge != null ? Number(summary.finalCharge).toFixed(2) : "0.00"} PGC
              </span>
            </div>
            {isStudent && (
              <div className="flex justify-between text-xs text-emerald-700">
                <span>Refunded to Wallet:</span>
                <span className="font-bold">
                  +🪙 {summary.refundedAmount != null ? Number(summary.refundedAmount).toFixed(2) : "0.00"} PGC
                </span>
              </div>
            )}
          </div>

          <div className="mt-8 flex gap-3">
            <Button
              variant="primary"
              onClick={() => navigate(`/courses/${summary.courseId}`)}
              className="w-full"
            >
              Back to Course
            </Button>
          </div>
        </div>
      </main>
    );
  }

  // Active LiveKit Call View
  return (
    <main className="relative flex h-[calc(100vh-64px)] flex-col bg-slate-950 overflow-hidden">
      {/* Session Confirmation Modal */}
      {showEndConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-sm rounded-2xl border border-slate-700 bg-slate-900 p-6 text-white shadow-2xl">
            <h3 className="text-lg font-bold">End Doubt Session?</h3>
            <p className="mt-2 text-xs text-slate-400">
              Are you sure you want to end this call? Billing will immediately stop, and any unused reserved GeekCoins will be released back to the student's wallet.
            </p>
            <div className="mt-5 flex gap-3">
              <Button
                variant="secondary"
                disabled={endMutation.isPending}
                onClick={() => setShowEndConfirm(false)}
                className="flex-1 bg-slate-800 text-white border-slate-700 hover:bg-slate-700 text-xs"
              >
                Continue Call
              </Button>
              <Button
                variant="danger"
                disabled={endMutation.isPending}
                onClick={() => endMutation.mutate()}
                className="flex-1 text-xs"
              >
                {endMutation.isPending ? "Ending..." : "End Session"}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* LiveKit Room */}
      {tokenData?.token ? (
        <LiveKitRoom
          token={tokenData.token}
          serverUrl={tokenData.serverUrl}
          connect={true}
          video={true}
          audio={true}
          onDisconnected={() => {
            queryClient.invalidateQueries({ queryKey: ["doubt-session", sessionId] });
          }}
          data-lk-theme="default"
          className="flex h-full flex-col"
        >
          <RoomLiveStatus
            session={session}
            onEndCall={() => setShowEndConfirm(true)}
          />
          <div className="relative flex-1 overflow-hidden">
            <VideoConference />
            <RoomAudioRenderer />
          </div>
        </LiveKitRoom>
      ) : (
        <div className="flex flex-1 items-center justify-center text-slate-400 text-sm">
          <Spinner label="Preparing video connection..." />
        </div>
      )}
    </main>
  );
}
