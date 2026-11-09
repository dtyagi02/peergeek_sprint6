import { useState } from "react";
import { useParams } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getInstructorSubmission,
  gradeSubmission,
  getInstructorDownloadUrl,
} from "./assignmentApi";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { ErrorState } from "../../shared/components/ErrorState";
import * as Dialog from "@radix-ui/react-dialog";

export function ManualGradingPage() {
  const { submissionId } = useParams();

  const submissionQuery = useQuery({
    queryKey: ["instructor", "submission", submissionId],
    queryFn: () => getInstructorSubmission(submissionId),
  });

  if (submissionQuery.isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Spinner label="Loading submission for grading..." />
      </div>
    );
  }

  if (submissionQuery.isError) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-8">
        <ErrorState
          title="Submission Not Found"
          message={
            submissionQuery.error?.message || "Unable to load submission."
          }
          onRetry={() => submissionQuery.refetch()}
        />
      </div>
    );
  }

  const sub = submissionQuery.data;

  return (
    <GradingForm
      key={`${sub.submissionId}-${sub.version}`}
      sub={sub}
      submissionQuery={submissionQuery}
    />
  );
}

function GradingForm({ sub, submissionQuery }) {
  const queryClient = useQueryClient();
  const submissionId = sub.submissionId;

  const [versionConflictOpen, setVersionConflictOpen] = useState(false);
  const [successMessage, setSuccessMessage] = useState("");
  const [errorMessage, setErrorMessage] = useState("");

  // Initialize grade form state directly without useEffect
  const [projectMarks, setProjectMarks] = useState(() => {
    if (sub.kind === "PROJECT") {
      return sub.marks !== null && sub.marks !== undefined
        ? String(sub.marks)
        : "";
    }
    return "";
  });

  const [overallFeedback, setOverallFeedback] = useState(
    () => sub.overallFeedback || "",
  );

  const [questionGrades, setQuestionGrades] = useState(() => {
    const initialMap = {};
    if (sub.questions && sub.questions.length > 0) {
      sub.questions.forEach((q) => {
        const existingAns = q.answer;
        const pts =
          existingAns?.points !== null && existingAns?.points !== undefined
            ? String(existingAns.points)
            : existingAns?.autoPoints !== null &&
                existingAns?.autoPoints !== undefined
              ? String(existingAns.autoPoints)
              : "";

        initialMap[q.questionId] = {
          points: pts,
          feedback: existingAns?.feedback || "",
        };
      });
    }
    return initialMap;
  });

  const [validationErrors, setValidationErrors] = useState({});

  const gradeMutation = useMutation({
    mutationFn: (payload) => gradeSubmission(submissionId, payload),
    onSuccess: (updated) => {
      queryClient.setQueryData(
        ["instructor", "submission", submissionId],
        updated,
      );
      queryClient.invalidateQueries({
        queryKey: ["instructor", "submissions"],
      });
      setSuccessMessage("Grade saved successfully!");
      setErrorMessage("");
      setValidationErrors({});
      setTimeout(() => setSuccessMessage(""), 5000);
    },
    onError: (err) => {
      if (
        err.status === 409 ||
        err.message?.includes("conflict") ||
        err.message?.includes("Version")
      ) {
        setVersionConflictOpen(true);
      } else {
        setErrorMessage(err.message || "Failed to save grade");
      }
    },
  });

  const handleDownloadFile = async (fileId, filename) => {
    try {
      const res = await getInstructorDownloadUrl(submissionId, fileId);
      if (res?.url) {
        const link = document.createElement("a");
        link.href = res.url;
        link.download = filename || "submission_file";
        link.target = "_blank";
        link.rel = "noreferrer noopener";
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      }
    } catch (e) {
      setErrorMessage("Failed to generate download URL: " + e.message);
    }
  };

  const handleQuestionPointChange = (qId, val) => {
    setQuestionGrades((prev) => ({
      ...prev,
      [qId]: {
        ...prev[qId],
        points: val,
      },
    }));
    // clear validation error
    if (validationErrors[qId]) {
      setValidationErrors((prev) => {
        const next = { ...prev };
        delete next[qId];
        return next;
      });
    }
  };

  const handleQuestionFeedbackChange = (qId, val) => {
    setQuestionGrades((prev) => ({
      ...prev,
      [qId]: {
        ...prev[qId],
        feedback: val,
      },
    }));
  };

  const validateAndSave = () => {
    setErrorMessage("");
    const errors = {};

    if (sub.kind === "PROJECT") {
      if (projectMarks === "" || isNaN(Number(projectMarks))) {
        errors.marks = "Marks are required for a project submission.";
      } else {
        const num = Number(projectMarks);
        if (num < 0 || num > sub.maxPoints) {
          errors.marks = `Marks must be between 0 and ${sub.maxPoints}.`;
        }
      }

      if (Object.keys(errors).length > 0) {
        setValidationErrors(errors);
        return;
      }

      gradeMutation.mutate({
        version: sub.version,
        marks: Number(projectMarks),
        overallFeedback: overallFeedback.trim() || undefined,
      });
    } else {
      // QUESTIONS
      const qGradesPayload = [];
      let incomplete = false;

      sub.questions.forEach((q) => {
        const entry = questionGrades[q.questionId] || {
          points: "",
          feedback: "",
        };
        const ptsStr = entry.points;

        const isAuto = q.type === "MCQ_SINGLE" || q.type === "MCQ_MULTI";

        if (ptsStr === "" || isNaN(Number(ptsStr))) {
          if (!isAuto) {
            errors[q.questionId] = "Points are required for this question.";
            incomplete = true;
          }
        } else {
          const num = Number(ptsStr);
          if (num < 0 || num > q.points) {
            errors[q.questionId] = `Points must be between 0 and ${q.points}.`;
          } else {
            qGradesPayload.push({
              questionId: q.questionId,
              points: num,
              feedback: entry.feedback?.trim() || undefined,
            });
          }
        }
      });

      if (Object.keys(errors).length > 0) {
        setValidationErrors(errors);
        if (incomplete) {
          setErrorMessage(
            "All manual questions must be assigned points before saving.",
          );
        }
        return;
      }

      gradeMutation.mutate({
        version: sub.version,
        questionGrades: qGradesPayload,
        overallFeedback: overallFeedback.trim() || undefined,
      });
    }
  };

  // Recompute preview total points
  const previewTotalPoints = () => {
    if (!sub) return 0;
    if (sub.kind === "PROJECT") {
      return Number(projectMarks) || 0;
    }
    return (sub.questions || []).reduce((acc, q) => {
      const entry = questionGrades[q.questionId];
      const pts =
        entry?.points !== "" && !isNaN(Number(entry?.points))
          ? Number(entry.points)
          : (q.answer?.autoPoints ?? 0);
      return acc + pts;
    }, 0);
  };

  if (submissionQuery.isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Spinner label="Loading submission for grading..." />
      </div>
    );
  }

  if (submissionQuery.isError) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-8">
        <ErrorState
          title="Submission Not Found"
          message={
            submissionQuery.error?.message || "Unable to load submission."
          }
          onRetry={() => submissionQuery.refetch()}
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
      {/* Top Breadcrumb & Status */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <button
            type="button"
            onClick={() => window.history.back()}
            className="text-sm font-medium text-brand-600 hover:text-brand-700"
          >
            ← Back to Queue
          </button>
          <h1 className="mt-2 text-2xl font-bold tracking-tight text-ink-900">
            Grade: {sub.assignmentTitle}
          </h1>
          <p className="mt-1 text-sm text-ink-600">
            Learner:{" "}
            <span className="font-semibold text-ink-900">
              {sub.learner?.displayName}
            </span>
          </p>
        </div>

        {/* Status badges */}
        <div className="flex flex-wrap items-center gap-2">
          {sub.status === "SUBMITTED" && (
            <span className="rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-800">
              Submitted
            </span>
          )}
          {sub.status === "GRADED" && (
            <span className="rounded-full bg-emerald-100 px-3 py-1 text-xs font-semibold text-emerald-800">
              Graded
            </span>
          )}
          {sub.status === "IN_PROGRESS" && (
            <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-800">
              In Progress
            </span>
          )}
          {sub.late && (
            <span className="rounded-full bg-rose-100 px-2.5 py-1 text-xs font-semibold text-rose-800">
              Late
            </span>
          )}
          {sub.autoSubmitted && (
            <span className="rounded-full bg-purple-100 px-2.5 py-1 text-xs font-semibold text-purple-800">
              Auto-submitted
            </span>
          )}
          {sub.autoGraded && (
            <span className="rounded-full bg-teal-100 px-2.5 py-1 text-xs font-semibold text-teal-800">
              Auto-graded
            </span>
          )}
        </div>
      </div>

      {/* Alert Banners */}
      {successMessage && (
        <div className="mb-6 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
          ✓ {successMessage}
        </div>
      )}
      {errorMessage && (
        <div className="mb-6 rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-900">
          ⚠ {errorMessage}
        </div>
      )}

      {/* Submission Meta Card */}
      <div className="mb-8 grid grid-cols-1 gap-4 rounded-xl border border-surface-200 bg-white p-5 shadow-sm sm:grid-cols-4">
        <div>
          <span className="text-xs font-semibold uppercase tracking-wider text-ink-500">
            Started At
          </span>
          <p className="mt-1 text-sm font-medium text-ink-900">
            {sub.startedAt
              ? new Date(sub.startedAt).toLocaleString(undefined, {
                  dateStyle: "medium",
                  timeStyle: "short",
                })
              : "—"}
          </p>
        </div>
        <div>
          <span className="text-xs font-semibold uppercase tracking-wider text-ink-500">
            Submitted At
          </span>
          <p className="mt-1 text-sm font-medium text-ink-900">
            {sub.submittedAt
              ? new Date(sub.submittedAt).toLocaleString(undefined, {
                  dateStyle: "medium",
                  timeStyle: "short",
                })
              : "—"}
          </p>
        </div>
        <div>
          <span className="text-xs font-semibold uppercase tracking-wider text-ink-500">
            Graded By
          </span>
          <p className="mt-1 text-sm font-medium text-ink-900">
            {sub.gradedBy?.displayName ||
              (sub.autoGraded ? "System (Auto)" : "Not graded yet")}
          </p>
        </div>
        <div>
          <span className="text-xs font-semibold uppercase tracking-wider text-ink-500">
            Calculated Score
          </span>
          <p className="mt-1 text-lg font-bold text-brand-700">
            {previewTotalPoints().toFixed(2)} / {sub.maxPoints} pts
          </p>
        </div>
      </div>

      {/* Body: PROJECT or QUESTIONS */}
      {sub.kind === "PROJECT" ? (
        <div className="space-y-6">
          {/* Project Text Answer */}
          <div className="rounded-xl border border-surface-200 bg-white p-6 shadow-sm">
            <h2 className="text-base font-semibold text-ink-900">
              Learner Text Response
            </h2>
            {sub.textAnswer ? (
              <div className="mt-3 whitespace-pre-wrap rounded-lg bg-surface-50 p-4 text-sm text-ink-800 border border-surface-200 font-sans">
                {sub.textAnswer}
              </div>
            ) : (
              <p className="mt-3 text-sm italic text-ink-500">
                No text response provided.
              </p>
            )}
          </div>

          {/* Project Attached Files */}
          <div className="rounded-xl border border-surface-200 bg-white p-6 shadow-sm">
            <h2 className="text-base font-semibold text-ink-900">
              Submitted Files ({sub.files?.length || 0})
            </h2>
            {sub.files && sub.files.length > 0 ? (
              <ul className="mt-4 divide-y divide-surface-100">
                {sub.files.map((file) => (
                  <li
                    key={file.id}
                    className="flex flex-wrap items-center justify-between gap-4 py-3"
                  >
                    <div>
                      <p className="text-sm font-medium text-ink-900">
                        {file.originalFilename}
                      </p>
                      <p className="text-xs text-ink-500">
                        {(file.sizeBytes / 1024).toFixed(1)} KB ·{" "}
                        {file.mimeType}
                      </p>
                    </div>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() =>
                        handleDownloadFile(file.id, file.originalFilename)
                      }
                    >
                      Download File
                    </Button>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mt-3 text-sm italic text-ink-500">
                No files uploaded.
              </p>
            )}
          </div>

          {/* Project Grading Input */}
          <div className="rounded-xl border border-surface-200 bg-white p-6 shadow-sm">
            <h2 className="text-base font-semibold text-ink-900">
              Project Evaluation
            </h2>
            <div className="mt-4 max-w-xs">
              <label
                htmlFor="project-marks"
                className="block text-xs font-semibold text-ink-700"
              >
                Marks (Max: {sub.maxPoints}) *
              </label>
              <input
                id="project-marks"
                type="number"
                step="0.01"
                min="0"
                max={sub.maxPoints}
                value={projectMarks}
                onChange={(e) => {
                  setProjectMarks(e.target.value);
                  if (validationErrors.marks) {
                    setValidationErrors((prev) => {
                      const next = { ...prev };
                      delete next.marks;
                      return next;
                    });
                  }
                }}
                className={`mt-1 block w-full rounded-lg border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-1 ${
                  validationErrors.marks
                    ? "border-rose-400 focus:border-rose-500 focus:ring-rose-500"
                    : "border-surface-300 focus:border-brand-500 focus:ring-brand-500"
                }`}
              />
              {validationErrors.marks && (
                <p className="mt-1 text-xs text-rose-600">
                  {validationErrors.marks}
                </p>
              )}
            </div>

            <div className="mt-6">
              <label
                htmlFor="overall-feedback-project"
                className="block text-xs font-semibold text-ink-700"
              >
                Overall Feedback (Optional, max 5000 chars)
              </label>
              <textarea
                id="overall-feedback-project"
                rows={4}
                maxLength={5000}
                value={overallFeedback}
                onChange={(e) => setOverallFeedback(e.target.value)}
                placeholder="Provide constructive feedback for the learner..."
                className="mt-1 block w-full rounded-lg border border-surface-300 p-3 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              />
              <span className="mt-1 block text-right text-xs text-ink-500">
                {overallFeedback.length} / 5000
              </span>
            </div>
          </div>
        </div>
      ) : (
        /* QUESTIONS List */
        <div className="space-y-6">
          {(sub.questions || []).map((q, idx) => {
            const ans = q.answer;
            const currentGrade = questionGrades[q.questionId] || {
              points: "",
              feedback: "",
            };
            const qError = validationErrors[q.questionId];

            return (
              <div
                key={q.questionId}
                className="rounded-xl border border-surface-200 bg-white p-6 shadow-sm"
              >
                {/* Question Header */}
                <div className="flex flex-wrap items-center justify-between gap-2 border-b border-surface-100 pb-3">
                  <div className="flex items-center gap-2">
                    <span className="flex h-6 w-6 items-center justify-center rounded-full bg-brand-100 text-xs font-bold text-brand-800">
                      {idx + 1}
                    </span>
                    <span className="text-xs font-semibold uppercase tracking-wider text-ink-500">
                      {q.type.replace("_", " ")}
                    </span>
                  </div>
                  <span className="text-xs font-semibold text-ink-700">
                    Max: {q.points} pts
                  </span>
                </div>

                {/* Prompt */}
                <div className="mt-4 whitespace-pre-wrap text-sm font-medium text-ink-900">
                  {q.prompt}
                </div>

                {/* Learner Answer Section */}
                <div className="mt-4 rounded-lg bg-surface-50 p-4 border border-surface-200">
                  <span className="text-xs font-semibold uppercase tracking-wider text-ink-500">
                    Learner Answer:
                  </span>

                  {/* MCQ Types */}
                  {(q.type === "MCQ_SINGLE" || q.type === "MCQ_MULTI") && (
                    <div className="mt-3 space-y-2">
                      {(q.options || []).map((opt) => {
                        const isSelected = ans?.selectedOptionIds?.includes(
                          opt.id,
                        );
                        const isCorrect = opt.correct === true;

                        return (
                          <div
                            key={opt.id}
                            className={`flex items-start gap-3 rounded-lg border p-3 text-sm ${
                              isSelected && isCorrect
                                ? "border-emerald-300 bg-emerald-50 text-emerald-900"
                                : isSelected && !isCorrect
                                  ? "border-rose-300 bg-rose-50 text-rose-900"
                                  : isCorrect
                                    ? "border-emerald-200 bg-white text-emerald-800 border-dashed"
                                    : "border-surface-200 bg-white text-ink-700"
                            }`}
                          >
                            <input
                              type={
                                q.type === "MCQ_SINGLE" ? "radio" : "checkbox"
                              }
                              checked={isSelected || false}
                              disabled
                              className="mt-0.5"
                            />
                            <div className="flex-1">
                              <span>{opt.text}</span>
                              <div className="mt-1 flex items-center gap-2 text-xs">
                                {isSelected && (
                                  <span className="font-semibold">
                                    [Learner selected]
                                  </span>
                                )}
                                {isCorrect && (
                                  <span className="font-semibold text-emerald-700">
                                    ✓ Correct option
                                  </span>
                                )}
                              </div>
                            </div>
                          </div>
                        );
                      })}
                      {ans?.autoPoints !== null &&
                        ans?.autoPoints !== undefined && (
                          <p className="mt-2 text-xs font-medium text-ink-600">
                            Auto-score: {ans.autoPoints} / {q.points} pts
                          </p>
                        )}
                    </div>
                  )}

                  {/* DESCRIPTIVE */}
                  {q.type === "DESCRIPTIVE" && (
                    <div className="mt-2 whitespace-pre-wrap text-sm text-ink-800 font-sans">
                      {ans?.answerText ? (
                        ans.answerText
                      ) : (
                        <span className="italic text-ink-400">
                          No answer provided.
                        </span>
                      )}
                    </div>
                  )}

                  {/* CODE: Plain text in monospace <pre>, NEVER executed */}
                  {q.type === "CODE" && (
                    <div className="mt-2">
                      {q.codeLanguage && (
                        <span className="mb-1 inline-block rounded bg-surface-200 px-2 py-0.5 text-xs font-mono text-ink-700">
                          {q.codeLanguage}
                        </span>
                      )}
                      <pre className="overflow-x-auto rounded-lg bg-surface-900 p-4 font-mono text-xs text-surface-100">
                        {ans?.answerText || "// No code answer submitted."}
                      </pre>
                    </div>
                  )}
                </div>

                {/* Grade Input for this Question */}
                <div className="mt-5 grid grid-cols-1 gap-4 border-t border-surface-100 pt-4 sm:grid-cols-3">
                  <div>
                    <label
                      htmlFor={`q-pts-${q.questionId}`}
                      className="block text-xs font-semibold text-ink-700"
                    >
                      Assigned Points * (Max: {q.points})
                    </label>
                    <input
                      id={`q-pts-${q.questionId}`}
                      type="number"
                      step="0.01"
                      min="0"
                      max={q.points}
                      value={currentGrade.points}
                      onChange={(e) =>
                        handleQuestionPointChange(q.questionId, e.target.value)
                      }
                      className={`mt-1 block w-full rounded-lg border px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-1 ${
                        qError
                          ? "border-rose-400 focus:border-rose-500 focus:ring-rose-500"
                          : "border-surface-300 focus:border-brand-500 focus:ring-brand-500"
                      }`}
                    />
                    {qError && (
                      <p className="mt-1 text-xs text-rose-600">{qError}</p>
                    )}
                  </div>

                  <div className="sm:col-span-2">
                    <label
                      htmlFor={`q-fb-${q.questionId}`}
                      className="block text-xs font-semibold text-ink-700"
                    >
                      Question Feedback (Optional)
                    </label>
                    <input
                      id={`q-fb-${q.questionId}`}
                      type="text"
                      maxLength={5000}
                      value={currentGrade.feedback}
                      onChange={(e) =>
                        handleQuestionFeedbackChange(
                          q.questionId,
                          e.target.value,
                        )
                      }
                      placeholder="Comment on this answer..."
                      className="mt-1 block w-full rounded-lg border border-surface-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                    />
                  </div>
                </div>
              </div>
            );
          })}

          {/* Overall Feedback Card */}
          <div className="rounded-xl border border-surface-200 bg-white p-6 shadow-sm">
            <h2 className="text-base font-semibold text-ink-900">
              Overall Assessment Feedback
            </h2>
            <div className="mt-4">
              <label
                htmlFor="overall-feedback-questions"
                className="block text-xs font-semibold text-ink-700"
              >
                Feedback (Optional, max 5000 chars)
              </label>
              <textarea
                id="overall-feedback-questions"
                rows={4}
                maxLength={5000}
                value={overallFeedback}
                onChange={(e) => setOverallFeedback(e.target.value)}
                placeholder="Summary feedback for the learner..."
                className="mt-1 block w-full rounded-lg border border-surface-300 p-3 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              />
              <span className="mt-1 block text-right text-xs text-ink-500">
                {overallFeedback.length} / 5000
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Save Action Footer */}
      <div className="mt-8 flex items-center justify-between border-t border-surface-200 pt-6">
        <div className="text-sm text-ink-600">
          Total Score:{" "}
          <span className="font-bold text-ink-900">
            {previewTotalPoints().toFixed(2)} / {sub.maxPoints} pts
          </span>
        </div>
        <div className="flex items-center gap-3">
          <Button
            type="button"
            variant="primary"
            disabled={gradeMutation.isPending || sub.status === "IN_PROGRESS"}
            onClick={validateAndSave}
          >
            {gradeMutation.isPending ? "Saving Grade..." : "Save Grade"}
          </Button>
        </div>
      </div>

      {/* 409 Version Conflict Dialog */}
      <Dialog.Root
        open={versionConflictOpen}
        onOpenChange={setVersionConflictOpen}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-40 bg-slate-950/50 backdrop-blur-sm" />
          <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,28rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl">
            <Dialog.Title className="text-lg font-bold text-ink-900">
              Grading Conflict Detected
            </Dialog.Title>
            <Dialog.Description className="mt-2 text-sm text-ink-600">
              Another instructor or session has updated this submission while
              you were editing. Please reload to inspect the newest version
              before saving.
            </Dialog.Description>
            <div className="mt-6 flex justify-end gap-3">
              <Button
                variant="primary"
                onClick={() => {
                  setVersionConflictOpen(false);
                  submissionQuery.refetch();
                }}
              >
                Reload Latest Version
              </Button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
}
