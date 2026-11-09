import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { submitReport } from "./reportsApi";
import { Dialog } from "../../shared/components/Dialog";
import { Button } from "../../shared/components/Button";

const REPORT_CATEGORIES = [
  {
    value: "INCORRECT_OR_MISLEADING_INFORMATION",
    label: "Incorrect or misleading information",
  },
  { value: "BROKEN_RESOURCE", label: "Broken or unplayable resource" },
  { value: "INCOMPLETE_CONTENT", label: "Incomplete content" },
  {
    value: "OFFENSIVE_OR_INAPPROPRIATE_CONTENT",
    label: "Offensive or inappropriate content",
  },
  { value: "COPYRIGHT_CONCERN", label: "Copyright concern" },
  { value: "MISLEADING_DESCRIPTION", label: "Misleading description" },
  { value: "OTHER", label: "Other issue" },
];

export function ReportModal({ open, onOpenChange, courseId }) {
  const queryClient = useQueryClient();
  const [category, setCategory] = useState("BROKEN_RESOURCE");
  const [details, setDetails] = useState("");
  const [submitted, setSubmitted] = useState(false);

  const reportMutation = useMutation({
    mutationFn: () =>
      submitReport(courseId, { category, details: details.trim() }),
    onSuccess: () => {
      setSubmitted(true);
      queryClient.invalidateQueries({ queryKey: ["admin", "reports"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "summary"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "activities"] });
      queryClient.invalidateQueries({ queryKey: ["reports", courseId] });
    },
  });

  function handleClose() {
    onOpenChange(false);
    setTimeout(() => {
      setSubmitted(false);
      setDetails("");
      setCategory("BROKEN_RESOURCE");
    }, 200);
  }

  function handleSubmit(e) {
    e.preventDefault();
    if (!details.trim()) return;
    reportMutation.mutate();
  }

  return (
    <Dialog
      open={open}
      onOpenChange={handleClose}
      title="Report Course Issue"
      description="Help maintain course quality. Reports are reviewed by the course instructor and PeerGeek moderation."
    >
      {submitted ? (
        <div className="py-6 text-center space-y-4">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-emerald-100 text-emerald-600 text-2xl">
            ✓
          </div>
          <h3 className="text-base font-bold text-ink-900">
            Thank you for your report
          </h3>
          <p className="text-sm text-slate-600">
            Your report has been submitted to the course instructor for review.
          </p>
          <div className="pt-2">
            <Button type="button" onClick={handleClose} className="w-full">
              Close
            </Button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4 mt-4">
          <div>
            <label
              htmlFor="report-category"
              className="block text-xs font-semibold text-slate-700 mb-1"
            >
              Issue Category
            </label>
            <select
              id="report-category"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="w-full rounded-lg border border-surface-300 bg-white p-2.5 text-sm text-ink-900 focus:border-brand-500 focus:outline-hidden focus:ring-1 focus:ring-brand-500"
            >
              {REPORT_CATEGORIES.map((cat) => (
                <option key={cat.value} value={cat.value}>
                  {cat.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label
              htmlFor="report-details"
              className="block text-xs font-semibold text-slate-700 mb-1"
            >
              Details
            </label>
            <textarea
              id="report-details"
              rows={4}
              required
              value={details}
              onChange={(e) => setDetails(e.target.value)}
              placeholder="Describe the problem, specific lesson, or broken resource..."
              maxLength={2000}
              className="w-full rounded-lg border border-surface-300 p-2.5 text-sm text-ink-900 placeholder:text-slate-400 focus:border-brand-500 focus:outline-hidden focus:ring-1 focus:ring-brand-500"
            />
          </div>

          {reportMutation.isError && (
            <p className="text-xs font-medium text-danger-600">
              Failed to submit report. Please try again.
            </p>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <Button
              type="button"
              variant="secondary"
              onClick={handleClose}
              disabled={reportMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={reportMutation.isPending || !details.trim()}
            >
              {reportMutation.isPending ? "Submitting..." : "Submit Report"}
            </Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}
