import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "../../shared/components/Button";
import { CourseDoubtSessionCard } from "./CourseDoubtSessionCard";
import { getCourseAvailability } from "./doubtSessionApi";

export function PlayerDoubtButton({ courseId }) {
  const [open, setOpen] = useState(false);

  const { data: availability } = useQuery({
    queryKey: ["course-doubt-availability", courseId],
    queryFn: () => getCourseAvailability(courseId),
    enabled: !!courseId,
    refetchInterval: 8000,
  });

  const isLive = availability?.enabled && availability?.available && !availability?.instructorHasActiveSession;

  return (
    <>
      <button
        type="button"
        data-testid="player-ask-doubt-btn"
        onClick={() => setOpen(true)}
        className={`inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-bold transition-all shadow-xs ${
          isLive
            ? "bg-gradient-to-r from-emerald-600 to-teal-600 text-white hover:from-emerald-700 hover:to-teal-700 ring-2 ring-emerald-400/40 animate-pulse"
            : "border border-indigo-200 bg-indigo-50 text-indigo-700 hover:bg-indigo-100"
        }`}
      >
        <span>💬</span>
        <span>Ask Doubt Live</span>
        {isLive && (
          <span className="h-2 w-2 rounded-full bg-emerald-300 animate-ping" />
        )}
      </button>

      {open && (
        <div
          data-testid="player-doubt-modal"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs animate-in fade-in duration-150"
        >
          <div className="relative w-full max-w-lg rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl">
            {/* Close Button */}
            <button
              type="button"
              onClick={() => setOpen(false)}
              className="absolute right-4 top-4 rounded-full p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
              aria-label="Close doubt session window"
            >
              ✕
            </button>

            <CourseDoubtSessionCard
              courseId={courseId}
              isEnrolled={true}
            />
          </div>
        </div>
      )}
    </>
  );
}
