import { useState, useEffect, useRef, useCallback } from "react";
import { Link, useParams } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getLearnerAssignmentDetail,
  startAttempt,
  getAttempt,
  saveDraft,
  submitAssignment,
  getLearnerSubmission,
  initiateFileUpload,
  confirmFileUpload,
  deleteFile,
  getLearnerDownloadUrl,
} from "./assignmentApi";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { ErrorState } from "../../shared/components/ErrorState";
import * as Dialog from "@radix-ui/react-dialog";

export function TakeAssignmentPage() {
  const { assignmentId } = useParams();

  const detailQuery = useQuery({
    queryKey: ["learner", "assignment", assignmentId],
    queryFn: () => getLearnerAssignmentDetail(assignmentId),
  });

  if (detailQuery.isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Spinner label="Loading assignment..." />
      </div>
    );
  }

  if (detailQuery.isError) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-8">
        <ErrorState
          title="Assignment Unavailable"
          message={
            detailQuery.error?.message || "Unable to load this assignment."
          }
          onRetry={() => detailQuery.refetch()}
        />
      </div>
    );
  }

  const assignment = detailQuery.data;

  // Render sub-flow based on attempt status (supporting both nested attempt object and flat properties)
  const attemptStatus =
    assignment.attempt?.status || assignment.attemptStatus || null;
  const hasAttempt = Boolean(assignment.attempt || assignment.hasAttempt);

  if (!hasAttempt || !attemptStatus) {
    return (
      <BeforeStartView
        assignment={assignment}
        onStarted={() => detailQuery.refetch()}
      />
    );
  }

  if (attemptStatus === "IN_PROGRESS") {
    return (
      <InProgressView
        assignment={assignment}
        onSubmitted={() => detailQuery.refetch()}
      />
    );
  }

  // SUBMITTED or GRADED
  return <ResultView assignment={assignment} />;
}

// --------------------------------------------------------------------------
// 1. BEFORE START VIEW
// --------------------------------------------------------------------------
function BeforeStartView({ assignment, onStarted }) {
  const [startConfirmOpen, setStartConfirmOpen] = useState(false);
  const [startError, setStartError] = useState("");

  const startMutation = useMutation({
    mutationFn: () => startAttempt(assignment.id),
    onSuccess: () => {
      onStarted();
    },
    onError: (err) => {
      if (
        err.status === 409 &&
        (err.code === "ALREADY_SUBMITTED" ||
          err.message?.includes("ALREADY_SUBMITTED") ||
          err.message?.includes("already submitted"))
      ) {
        onStarted();
        return;
      }
      if (
        err.status === 409 &&
        (err.code === "DEADLINE_PASSED" ||
          err.message?.includes("DEADLINE_PASSED"))
      ) {
        setStartError("The deadline for this assignment has passed.");
      } else if (
        err.status === 409 &&
        (err.code === "ASSIGNMENT_NOT_OPEN" ||
          err.message?.includes("ASSIGNMENT_NOT_OPEN"))
      ) {
        setStartError("This assignment is not open yet.");
      } else {
        setStartError(err.message || "Failed to start attempt");
      }
    },
  });

  const handleStartClick = () => {
    if (assignment.timeLimitMinutes) {
      setStartConfirmOpen(true);
    } else {
      startMutation.mutate();
    }
  };

  return (
    <div className="mx-auto max-w-4xl px-4 py-10 sm:px-6 lg:px-8">
      {/* Back button */}
      <div className="mb-6">
        <Link
          to={`/learning/courses/${assignment.courseId}/assignments`}
          className="text-sm font-medium text-brand-600 hover:text-brand-700"
        >
          ← Back to Course Assignments
        </Link>
      </div>

      <div className="rounded-2xl border border-surface-200 bg-white p-6 shadow-sm sm:p-8">
        {/* Title & Kind Badge */}
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-surface-100 pb-5">
          <div>
            <span className="inline-block rounded-full bg-brand-50 px-3 py-1 text-xs font-semibold text-brand-700">
              {assignment.kind === "PROJECT"
                ? "Practical Project"
                : "Assessment"}
            </span>
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-ink-900 sm:text-3xl">
              {assignment.title}
            </h1>
          </div>
          <div className="text-right">
            <span className="text-xs uppercase tracking-wider text-ink-500">
              Max Score
            </span>
            <div className="text-xl font-extrabold text-brand-700">
              {assignment.maxPoints} pts
            </div>
          </div>
        </div>

        {/* Instructions (Plain text with line breaks, NEVER dangerouslySetInnerHTML) */}
        <div className="mt-6">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-ink-500">
            Instructions
          </h2>
          <div className="mt-3 whitespace-pre-wrap rounded-xl bg-surface-50 p-5 text-sm leading-relaxed text-ink-800 border border-surface-200 font-sans">
            {assignment.instructions || "No special instructions provided."}
          </div>
        </div>

        {/* Rules & Timing Metadata Grid */}
        <div className="mt-8 grid grid-cols-1 gap-4 rounded-xl border border-surface-200 bg-surface-50/50 p-5 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <span className="text-xs font-semibold text-ink-500">Opens At</span>
            <p className="mt-1 text-sm font-medium text-ink-900">
              {assignment.opensAt
                ? new Date(assignment.opensAt).toLocaleString(undefined, {
                    dateStyle: "medium",
                    timeStyle: "short",
                  })
                : "Open now"}
            </p>
          </div>
          <div>
            <span className="text-xs font-semibold text-ink-500">Due At</span>
            <p className="mt-1 text-sm font-medium text-ink-900">
              {assignment.dueAt
                ? new Date(assignment.dueAt).toLocaleString(undefined, {
                    dateStyle: "medium",
                    timeStyle: "short",
                  })
                : "No due date"}
            </p>
          </div>
          <div>
            <span className="text-xs font-semibold text-ink-500">
              Time Limit
            </span>
            <p className="mt-1 text-sm font-medium text-ink-900">
              {assignment.timeLimitMinutes
                ? `${assignment.timeLimitMinutes} minutes`
                : "Untimed"}
            </p>
          </div>
          <div>
            <span className="text-xs font-semibold text-ink-500">
              Late Policy
            </span>
            <p className="mt-1 text-sm font-medium text-ink-900">
              {assignment.allowLate
                ? "Late allowed (flagged)"
                : "Strict deadline"}
            </p>
          </div>
        </div>

        {/* Single attempt disclaimer */}
        <div className="mt-6 rounded-lg border border-amber-200 bg-amber-50 p-4 text-xs text-amber-900">
          <span className="font-semibold">Notice:</span> You have exactly one
          attempt for this assignment. Once started, your answers are
          automatically saved as you write.
        </div>

        {startError && (
          <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-900">
            {startError}
          </div>
        )}

        {/* Action Button */}
        <div className="mt-8 flex justify-end">
          <Button
            size="lg"
            variant="primary"
            disabled={startMutation.isPending}
            onClick={handleStartClick}
          >
            {startMutation.isPending
              ? "Starting..."
              : assignment.timeLimitMinutes
                ? "Start Timed Assessment"
                : "Start Assignment"}
          </Button>
        </div>
      </div>

      {/* Timed Assessment Confirmation Dialog */}
      <Dialog.Root open={startConfirmOpen} onOpenChange={setStartConfirmOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-40 bg-slate-950/50 backdrop-blur-sm" />
          <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,28rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl">
            <Dialog.Title className="text-lg font-bold text-ink-900">
              Start Timed Assessment
            </Dialog.Title>
            <Dialog.Description className="mt-2 text-sm text-ink-600">
              This assessment has a time limit of{" "}
              <strong className="text-ink-900">
                {assignment.timeLimitMinutes} minutes
              </strong>
              . The timer starts immediately upon confirmation and{" "}
              <strong className="text-rose-600">cannot be paused</strong>. Your
              answers will auto-submit when the time expires.
            </Dialog.Description>
            <div className="mt-6 flex justify-end gap-3">
              <Button
                variant="secondary"
                onClick={() => setStartConfirmOpen(false)}
                disabled={startMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="primary"
                onClick={() => {
                  setStartConfirmOpen(false);
                  startMutation.mutate();
                }}
                disabled={startMutation.isPending}
              >
                {startMutation.isPending ? "Starting..." : "I'm Ready, Begin"}
              </Button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
}

// --------------------------------------------------------------------------
// 2. IN PROGRESS VIEW
// --------------------------------------------------------------------------
function InProgressView({ assignment, onSubmitted }) {
  const queryClient = useQueryClient();
  const attemptQuery = useQuery({
    queryKey: ["learner", "attempt", assignment.id],
    queryFn: () => getAttempt(assignment.id),
  });

  if (attemptQuery.isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Spinner label="Loading attempt..." />
      </div>
    );
  }

  if (attemptQuery.isError) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-8">
        <ErrorState
          title="Could not load attempt"
          message={attemptQuery.error?.message}
          onRetry={() => attemptQuery.refetch()}
        />
      </div>
    );
  }

  const initialAttempt = attemptQuery.data;

  return (
    <AttemptSession
      key={initialAttempt.submissionId}
      assignment={assignment}
      initialAttempt={initialAttempt}
      onSubmitted={onSubmitted}
      onReloadAttempt={() => {
        queryClient.invalidateQueries({
          queryKey: ["learner", "attempt", assignment.id],
        });
      }}
    />
  );
}

