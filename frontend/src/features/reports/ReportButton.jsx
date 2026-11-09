import { useState } from "react";
import { ReportModal } from "./ReportModal";

export function ReportButton({ courseId }) {
  const [modalOpen, setModalOpen] = useState(false);

  if (!courseId) return null;

  return (
    <>
      <button
        type="button"
        onClick={() => setModalOpen(true)}
        className="inline-flex items-center gap-1 text-xs font-semibold text-slate-500 hover:text-danger-600 transition"
        aria-label="Report an issue with this course content"
      >
        <span>🚩</span>
        <span>Report issue</span>
      </button>

      <ReportModal
        open={modalOpen}
        onOpenChange={setModalOpen}
        courseId={courseId}
      />
    </>
  );
}