function AttemptSession({
  assignment,
  initialAttempt,
  onSubmitted,
  onReloadAttempt,
}) {
  const assignmentId = assignment.id;
  const [, setVersion] = useState(initialAttempt.version);

  // Form state
  const [textAnswer, setTextAnswer] = useState(initialAttempt.textAnswer || "");
  // answersMap: questionId -> { answerText, selectedOptionIds }
  const [answersMap, setAnswersMap] = useState(() => {
    const map = {};
    (initialAttempt.answers || []).forEach((a) => {
      map[a.questionId] = {
        answerText: a.answerText || "",
        selectedOptionIds: a.selectedOptionIds || [],
      };
    });
    return map;
  });

  // Files state (for PROJECT)
  const [files, setFiles] = useState(initialAttempt.files || []);
  const [uploadingFiles, setUploadingFiles] = useState([]); // [{ tempId, file, progress, xhr }]

  // Autosave status state
  const [autosaveStatus, setAutosaveStatus] = useState("idle"); // idle | saving | saved | error
  const [lastSavedTime, setLastSavedTime] = useState("");
  const [saveErrorMessage, setSaveErrorMessage] = useState("");
  const isDirtyRef = useRef(false);

  // Modals & Dialogs
  const [conflictDialogOpen, setConflictDialogOpen] = useState(false);
  const [submitDialogOpen, setSubmitDialogOpen] = useState(false);
  const [expiredBanner, setExpiredBanner] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState("");

  // Refs for race-free autosave and submission synchronization
  const versionRef = useRef(initialAttempt.version);
  const inFlightPromiseRef = useRef(null);
  const answersMapRef = useRef(answersMap);
  const textAnswerRef = useRef(textAnswer || "");

  useEffect(() => {
    answersMapRef.current = answersMap;
  }, [answersMap]);

  useEffect(() => {
    textAnswerRef.current = textAnswer;
  }, [textAnswer]);

  // Timer & server offset
  const [remainingSeconds, setRemainingSeconds] = useState(null);
  const [ariaLiveMessage, setAriaLiveMessage] = useState("");
  const serverOffsetRef = useRef(0);

  // Compute server offset
  useEffect(() => {
    if (initialAttempt.serverNow) {
      serverOffsetRef.current =
        new Date(initialAttempt.serverNow).getTime() - Date.now();
    }
  }, [initialAttempt.serverNow]);

  // Autosave execution function (mutex-protected)
  const executeAutosave = useCallback(async () => {
    if (inFlightPromiseRef.current) {
      try {
        await inFlightPromiseRef.current;
      } catch {
        // Continue to attempt saving current dirty state
      }
    }

    if (!isDirtyRef.current) {
      return versionRef.current;
    }

    const currentVersion = versionRef.current;
    const currentAnswers = answersMapRef.current;
    const currentText = textAnswerRef.current;

    const answersPayload = Object.entries(currentAnswers).map(([qId, val]) => ({
      questionId: Number(qId),
      answerText: val.answerText || undefined,
      selectedOptionIds: val.selectedOptionIds?.length
        ? val.selectedOptionIds
        : undefined,
    }));

    setAutosaveStatus("saving");

    const savePromise = (async () => {
      const res = await saveDraft(assignmentId, {
        version: currentVersion,
        textAnswer: currentText || undefined,
        answers: answersPayload,
      });

      versionRef.current = res.version;
      setVersion(res.version);
      isDirtyRef.current = false;
      setAutosaveStatus("saved");
      setLastSavedTime(
        new Date().toLocaleTimeString(undefined, {
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
        }),
      );
      setSaveErrorMessage("");
      return res.version;
    })();

    inFlightPromiseRef.current = savePromise;

    try {
      const newVersion = await savePromise;
      return newVersion;
    } catch (err) {
      if (err.status === 409) {
        if (
          err.message?.includes("EXPIRED") ||
          err.message?.includes("DEADLINE")
        ) {
          setExpiredBanner(true);
          setTimeout(() => onSubmitted(), 3000);
        } else if (
          err.code === "ALREADY_SUBMITTED" ||
          err.message?.includes("ALREADY_SUBMITTED") ||
          err.message?.includes("already been submitted")
        ) {
          onSubmitted();
        } else {
          setConflictDialogOpen(true);
        }
      } else {
        setAutosaveStatus("error");
        setSaveErrorMessage(err.message || "Save failed");
      }
      throw err;
    } finally {
      inFlightPromiseRef.current = null;
    }
  }, [assignmentId, onSubmitted]);

  // Robust final submit handler
  const handleFinalSubmit = useCallback(async () => {
    if (isSubmitting) return;
    setIsSubmitting(true);
    setSubmitError("");

    try {
      let currentVersion = versionRef.current;
      if (isDirtyRef.current || inFlightPromiseRef.current) {
        try {
          currentVersion = await executeAutosave();
        } catch (saveErr) {
          setIsSubmitting(false);
          setSubmitError(
            saveErr.message ||
              "Could not save your latest answers before submitting. Please try again.",
          );
          return;
        }
      }

      await submitAssignment(assignmentId, { version: currentVersion });
      isDirtyRef.current = false;
      setSubmitDialogOpen(false);
      onSubmitted();
    } catch (err) {
      setIsSubmitting(false);
      if (
        err.status === 409 &&
        (err.message?.includes("EXPIRED") || err.message?.includes("DEADLINE"))
      ) {
        setSubmitDialogOpen(false);
        setExpiredBanner(true);
        setTimeout(() => onSubmitted(), 3000);
      } else if (
        err.status === 409 &&
        (err.code === "ALREADY_SUBMITTED" ||
          err.message?.includes("ALREADY_SUBMITTED") ||
          err.message?.includes("already been submitted"))
      ) {
        setSubmitDialogOpen(false);
        onSubmitted();
      } else {
        setSubmitError(
          err.message || "Failed to submit assignment. Please try again.",
        );
      }
    }
  }, [isSubmitting, executeAutosave, assignmentId, onSubmitted]);

  // Debounced Autosave (2 seconds after typing)
  useEffect(() => {
    if (!isDirtyRef.current) return;
    const timer = setTimeout(() => {
      executeAutosave();
    }, 2000);
    return () => clearTimeout(timer);
  }, [answersMap, textAnswer, executeAutosave]);

  // Warn on browser unload if unsaved changes exist
  useEffect(() => {
    const handleBeforeUnload = (e) => {
      if (isDirtyRef.current) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, []);

  // Countdown timer logic
  useEffect(() => {
    if (!initialAttempt.deadlineAt) return;

    const deadline = new Date(initialAttempt.deadlineAt).getTime();

    const interval = setInterval(() => {
      const currentServerTime = Date.now() + serverOffsetRef.current;
      const diffMs = deadline - currentServerTime;
      const diffSec = Math.max(0, Math.floor(diffMs / 1000));
      setRemainingSeconds(diffSec);

      // Aria-live announcements at exactly 10 min, 1 min, and 10 seconds
      if (diffSec === 600) {
        setAriaLiveMessage("10 minutes remaining on assignment");
      } else if (diffSec === 60) {
        setAriaLiveMessage("1 minute remaining on assignment");
      } else if (diffSec === 10) {
        setAriaLiveMessage("10 seconds remaining on assignment");
      }

      // Time expired: auto-submit
      if (diffSec <= 0) {
        clearInterval(interval);
        setExpiredBanner(true);
        handleFinalSubmit();
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [initialAttempt.deadlineAt, handleFinalSubmit]);

  // Format seconds to HH:MM:SS or MM:SS
  const formatTimer = (totalSeconds) => {
    if (totalSeconds === null) return "--:--";
    const h = Math.floor(totalSeconds / 3600);
    const m = Math.floor((totalSeconds % 3600) / 60);
    const s = totalSeconds % 60;
    const pad = (n) => String(n).padStart(2, "0");
    if (h > 0) {
      return `${pad(h)}:${pad(m)}:${pad(s)}`;
    }
    return `${pad(m)}:${pad(s)}`;
  };

  // Helper: answer updates
  const handleMcqSingleSelect = (questionId, optionId) => {
    isDirtyRef.current = true;
    setAnswersMap((prev) => ({
      ...prev,
      [questionId]: {
        ...(prev[questionId] || {}),
        selectedOptionIds: [optionId],
      },
    }));
  };

  const handleMcqMultiToggle = (questionId, optionId) => {
    isDirtyRef.current = true;
    setAnswersMap((prev) => {
      const currentSelected = prev[questionId]?.selectedOptionIds || [];
      const updated = currentSelected.includes(optionId)
        ? currentSelected.filter((id) => id !== optionId)
        : [...currentSelected, optionId];
      return {
        ...prev,
        [questionId]: {
          ...(prev[questionId] || {}),
          selectedOptionIds: updated,
        },
      };
    });
  };

  const handleTextAnswerChange = (questionId, text) => {
    isDirtyRef.current = true;
    setAnswersMap((prev) => ({
      ...prev,
      [questionId]: {
        ...(prev[questionId] || {}),
        answerText: text,
      },
    }));
  };

  // File Upload Handlers (XHR with real progress)
  const handleFileSelect = async (e) => {
    const selectedList = Array.from(e.target.files || []);
    if (!selectedList.length) return;

    if (files.length + selectedList.length > 5) {
      alert("Maximum 5 files allowed per submission.");
      return;
    }

    for (const file of selectedList) {
      if (file.size > 25 * 1024 * 1024) {
        alert(`File ${file.name} exceeds 25 MB cap.`);
        continue;
      }
      uploadSingleFile(file);
    }
    // reset input
    e.target.value = "";
  };

  const uploadSingleFile = async (file) => {
    const tempId =
      String(Date.now()) + Math.random().toString(36).substring(2, 5);
    const uploadItem = {
      tempId,
      file,
      progress: 0,
      status: "UPLOADING",
      error: "",
    };
    setUploadingFiles((prev) => [...prev, uploadItem]);

    try {
      // 1. Request presigned upload
      const initRes = await initiateFileUpload(assignmentId, {
        filename: file.name,
        mimeType: file.type || "application/octet-stream",
        sizeBytes: file.size,
      });

      const fileId = initRes.fileId;
      const { url, method, headers } = initRes.upload;

      // 2. Perform XHR PUT
      const xhr = new XMLHttpRequest();
      uploadItem.xhr = xhr;

      xhr.upload.onprogress = (evt) => {
        if (evt.lengthComputable) {
          const pct = Math.round((evt.loaded / evt.total) * 100);
          setUploadingFiles((prev) =>
            prev.map((item) =>
              item.tempId === tempId ? { ...item, progress: pct } : item,
            ),
          );
        }
      };

      await new Promise((resolve, reject) => {
        xhr.open(method || "PUT", url);
        if (headers) {
          Object.entries(headers).forEach(([k, v]) => {
            xhr.setRequestHeader(k, v);
          });
        }
        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve();
          } else {
            reject(new Error(`Storage returned status ${xhr.status}`));
          }
        };
        xhr.onerror = () =>
          reject(new Error("Network error during file upload"));
        xhr.onabort = () => reject(new Error("Upload cancelled"));
        xhr.send(file);
      });

      // 3. Confirm file
      setUploadingFiles((prev) =>
        prev.map((item) =>
          item.tempId === tempId ? { ...item, status: "CONFIRMING" } : item,
        ),
      );

      const confirmRes = await confirmFileUpload(assignmentId, fileId);

      if (confirmRes.status === "READY") {
        setFiles((prev) => [
          ...prev,
          {
            id: fileId,
            originalFilename: file.name,
            mimeType: file.type,
            sizeBytes: file.size,
            status: "READY",
          },
        ]);
        setUploadingFiles((prev) =>
          prev.filter((item) => item.tempId !== tempId),
        );
      } else {
        setUploadingFiles((prev) =>
          prev.map((item) =>
            item.tempId === tempId
              ? {
                  ...item,
                  status: "FAILED",
                  error: confirmRes.failureReason || "File validation failed.",
                }
              : item,
          ),
        );
      }
    } catch (err) {
      setUploadingFiles((prev) =>
        prev.map((item) =>
          item.tempId === tempId
            ? {
                ...item,
                status: "FAILED",
                error: err.message || "Upload failed.",
              }
            : item,
        ),
      );
    }
  };

  const cancelUpload = (tempId) => {
    setUploadingFiles((prev) => {
      const item = prev.find((i) => i.tempId === tempId);
      if (item?.xhr) item.xhr.abort();
      return prev.filter((i) => i.tempId !== tempId);
    });
  };

  const handleDeleteFile = async (fileId) => {
    try {
      await deleteFile(assignmentId, fileId);
      setFiles((prev) => prev.filter((f) => f.id !== fileId));
    } catch (err) {
      alert("Failed to delete file: " + err.message);
    }
  };

  const handleDownloadFile = async (fileId, filename) => {
    try {
      const res = await getLearnerDownloadUrl(assignmentId, fileId);
      if (res?.url) {
        const link = document.createElement("a");
        link.href = res.url;
        link.download = filename || "file";
        link.target = "_blank";
        link.rel = "noreferrer noopener";
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      }
    } catch (err) {
      alert("Download failed: " + err.message);
    }
  };

  // Find list of unanswered questions for submit confirmation dialog
  const getUnansweredQuestions = () => {
    if (assignment.kind === "PROJECT") {
      const noText = !textAnswer.trim();
      const noFiles = files.length === 0;
      const list = [];
      if (assignment.allowText && noText) list.push("Text response");
      if (assignment.allowFiles && noFiles) list.push("File submission");
      return list;
    }

    const unanswered = [];
    (initialAttempt.questions || []).forEach((q, idx) => {
      const ans = answersMap[q.id];
      if (q.type === "MCQ_SINGLE" || q.type === "MCQ_MULTI") {
        if (!ans?.selectedOptionIds?.length) {
          unanswered.push(`Question ${idx + 1}`);
        }
      } else {
        if (!ans?.answerText?.trim()) {
          unanswered.push(`Question ${idx + 1}`);
        }
      }
    });
    return unanswered;
  };

  const questions = initialAttempt.questions || [];

  return (
    <div className="min-h-screen bg-surface-50 pb-24">
      {/* Aria Live Announcement Region */}
      <div aria-live="assertive" className="sr-only">
        {ariaLiveMessage}
      </div>

      {/* Sticky Header with Timer & Autosave Status */}
      <header className="sticky top-0 z-30 border-b border-surface-200 bg-white/95 backdrop-blur-md">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3 sm:px-6">
          <div>
            <span className="text-xs font-semibold uppercase tracking-wider text-ink-500">
              {assignment.kind === "PROJECT"
                ? "Project Attempt"
                : "Assessment Attempt"}
            </span>
            <h1 className="text-lg font-bold text-ink-900 truncate max-w-md">
              {assignment.title}
            </h1>
          </div>

          <div className="flex items-center gap-4">
            {/* Autosave Status Pill */}
            <div className="text-xs">
              {autosaveStatus === "saving" && (
                <span className="text-amber-600 font-medium">Saving...</span>
              )}
              {autosaveStatus === "saved" && (
                <span className="text-ink-500">Saved at {lastSavedTime}</span>
              )}
              {autosaveStatus === "error" && (
                <span
                  className="text-rose-600 font-medium"
                  title={saveErrorMessage}
                >
                  Save failed (retrying)
                </span>
              )}
            </div>

            {/* Timer Display (if timed) */}
            {initialAttempt.deadlineAt && (
              <div
                className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-mono font-bold ${
                  remainingSeconds !== null && remainingSeconds < 300
                    ? "bg-rose-100 text-rose-800 animate-pulse"
                    : "bg-surface-100 text-ink-900"
                }`}
              >
                <span>⏱</span>
                <span>{formatTimer(remainingSeconds)}</span>
              </div>
            )}

            {/* Submit Button */}
            <Button
              variant="primary"
              size="sm"
              disabled={isSubmitting}
              onClick={() => {
                setSubmitError("");
                setSubmitDialogOpen(true);
              }}
            >
              Submit
            </Button>
          </div>
        </div>
      </header>

      {/* Expired alert banner */}
      {expiredBanner && (
        <div className="mx-auto mt-4 max-w-5xl px-4">
          <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm font-semibold text-rose-900">
            Time's up; your saved answers were submitted.
          </div>
        </div>
      )}

      {/* Main Content Form */}
      <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
        {/* Instructions Card */}
        {assignment.instructions && (
          <div className="mb-8 rounded-xl border border-surface-200 bg-white p-6 shadow-sm">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-ink-500">
              Instructions
            </h2>
            <div className="mt-2 whitespace-pre-wrap text-sm text-ink-800 font-sans leading-relaxed">
              {assignment.instructions}
            </div>
          </div>
        )}

        {/* QUESTIONS KIND */}
        {assignment.kind === "QUESTIONS" ? (
          <div className="space-y-6">
            {questions.map((q, idx) => {
              const currentAnswer = answersMap[q.id] || {
                answerText: "",
                selectedOptionIds: [],
              };

              return (
                <div
                  key={q.id}
                  className="rounded-xl border border-surface-200 bg-white p-6 shadow-sm"
                >
                  {/* Question Header */}
                  <div className="flex items-center justify-between border-b border-surface-100 pb-3">
                    <span className="flex h-7 w-7 items-center justify-center rounded-full bg-brand-100 text-xs font-bold text-brand-800">
                      {idx + 1}
                    </span>
                    <span className="text-xs font-semibold text-ink-600">
                      {q.points} {q.points === 1 ? "point" : "points"}
                    </span>
                  </div>

                  {/* Prompt */}
                  <div className="mt-4 whitespace-pre-wrap text-sm font-medium text-ink-900">
                    {q.prompt}
                  </div>

                  {/* MCQ_SINGLE (Radios) */}
                  {q.type === "MCQ_SINGLE" && (
                    <div className="mt-4 space-y-2.5">
                      {(q.options || []).map((opt) => {
                        const isSelected =
                          currentAnswer.selectedOptionIds?.[0] === opt.id;
                        return (
                          <label
                            key={opt.id}
                            className={`flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 text-sm transition ${
                              isSelected
                                ? "border-brand-500 bg-brand-50/40 text-brand-950 ring-1 ring-brand-500"
                                : "border-surface-200 hover:bg-surface-50 text-ink-800"
                            }`}
                          >
                            <input
                              type="radio"
                              name={`question-${q.id}`}
                              checked={isSelected}
                              onChange={() =>
                                handleMcqSingleSelect(q.id, opt.id)
                              }
                              onBlur={executeAutosave}
                              className="mt-0.5 text-brand-600 focus:ring-brand-500"
                            />
                            <span className="flex-1 select-none">
                              {opt.text}
                            </span>
                          </label>
                        );
                      })}
                    </div>
                  )}

                  {/* MCQ_MULTI (Checkboxes) */}
                  {q.type === "MCQ_MULTI" && (
                    <div className="mt-4 space-y-2.5">
                      <p className="text-xs text-ink-500 italic">
                        Select all options that apply:
                      </p>
                      {(q.options || []).map((opt) => {
                        const isSelected =
                          currentAnswer.selectedOptionIds?.includes(opt.id) ||
                          false;
                        return (
                          <label
                            key={opt.id}
                            className={`flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 text-sm transition ${
                              isSelected
                                ? "border-brand-500 bg-brand-50/40 text-brand-950 ring-1 ring-brand-500"
                                : "border-surface-200 hover:bg-surface-50 text-ink-800"
                            }`}
                          >
                            <input
                              type="checkbox"
                              name={`question-${q.id}`}
                              checked={isSelected}
                              onChange={() =>
                                handleMcqMultiToggle(q.id, opt.id)
                              }
                              onBlur={executeAutosave}
                              className="mt-0.5 rounded text-brand-600 focus:ring-brand-500"
                            />
                            <span className="flex-1 select-none">
                              {opt.text}
                            </span>
                          </label>
                        );
                      })}
                    </div>
                  )}

                  {/* DESCRIPTIVE (Textarea) */}
                  {q.type === "DESCRIPTIVE" && (
                    <div className="mt-4">
                      <textarea
                        rows={5}
                        maxLength={20000}
                        value={currentAnswer.answerText}
                        onChange={(e) =>
                          handleTextAnswerChange(q.id, e.target.value)
                        }
                        onBlur={executeAutosave}
                        placeholder="Write your explanation here..."
                        className="block w-full rounded-lg border border-surface-300 p-3 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                      />
                      <span className="mt-1 block text-right text-xs text-ink-500">
                        {currentAnswer.answerText.length} / 20,000
                      </span>
                    </div>
                  )}

                  {/* CODE (Monospace textarea with standard Tab navigation) */}
                  {q.type === "CODE" && (
                    <div className="mt-4">
                      {q.codeLanguage && (
                        <div className="mb-2">
                          <span className="rounded bg-surface-200 px-2 py-0.5 text-xs font-mono font-semibold text-ink-700">
                            Language: {q.codeLanguage}
                          </span>
                        </div>
                      )}
                      <textarea
                        rows={8}
                        maxLength={50000}
                        value={currentAnswer.answerText}
                        onChange={(e) =>
                          handleTextAnswerChange(q.id, e.target.value)
                        }
                        onBlur={executeAutosave}
                        placeholder="// Enter your solution here..."
                        className="block w-full rounded-lg border border-surface-300 bg-surface-900 p-4 font-mono text-xs leading-relaxed text-surface-100 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                      />
                      <span className="mt-1 block text-right text-xs text-ink-500">
                        {currentAnswer.answerText.length} / 50,000
                      </span>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        ) : (
          /* PROJECT KIND */
          <div className="space-y-6">
            {/* Text Answer */}
            {assignment.allowText && (
              <div className="rounded-xl border border-surface-200 bg-white p-6 shadow-sm">
                <h2 className="text-base font-semibold text-ink-900">
                  Project Text Response
                </h2>
                <p className="mt-1 text-xs text-ink-500">
                  Provide your project overview, report, or summary below.
                </p>
                <textarea
                  rows={8}
                  maxLength={20000}
                  value={textAnswer}
                  onChange={(e) => {
                    isDirtyRef.current = true;
                    setTextAnswer(e.target.value);
                  }}
                  onBlur={executeAutosave}
                  placeholder="Type your project submission here..."
                  className="mt-4 block w-full rounded-lg border border-surface-300 p-3 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                />
                <span className="mt-1 block text-right text-xs text-ink-500">
                  {textAnswer.length} / 20,000
                </span>
              </div>
            )}

            {/* File Uploads */}
            {assignment.allowFiles && (
              <div className="rounded-xl border border-surface-200 bg-white p-6 shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <h2 className="text-base font-semibold text-ink-900">
                      Upload Files ({files.length} / 5)
                    </h2>
                    <p className="mt-1 text-xs text-ink-500">
                      Allowed formats: PDF, DOCX, PPTX, XLSX, TXT, CSV, PNG,
                      JPEG, ZIP (max 25 MB each).
                    </p>
                  </div>
                  <div>
                    <label
                      htmlFor="project-file-input"
                      className={`inline-flex cursor-pointer items-center rounded-lg px-4 py-2 text-xs font-semibold shadow-sm transition ${
                        files.length >= 5
                          ? "bg-surface-200 text-ink-400 cursor-not-allowed"
                          : "bg-brand-600 text-white hover:bg-brand-700"
                      }`}
                    >
                      + Add File
                    </label>
                    <input
                      id="project-file-input"
                      type="file"
                      multiple
                      accept=".pdf,.docx,.pptx,.xlsx,.txt,.csv,.png,.jpeg,.jpg,.zip"
                      disabled={files.length >= 5}
                      onChange={handleFileSelect}
                      className="sr-only"
                    />
                  </div>
                </div>

                {/* In-Flight Uploads Progress */}
                {uploadingFiles.length > 0 && (
                  <div className="mt-4 space-y-3">
                    {uploadingFiles.map((up) => (
                      <div
                        key={up.tempId}
                        className="rounded-lg border border-surface-200 bg-surface-50 p-3"
                      >
                        <div className="flex items-center justify-between text-xs">
                          <span className="font-medium text-ink-900">
                            {up.file.name}
                          </span>
                          <span className="text-ink-600">
                            {up.status === "UPLOADING"
                              ? `${up.progress}%`
                              : up.status === "CONFIRMING"
                                ? "Verifying file..."
                                : "Failed"}
                          </span>
                        </div>
                        {up.status === "UPLOADING" && (
                          <div className="mt-2 h-1.5 w-full rounded-full bg-surface-200 overflow-hidden">
                            <div
                              className="h-full bg-brand-600 transition-all duration-150"
                              style={{ width: `${up.progress}%` }}
                            />
                          </div>
                        )}
                        {up.status === "FAILED" && (
                          <div className="mt-2 flex items-center justify-between text-xs text-rose-600">
                            <span>{up.error}</span>
                            <div className="flex gap-2">
                              <button
                                type="button"
                                onClick={() => {
                                  cancelUpload(up.tempId);
                                  uploadSingleFile(up.file);
                                }}
                                className="font-semibold text-brand-600 hover:underline"
                              >
                                Retry
                              </button>
                              <button
                                type="button"
                                onClick={() => cancelUpload(up.tempId)}
                                className="font-semibold text-ink-600 hover:underline"
                              >
                                Dismiss
                              </button>
                            </div>
                          </div>
                        )}
                        {up.status === "UPLOADING" && (
                          <div className="mt-2 text-right">
                            <button
                              type="button"
                              onClick={() => cancelUpload(up.tempId)}
                              className="text-xs text-ink-500 hover:text-rose-600"
                            >
                              Cancel upload
                            </button>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}

                {/* Uploaded Files List */}
                <div className="mt-4">
                  {files.length > 0 ? (
                    <ul className="divide-y divide-surface-100 rounded-lg border border-surface-200 bg-white">
                      {files.map((file) => (
                        <li
                          key={file.id}
                          className="flex flex-wrap items-center justify-between gap-3 p-3.5"
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
                          <div className="flex items-center gap-2">
                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() =>
                                handleDownloadFile(
                                  file.id,
                                  file.originalFilename,
                                )
                              }
                            >
                              Download
                            </Button>
                            <Button
                              variant="danger"
                              size="sm"
                              onClick={() => handleDeleteFile(file.id)}
                            >
                              Delete
                            </Button>
                          </div>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="py-4 text-center text-xs italic text-ink-500">
                      No files uploaded yet.
                    </p>
                  )}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Bottom Submit Area */}
        <div className="mt-8 flex items-center justify-between border-t border-surface-200 pt-6">
          <div className="text-xs text-ink-500">
            {isDirtyRef.current
              ? "Unsaved changes pending..."
              : "All changes saved."}
          </div>
          <Button
            variant="primary"
            size="lg"
            disabled={isSubmitting}
            onClick={() => {
              setSubmitError("");
              setSubmitDialogOpen(true);
            }}
          >
            {isSubmitting ? "Submitting..." : "Submit Assignment"}
          </Button>
        </div>
      </main>

      {/* Submit Confirmation Dialog (checks unanswered questions) */}
      <Dialog.Root open={submitDialogOpen} onOpenChange={setSubmitDialogOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-40 bg-slate-950/50 backdrop-blur-sm" />
          <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,30rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl">
            <Dialog.Title className="text-lg font-bold text-ink-900">
              Submit Assignment
            </Dialog.Title>
            <div className="mt-2 text-sm text-ink-600">
              {submitError && (
                <div className="mb-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs text-rose-900 font-medium">
                  {submitError}
                </div>
              )}
              {getUnansweredQuestions().length > 0 ? (
                <div>
                  <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">
                    <strong className="block font-semibold">
                      Unanswered Items:
                    </strong>
                    <ul className="mt-1 list-disc pl-4">
                      {getUnansweredQuestions().map((item, idx) => (
                        <li key={idx}>{item}</li>
                      ))}
                    </ul>
                  </div>
                  <p className="mt-3">
                    You have unanswered items. Are you sure you want to finalize
                    your submission? Once submitted, you cannot change your
                    answers.
                  </p>
                </div>
              ) : (
                <p>
                  Are you ready to submit your assignment? Once submitted, you
                  cannot edit your responses.
                </p>
              )}
            </div>
            <div className="mt-6 flex justify-end gap-3">
              <Button
                variant="secondary"
                onClick={() => setSubmitDialogOpen(false)}
                disabled={isSubmitting}
              >
                Go Back & Review
              </Button>
              <Button
                variant="primary"
                onClick={handleFinalSubmit}
                disabled={isSubmitting}
              >
                {isSubmitting ? "Submitting..." : "Confirm & Submit"}
              </Button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>

      {/* 409 Version Conflict Dialog */}
      <Dialog.Root
        open={conflictDialogOpen}
        onOpenChange={setConflictDialogOpen}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-40 bg-slate-950/50 backdrop-blur-sm" />
          <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,28rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl">
            <Dialog.Title className="text-lg font-bold text-ink-900">
              Session Updated in Another Window
            </Dialog.Title>
            <Dialog.Description className="mt-2 text-sm text-ink-600">
              Your assignment attempt was updated in another browser tab or
              session. Please reload to continue with the latest saved version.
            </Dialog.Description>
            <div className="mt-6 flex justify-end">
              <Button
                variant="primary"
                onClick={() => {
                  setConflictDialogOpen(false);
                  onReloadAttempt();
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

// --------------------------------------------------------------------------
// 3. RESULT VIEW (SUBMITTED or GRADED)
// --------------------------------------------------------------------------
function ResultView({ assignment }) {
  const submissionQuery = useQuery({
    queryKey: ["learner", "submission", assignment.id],
    queryFn: () => getLearnerSubmission(assignment.id),
  });

  if (submissionQuery.isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Spinner label="Loading submission results..." />
      </div>
    );
  }

  if (submissionQuery.isError) {
    return (
      <div className="mx-auto max-w-4xl px-4 py-8">
        <ErrorState
          title="Submission Not Found"
          message={submissionQuery.error?.message}
          onRetry={() => submissionQuery.refetch()}
        />
      </div>
    );
  }

  const sub = submissionQuery.data;

  const handleDownloadFile = async (fileId, filename) => {
    try {
      const res = await getLearnerDownloadUrl(assignment.id, fileId);
      if (res?.url) {
        const link = document.createElement("a");
        link.href = res.url;
        link.download = filename || "file";
        link.target = "_blank";
        link.rel = "noreferrer noopener";
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      }
    } catch (err) {
      alert("Download failed: " + err.message);
    }
  };

  return (
    <div className="mx-auto max-w-4xl px-4 py-10 sm:px-6 lg:px-8">
      {/* Back button */}
      <div className="mb-6">
        <Link
          to={`/learning/courses/${assignment.courseId}/assignments`}
          className="text-sm font-medium text-brand-600 hover:text-brand-700"
        >
          ← Back to Course Assignments
        </Link>
      </div>

      {/* Main Result Card */}
      <div className="rounded-2xl border border-surface-200 bg-white p-6 shadow-sm sm:p-8">
        {/* Header */}
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-surface-100 pb-5">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-full bg-brand-50 px-3 py-1 text-xs font-semibold text-brand-700">
                {(sub.kind || assignment.kind) === "PROJECT"
                  ? "Project"
                  : "Assessment"}
              </span>
              {sub.status === "SUBMITTED" && (
                <span className="rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-800">
                  Submitted (Awaiting Grading)
                </span>
              )}
              {sub.status === "GRADED" && (
                <span className="rounded-full bg-emerald-100 px-3 py-1 text-xs font-semibold text-emerald-800">
                  Graded
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
            <h1 className="mt-2 text-2xl font-bold tracking-tight text-ink-900">
              {sub.assignmentTitle || assignment.title}
            </h1>
          </div>

          {/* Points Card */}
          {sub.status === "GRADED" && (
            <div className="text-right">
              <span className="text-xs uppercase tracking-wider text-ink-500">
                Final Score
              </span>
              <div className="text-2xl font-extrabold text-emerald-700">
                {sub.totalPoints !== null && sub.totalPoints !== undefined
                  ? sub.totalPoints
                  : sub.marks !== null && sub.marks !== undefined
                    ? sub.marks
                    : 0}{" "}
                / {sub.maxPoints} pts
              </div>
            </div>
          )}
        </div>

        {/* Status notice */}
        {sub.status === "SUBMITTED" && (
          <div className="mt-6 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
            <strong>Submission Received:</strong> Your work has been recorded
            and is currently in the queue for instructor review. You will see
            your score and feedback here once grading is complete.
          </div>
        )}

        {/* Overall Feedback (if graded) */}
        {sub.status === "GRADED" && sub.overallFeedback && (
          <div className="mt-6 rounded-xl border border-emerald-200 bg-emerald-50/70 p-5">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-emerald-800">
              Instructor Feedback
            </h2>
            <div className="mt-2 whitespace-pre-wrap text-sm text-emerald-950 font-sans leading-relaxed">
              {sub.overallFeedback}
            </div>
          </div>
        )}

        {/* Submission Details Body */}
        <div className="mt-8 space-y-6">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-ink-500">
            Your Submitted Responses
          </h2>

          {sub.kind === "PROJECT" ? (
            <div className="space-y-6">
              {sub.textAnswer && (
                <div className="rounded-xl border border-surface-200 bg-surface-50 p-5">
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-500">
                    Text Submission
                  </h3>
                  <div className="mt-2 whitespace-pre-wrap text-sm text-ink-900 font-sans leading-relaxed">
                    {sub.textAnswer}
                  </div>
                </div>
              )}

              {sub.files && sub.files.length > 0 && (
                <div className="rounded-xl border border-surface-200 bg-surface-50 p-5">
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-500">
                    Submitted Files
                  </h3>
                  <ul className="mt-3 divide-y divide-surface-200">
                    {sub.files.map((file) => (
                      <li
                        key={file.id}
                        className="flex flex-wrap items-center justify-between gap-3 py-3"
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
                          Download
                        </Button>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          ) : (
            /* QUESTIONS */
            <div className="space-y-6">
              {(sub.questions || []).map((q, idx) => {
                const qId = q.questionId ?? q.id;
                const ans = (sub.answers || []).find(
                  (a) => a.questionId === qId || a.questionId === q.id,
                );
                const userSelected =
                  q.selectedOptionIds ?? ans?.selectedOptionIds ?? [];
                const pointsEarned =
                  q.pointsAwarded !== null && q.pointsAwarded !== undefined
                    ? q.pointsAwarded
                    : ans?.points !== null && ans?.points !== undefined
                      ? ans.points
                      : (ans?.autoPoints ?? 0);
                const qMaxPoints = q.maxPoints ?? q.points ?? 0;

                return (
                  <div
                    key={qId}
                    className="rounded-xl border border-surface-200 bg-white p-5 shadow-sm"
                  >
                    {/* Header */}
                    <div className="flex items-center justify-between border-b border-surface-100 pb-2">
                      <span className="flex h-6 w-6 items-center justify-center rounded-full bg-brand-100 text-xs font-bold text-brand-800">
                        {idx + 1}
                      </span>
                      {sub.status === "GRADED" && (
                        <span className="text-xs font-bold text-emerald-700">
                          {pointsEarned} / {qMaxPoints} pts
                        </span>
                      )}
                    </div>

                    {/* Prompt */}
                    <div className="mt-3 whitespace-pre-wrap text-sm font-medium text-ink-900">
                      {q.prompt}
                    </div>

                    {/* MCQ Options */}
                    {(q.type === "MCQ_SINGLE" || q.type === "MCQ_MULTI") && (
                      <div className="mt-3 space-y-2">
                        {(q.options || []).map((opt) => {
                          const isSelected = userSelected.includes(opt.id);
                          const isCorrect =
                            opt.isCorrect === true ||
                            opt.correct === true ||
                            (q.correctOptionIds &&
                              q.correctOptionIds.includes(opt.id));
                          const showCorrect =
                            (opt.isCorrect !== null &&
                              opt.isCorrect !== undefined) ||
                            (opt.correct !== null &&
                              opt.correct !== undefined) ||
                            (q.correctOptionIds !== null &&
                              q.correctOptionIds !== undefined);

                          return (
                            <div
                              key={opt.id}
                              className={`flex items-start gap-3 rounded-lg border p-3 text-sm ${
                                isSelected && showCorrect && isCorrect
                                  ? "border-emerald-300 bg-emerald-50 text-emerald-900"
                                  : isSelected && showCorrect && !isCorrect
                                    ? "border-rose-300 bg-rose-50 text-rose-900"
                                    : isSelected
                                      ? "border-brand-400 bg-brand-50/50 text-brand-950"
                                      : showCorrect && isCorrect
                                        ? "border-emerald-200 bg-white text-emerald-800 border-dashed"
                                        : "border-surface-200 bg-white text-ink-700"
                              }`}
                            >
                              <span className="mt-0.5 text-xs font-bold">
                                {isSelected ? "●" : "○"}
                              </span>
                              <div className="flex-1">
                                <span>{opt.text}</span>
                                <div className="mt-1 flex items-center gap-2 text-xs">
                                  {isSelected && (
                                    <span className="font-semibold text-ink-800">
                                      [Your answer]
                                    </span>
                                  )}
                                  {showCorrect && isCorrect && (
                                    <span className="font-semibold text-emerald-700">
                                      ✓ Correct answer
                                    </span>
                                  )}
                                </div>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}

                    {/* Descriptive */}
                    {q.type === "DESCRIPTIVE" && (
                      <div className="mt-3 rounded-lg border border-surface-200 bg-surface-50 p-4">
                        <span className="text-xs font-semibold uppercase tracking-wider text-ink-500">
                          Your Answer:
                        </span>
                        <div className="mt-1 whitespace-pre-wrap text-sm text-ink-800 font-sans">
                          {q.answerText || ans?.answerText || (
                            <span className="italic text-ink-400">
                              No response given.
                            </span>
                          )}
                        </div>
                      </div>
                    )}

                    {/* CODE: Displayed in monospace <pre>, never executed */}
                    {q.type === "CODE" && (
                      <div className="mt-3">
                        {q.codeLanguage && (
                          <span className="mb-1 inline-block rounded bg-surface-200 px-2 py-0.5 text-xs font-mono text-ink-700">
                            {q.codeLanguage}
                          </span>
                        )}
                        <pre className="overflow-x-auto rounded-lg bg-surface-900 p-4 font-mono text-xs text-surface-100">
                          {q.answerText ||
                            ans?.answerText ||
                            "// No code answer submitted."}
                        </pre>
                      </div>
                    )}

                    {/* Question Feedback if graded */}
                    {sub.status === "GRADED" && (q.feedback || ans?.feedback) && (
                      <div className="mt-3 rounded-lg border border-surface-200 bg-surface-50 p-3 text-xs text-ink-700">
                        <strong className="text-ink-900">Feedback:</strong>{" "}
                        {q.feedback || ans?.feedback}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
