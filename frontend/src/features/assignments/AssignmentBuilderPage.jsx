import { useState, useEffect } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { z } from "zod";

function zodResolver(schema) {
  return (values) => {
    const result = schema.safeParse(values);
    if (result.success) {
      return { values: result.data, errors: {} };
    }
    const errors = {};
    for (const issue of result.error.issues) {
      const field = issue.path[0] || "root";
      if (!errors[field]) {
        errors[field] = { type: "validate", message: issue.message };
      }
    }
    return { values: {}, errors };
  };
}
import {
  getInstructorAssignment,
  updateAssignment,
  replaceQuestions,
  publishAssignment,
  unpublishAssignment,
  deleteAssignment,
} from "./assignmentApi";
import { apiRequest } from "../../shared/api/client";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { ErrorState } from "../../shared/components/ErrorState";
import * as Dialog from "@radix-ui/react-dialog";

const assignmentSchema = z.object({
  title: z.string().min(1, "Title is required").max(255, "Title max 255 chars"),
  instructions: z
    .string()
    .max(10000, "Instructions max 10000 chars")
    .optional(),
  moduleId: z.string().optional(),
  lessonId: z.string().optional(),
  opensAt: z.string().optional(),
  dueAt: z.string().optional(),
  timeLimitMinutes: z
    .union([z.number().min(1).max(480), z.literal(""), z.null()])
    .optional(),
  allowLate: z.boolean().default(true),
  allowText: z.boolean().default(true),
  allowFiles: z.boolean().default(true),
  showCorrectAnswers: z.boolean().default(false),
  maxPoints: z
    .number()
    .min(0.01, "Max points must be greater than zero")
    .optional(),
});

function toLocalDatetimeInput(utcIso) {
  if (!utcIso) return "";
  const d = new Date(utcIso);
  if (isNaN(d.getTime())) return "";
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function toUtcIso(localDatetimeStr) {
  if (!localDatetimeStr) return null;
  const d = new Date(localDatetimeStr);
  return isNaN(d.getTime()) ? null : d.toISOString();
}

export function AssignmentBuilderPage() {
  const { courseId, assignmentId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [publishErrors, setPublishErrors] = useState([]);
  const [statusMessage, setStatusMessage] = useState("");

  // Assignment details query
  const assignmentQuery = useQuery({
    queryKey: ["instructor", "assignment", assignmentId],
    queryFn: () => getInstructorAssignment(assignmentId),
  });

  // Course outline for placement
  const outlineQuery = useQuery({
    queryKey: ["instructor", "course", courseId, "outline"],
    queryFn: () => apiRequest(`/instructor/courses/${courseId}/outline`),
  });

  const assignment = assignmentQuery.data;
  const isLocked = Boolean(assignment && assignment.attemptCount > 0);

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(assignmentSchema),
    defaultValues: {
      title: "",
      instructions: "",
      moduleId: "",
      lessonId: "",
      opensAt: "",
      dueAt: "",
      timeLimitMinutes: "",
      allowLate: true,
      allowText: true,
      allowFiles: true,
      showCorrectAnswers: false,
      maxPoints: 100,
    },
  });

  // Local state for questions when kind === QUESTIONS
  const [questions, setQuestions] = useState([]);

  useEffect(() => {
    if (assignment) {
      reset({
        title: assignment.title || "",
        instructions: assignment.instructions || "",
        moduleId: assignment.moduleId ? String(assignment.moduleId) : "",
        lessonId: assignment.lessonId ? String(assignment.lessonId) : "",
        opensAt: toLocalDatetimeInput(assignment.opensAt),
        dueAt: toLocalDatetimeInput(assignment.dueAt),
        timeLimitMinutes: assignment.timeLimitMinutes || "",
        allowLate: assignment.allowLate ?? true,
        allowText: assignment.allowText ?? true,
        allowFiles: assignment.allowFiles ?? true,
        showCorrectAnswers: assignment.showCorrectAnswers ?? false,
        maxPoints: assignment.maxPoints ? Number(assignment.maxPoints) : 100,
      });

      if (assignment.questions) {
        setQuestions(
          assignment.questions.map((q) => ({
            id: q.id,
            type: q.type || "MCQ_SINGLE",
            prompt: q.prompt || "",
            points: Number(q.points) || 5,
            codeLanguage: q.codeLanguage || "",
            options: (q.options || []).map((o, idx) => ({
              id: o.id || `opt_${idx + 1}`,
              text: o.text || "",
              correct: Boolean(o.correct),
            })),
          })),
        );
      }
    }
  }, [assignment, reset]);

  // Save Settings Mutation
  const saveSettingsMutation = useMutation({
    mutationFn: (data) => {
      const payload = {
        version: assignment.version,
        title: data.title,
        instructions: data.instructions,
        moduleId: data.moduleId ? Number(data.moduleId) : null,
        lessonId: data.lessonId ? Number(data.lessonId) : null,
        opensAt: toUtcIso(data.opensAt),
        dueAt: toUtcIso(data.dueAt),
        timeLimitMinutes:
          data.timeLimitMinutes === "" || data.timeLimitMinutes === null
            ? null
            : Number(data.timeLimitMinutes),
        allowLate: data.allowLate,
        allowText: data.allowText,
        allowFiles: data.allowFiles,
        showCorrectAnswers: data.showCorrectAnswers,
      };
      if (assignment.kind === "PROJECT") {
        payload.maxPoints = Number(data.maxPoints);
      }
      return updateAssignment(assignmentId, payload);
    },
    onSuccess: (updated) => {
      queryClient.setQueryData(
        ["instructor", "assignment", assignmentId],
        updated,
      );
      setStatusMessage("Settings saved successfully.");
      setPublishErrors([]);
      setTimeout(() => setStatusMessage(""), 3000);
    },
    onError: (err) => {
      if (err.fieldErrors && err.fieldErrors.length > 0) {
        err.fieldErrors.forEach((fe) => {
          setError(fe.field, { message: fe.message });
        });
      } else {
        setPublishErrors([err.message || "Failed to save settings."]);
      }
    },
  });

  // Save Questions Mutation
  const saveQuestionsMutation = useMutation({
    mutationFn: (qList) => {
      const payload = qList.map((q) => ({
        id: q.id,
        type: q.type,
        prompt: q.prompt,
        points: Number(q.points) || 1,
        codeLanguage:
          q.type === "CODE" ? q.codeLanguage || undefined : undefined,
        options:
          q.type === "MCQ_SINGLE" || q.type === "MCQ_MULTI"
            ? q.options.map((o) => ({
                id: o.id,
                text: o.text,
                correct: Boolean(o.correct),
              }))
            : [],
      }));
      return replaceQuestions(assignmentId, payload);
    },
    onSuccess: (updated) => {
      queryClient.setQueryData(
        ["instructor", "assignment", assignmentId],
        updated,
      );
      setStatusMessage("Questions saved successfully.");
      setPublishErrors([]);
      setTimeout(() => setStatusMessage(""), 3000);
    },
    onError: (err) => {
      if (err.fieldErrors && err.fieldErrors.length > 0) {
        setPublishErrors(
          err.fieldErrors.map((fe) => `${fe.field}: ${fe.message}`),
        );
      } else {
        setPublishErrors([err.message || "Failed to save questions."]);
      }
    },
  });

  // Publish / Unpublish Mutation
  const publishMutation = useMutation({
    mutationFn: () => publishAssignment(assignmentId),
    onSuccess: (updated) => {
      queryClient.setQueryData(
        ["instructor", "assignment", assignmentId],
        updated,
      );
      setStatusMessage("Assignment published successfully!");
      setPublishErrors([]);
      setTimeout(() => setStatusMessage(""), 3000);
    },
    onError: (err) => {
      if (err.fieldErrors && err.fieldErrors.length > 0) {
        setPublishErrors(
          err.fieldErrors.map((fe) => `${fe.field}: ${fe.message}`),
        );
      } else {
        setPublishErrors([err.message || "Failed to publish assignment."]);
      }
    },
  });

  const unpublishMutation = useMutation({
    mutationFn: () => unpublishAssignment(assignmentId),
    onSuccess: (updated) => {
      queryClient.setQueryData(
        ["instructor", "assignment", assignmentId],
        updated,
      );
      setStatusMessage("Assignment unpublished.");
      setPublishErrors([]);
      setTimeout(() => setStatusMessage(""), 3000);
    },
    onError: (err) => {
      setPublishErrors([err.message || "Failed to unpublish assignment."]);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteAssignment(assignmentId),
    onSuccess: () => {
      navigate(`/instructor/courses/${courseId}/assignments`);
    },
    onError: (err) => {
      setPublishErrors([err.message || "Failed to delete assignment."]);
      setDeleteOpen(false);
    },
  });

  // Helper question manipulation
  const addQuestion = (type = "MCQ_SINGLE") => {
    if (isLocked) return;
    const resolvedType = type === "MCQ_MULTI" ? "MCQ_MULTI" : "MCQ_SINGLE";
    const newQ = {
      type: resolvedType,
      prompt: "New question prompt",
      points: 5,
      options: [
        { id: "opt_1", text: "Option A", correct: true },
        { id: "opt_2", text: "Option B", correct: false },
      ],
    };
    setQuestions([...questions, newQ]);
  };

  const removeQuestion = (idx) => {
    if (isLocked) return;
    setQuestions(questions.filter((_, i) => i !== idx));
  };

  const moveQuestion = (idx, direction) => {
    if (isLocked) return;
    const targetIdx = idx + direction;
    if (targetIdx < 0 || targetIdx >= questions.length) return;
    const next = [...questions];
    const temp = next[idx];
    next[idx] = next[targetIdx];
    next[targetIdx] = temp;
    setQuestions(next);
  };

  const updateQuestionField = (idx, field, value) => {
    if (isLocked) return;
    setQuestions((prev) =>
      prev.map((q, i) => {
        if (i !== idx) return q;
        const updated = { ...q, [field]: value };
        if (field === "type") {
          if (value === "MCQ_SINGLE" || value === "MCQ_MULTI") {
            if (!updated.options || updated.options.length < 2) {
              updated.options = [
                { id: "opt_1", text: "Option 1", correct: true },
                { id: "opt_2", text: "Option 2", correct: false },
              ];
            }
          }
        }
        return updated;
      }),
    );
  };

  const addOption = (qIdx) => {
    if (isLocked) return;
    setQuestions((prev) =>
      prev.map((q, i) => {
        if (i !== qIdx) return q;
        const newOptId = `opt_${(q.options?.length || 0) + 1}_${Date.now()}`;
        return {
          ...q,
          options: [
            ...(q.options || []),
            { id: newOptId, text: "", correct: false },
          ],
        };
      }),
    );
  };

  const removeOption = (qIdx, optIdx) => {
    if (isLocked) return;
    setQuestions((prev) =>
      prev.map((q, i) => {
        if (i !== qIdx) return q;
        return {
          ...q,
          options: q.options.filter((_, oi) => oi !== optIdx),
        };
      }),
    );
  };

  const updateOption = (qIdx, optIdx, field, val) => {
    if (isLocked) return;
    setQuestions((prev) =>
      prev.map((q, i) => {
        if (i !== qIdx) return q;
        const opts = q.options.map((o, oi) => {
          if (oi !== optIdx) {
            // If MCQ_SINGLE and marking this one correct, uncheck others
            if (
              field === "correct" &&
              val === true &&
              q.type === "MCQ_SINGLE"
            ) {
              return { ...o, correct: false };
            }
            return o;
          }
          return { ...o, [field]: val };
        });
        return { ...q, options: opts };
      }),
    );
  };

  if (assignmentQuery.isLoading) {
    return <Spinner label="Loading assignment builder..." />;
  }

  if (assignmentQuery.isError) {
    return (
      <ErrorState
        title="Failed to load assignment"
        message={assignmentQuery.error?.message}
        onRetry={() => assignmentQuery.refetch()}
      />
    );
  }

  const modules = outlineQuery.data?.modules || [];
  const selectedModuleId = watch("moduleId");
  const currentModule = modules.find(
    (m) => String(m.id) === String(selectedModuleId),
  );
  const availableLessons = currentModule?.lessons || [];

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      {/* Top Header */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4 border-b border-surface-200 pb-5">
        <div>
          <Link
            to={`/instructor/courses/${courseId}/assignments`}
            className="text-sm font-medium text-brand-600 hover:text-brand-700"
          >
            ← Back to Assignments List
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-bold text-ink-900">
              {assignment.title}
            </h1>
            <span
              className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                assignment.status === "PUBLISHED"
                  ? "bg-emerald-100 text-emerald-800"
                  : assignment.status === "UNPUBLISHED"
                    ? "bg-zinc-100 text-zinc-700"
                    : "bg-amber-100 text-amber-800"
              }`}
            >
              {assignment.status}
            </span>
            <span className="text-xs font-medium uppercase tracking-wider text-ink-500">
              {assignment.kind}
            </span>
          </div>
        </div>

        {/* Action Controls */}
        <div className="flex flex-wrap items-center gap-3">
          {assignment.status === "PUBLISHED" ? (
            <Button
              type="button"
              variant="outline"
              disabled={unpublishMutation.isPending}
              onClick={() => unpublishMutation.mutate()}
            >
              {unpublishMutation.isPending ? "Unpublishing..." : "Unpublish"}
            </Button>
          ) : (
            <Button
              type="button"
              variant="primary"
              disabled={publishMutation.isPending}
              onClick={() => publishMutation.mutate()}
            >
              {publishMutation.isPending
                ? "Publishing..."
                : "Publish Assignment"}
            </Button>
          )}

          <Button
            type="button"
            variant="danger"
            disabled={isLocked}
            onClick={() => setDeleteOpen(true)}
          >
            Delete
          </Button>
        </div>
      </div>

      {/* Locked Banner */}
      {isLocked && (
        <div
          role="region"
          aria-label="Assignment locked"
          className="mb-6 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900 shadow-xs"
        >
          <span className="font-semibold">Assignment Locked:</span> Learners
          have started attempts. Questions, question points, and project scoring
          criteria are locked to prevent grading discrepancies. Title,
          instructions, and due dates can still be updated.
        </div>
      )}

      {/* Status or Error Notifications */}
      {statusMessage && (
        <div
          role="status"
          className="mb-6 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800"
        >
          {statusMessage}
        </div>
      )}

      {publishErrors.length > 0 && (
        <div
          role="alert"
          className="mb-6 rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800"
        >
          <div className="font-semibold">
            Please fix the following validation errors:
          </div>
          <ul className="mt-2 list-inside list-disc space-y-1">
            {publishErrors.map((err, idx) => (
              <li key={idx}>{err}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
        {/* Left Column: Assignment Settings */}
        <div className="lg:col-span-1">
          <form
            onSubmit={handleSubmit((data) => saveSettingsMutation.mutate(data))}
            className="rounded-xl border border-surface-200 bg-white p-5 shadow-xs"
          >
            <h2 className="text-lg font-bold text-ink-900">
              Assignment Settings
            </h2>

            <div className="mt-4 space-y-4">
              <div>
                <label
                  htmlFor="title"
                  className="block text-xs font-semibold text-ink-800"
                >
                  Title
                </label>
                <input
                  id="title"
                  type="text"
                  {...register("title")}
                  className="mt-1 w-full rounded-lg border border-surface-300 px-3 py-2 text-sm focus:border-brand-600 focus:outline-hidden"
                />
                {errors.title && (
                  <p className="mt-1 text-xs text-rose-600">
                    {errors.title.message}
                  </p>
                )}
              </div>

              <div>
                <label
                  htmlFor="instructions"
                  className="block text-xs font-semibold text-ink-800"
                >
                  Instructions
                </label>
                <textarea
                  id="instructions"
                  rows={4}
                  {...register("instructions")}
                  className="mt-1 w-full rounded-lg border border-surface-300 px-3 py-2 text-sm focus:border-brand-600 focus:outline-hidden"
                />
                {errors.instructions && (
                  <p className="mt-1 text-xs text-rose-600">
                    {errors.instructions.message}
                  </p>
                )}
              </div>

              {/* Placement: Module / Lesson */}
              <div>
                <label
                  htmlFor="moduleId"
                  className="block text-xs font-semibold text-ink-800"
                >
                  Module Placement (Optional)
                </label>
                <select
                  id="moduleId"
                  {...register("moduleId")}
                  className="mt-1 w-full rounded-lg border border-surface-300 px-3 py-2 text-sm focus:border-brand-600 focus:outline-hidden"
                >
                  <option value="">Course Level (None)</option>
                  {modules.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.title}
                    </option>
                  ))}
                </select>
              </div>

              {availableLessons.length > 0 && (
                <div>
                  <label
                    htmlFor="lessonId"
                    className="block text-xs font-semibold text-ink-800"
                  >
                    Lesson Placement (Optional)
                  </label>
                  <select
                    id="lessonId"
                    {...register("lessonId")}
                    className="mt-1 w-full rounded-lg border border-surface-300 px-3 py-2 text-sm focus:border-brand-600 focus:outline-hidden"
                  >
                    <option value="">Module Level (None)</option>
                    {availableLessons.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.title}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {/* Timing */}
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div>
                  <label
                    htmlFor="opensAt"
                    className="block text-xs font-semibold text-ink-800"
                  >
                    Opens At
                  </label>
                  <input
                    id="opensAt"
                    type="datetime-local"
                    {...register("opensAt")}
                    className="mt-1 w-full rounded-lg border border-surface-300 px-2 py-1.5 text-xs focus:border-brand-600 focus:outline-hidden"
                  />
                  {errors.opensAt && (
                    <p className="mt-1 text-xs text-rose-600">
                      {errors.opensAt.message}
                    </p>
                  )}
                </div>
                <div>
                  <label
                    htmlFor="dueAt"
                    className="block text-xs font-semibold text-ink-800"
                  >
                    Due At
                  </label>
                  <input
                    id="dueAt"
                    type="datetime-local"
                    {...register("dueAt")}
                    className="mt-1 w-full rounded-lg border border-surface-300 px-2 py-1.5 text-xs focus:border-brand-600 focus:outline-hidden"
                  />
                  {errors.dueAt && (
                    <p className="mt-1 text-xs text-rose-600">
                      {errors.dueAt.message}
                    </p>
                  )}
                </div>
              </div>

              <div>
                <label
                  htmlFor="timeLimitMinutes"
                  className="block text-xs font-semibold text-ink-800"
                >
                  Time Limit (Minutes, optional)
                </label>
                <input
                  id="timeLimitMinutes"
                  type="number"
                  min="1"
                  max="480"
                  {...register("timeLimitMinutes", { valueAsNumber: true })}
                  placeholder="e.g. 60 (blank for untimed)"
                  className="mt-1 w-full rounded-lg border border-surface-300 px-3 py-2 text-sm focus:border-brand-600 focus:outline-hidden"
                />
                {errors.timeLimitMinutes && (
                  <p className="mt-1 text-xs text-rose-600">
                    {errors.timeLimitMinutes.message}
                  </p>
                )}
              </div>

              {/* Toggles */}
              <div className="space-y-2 pt-2">
                <label className="flex items-center gap-2 text-xs font-medium text-ink-800">
                  <input
                    type="checkbox"
                    {...register("allowLate")}
                    className="h-4 w-4 rounded border-surface-300 text-brand-600 focus:ring-brand-500"
                  />
                  Allow Late Submissions
                </label>

                {assignment.kind === "PROJECT" && (
                  <>
                    <label className="flex items-center gap-2 text-xs font-medium text-ink-800">
                      <input
                        type="checkbox"
                        {...register("allowText")}
                        className="h-4 w-4 rounded border-surface-300 text-brand-600 focus:ring-brand-500"
                      />
                      Allow Text Submissions
                    </label>

                    <label className="flex items-center gap-2 text-xs font-medium text-ink-800">
                      <input
                        type="checkbox"
                        {...register("allowFiles")}
                        className="h-4 w-4 rounded border-surface-300 text-brand-600 focus:ring-brand-500"
                      />
                      Allow File Uploads
                    </label>

                    <div>
                      <label
                        htmlFor="maxPoints"
                        className="block text-xs font-semibold text-ink-800"
                      >
                        Max Points
                      </label>
                      <input
                        id="maxPoints"
                        type="number"
                        disabled={isLocked}
                        {...register("maxPoints", { valueAsNumber: true })}
                        className="mt-1 w-full rounded-lg border border-surface-300 px-3 py-2 text-sm focus:border-brand-600 focus:outline-hidden disabled:bg-surface-100"
                      />
                    </div>
                  </>
                )}

                {assignment.kind === "QUESTIONS" && (
                  <label className="flex items-center gap-2 text-xs font-medium text-ink-800">
                    <input
                      type="checkbox"
                      {...register("showCorrectAnswers")}
                      className="h-4 w-4 rounded border-surface-300 text-brand-600 focus:ring-brand-500"
                    />
                    Show Correct Answers After Grading
                  </label>
                )}
              </div>

              <div className="pt-4">
                <Button
                  type="submit"
                  variant="primary"
                  className="w-full"
                  disabled={saveSettingsMutation.isPending}
                >
                  {saveSettingsMutation.isPending
                    ? "Saving..."
                    : "Save Settings"}
                </Button>
              </div>
            </div>
          </form>
        </div>

        {/* Right Column: Questions Editor (for QUESTIONS) or Project Info (for PROJECT) */}
        <div className="lg:col-span-2">
          {assignment.kind === "PROJECT" ? (
            <div className="rounded-xl border border-surface-200 bg-white p-6 shadow-xs">
              <h2 className="text-lg font-bold text-ink-900">
                Project Assessment Criteria
              </h2>
              <p className="mt-2 text-sm text-ink-600">
                Project assignments allow learners to submit text reports and
                upload files up to 25 MB each. Submissions are reviewed and
                graded manually by the instructor in the grading screen.
              </p>

              <div className="mt-6 rounded-lg bg-surface-50 p-4">
                <h3 className="text-sm font-semibold text-ink-900">
                  Grading Specification
                </h3>
                <ul className="mt-2 list-inside list-disc space-y-1 text-xs text-ink-700">
                  <li>Total Points: {watch("maxPoints") || 100}</li>
                  <li>
                    Text Submissions:{" "}
                    {watch("allowText") ? "Allowed" : "Disabled"}
                  </li>
                  <li>
                    File Submissions:{" "}
                    {watch("allowFiles") ? "Allowed" : "Disabled"}
                  </li>
                  <li>
                    Late Policy:{" "}
                    {watch("allowLate")
                      ? "Late submissions accepted"
                      : "Strict deadline"}
                  </li>
                </ul>
              </div>

              <div className="mt-6">
                <Link
                  to={`/instructor/courses/${courseId}/submissions?assignmentId=${assignmentId}`}
                  className="inline-flex items-center text-sm font-semibold text-brand-600 hover:text-brand-700"
                >
                  View Submissions for this Project →
                </Link>
              </div>
            </div>
          ) : (
            <div className="space-y-6">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h2 className="text-lg font-bold text-ink-900">
                    MCQ Questions ({questions.length})
                  </h2>
                  <p className="text-xs text-ink-500">
                    Total points:{" "}
                    {questions.reduce(
                      (sum, q) => sum + (Number(q.points) || 0),
                      0,
                    )}{" "}
                    · Auto-graded upon submission
                  </p>
                </div>

                {!isLocked && (
                  <div className="flex items-center gap-2">
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => addQuestion("MCQ_SINGLE")}
                    >
                      + Single Choice (MCQ)
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => addQuestion("MCQ_MULTI")}
                    >
                      + Multi Choice (MCQ)
                    </Button>
                  </div>
                )}
              </div>

              {questions.length === 0 ? (
                <div className="rounded-xl border border-dashed border-surface-300 p-8 text-center">
                  <p className="text-sm text-ink-500">
                    No questions added yet.
                  </p>
                  {!isLocked && (
                    <Button
                      type="button"
                      variant="primary"
                      className="mt-3"
                      onClick={() => addQuestion("MCQ_SINGLE")}
                    >
                      Add First Question
                    </Button>
                  )}
                </div>
              ) : (
                <div className="space-y-4">
                  {questions.map((q, qIdx) => (
                    <div
                      key={q.id || qIdx}
                      className="rounded-xl border border-surface-200 bg-white p-5 shadow-xs"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-surface-100 pb-3">
                        <div className="flex items-center gap-2">
                          <span className="flex h-6 w-6 items-center justify-center rounded-full bg-brand-50 text-xs font-bold text-brand-700">
                            {qIdx + 1}
                          </span>
                          <span className="font-semibold text-xs text-ink-700">
                            Question {qIdx + 1}
                          </span>
                          <select
                            value={q.type}
                            disabled={isLocked}
                            onChange={(e) =>
                              updateQuestionField(qIdx, "type", e.target.value)
                            }
                            className="rounded-md border border-surface-300 px-2 py-1 text-xs font-semibold text-ink-800 disabled:bg-surface-100"
                          >
                            <option value="MCQ_SINGLE">
                              MCQ (Single Choice)
                            </option>
                            <option value="MCQ_MULTI">
                              MCQ (Multi Choice)
                            </option>
                            {q.type !== "MCQ_SINGLE" && q.type !== "MCQ_MULTI" && (
                              <option value={q.type} disabled>
                                {q.type} (Unsupported)
                              </option>
                            )}
                          </select>
                        </div>

                        <div className="flex items-center gap-2">
                          <label className="flex items-center gap-1 text-xs font-medium text-ink-700">
                            Points:
                            <input
                              type="number"
                              min="0.5"
                              step="0.5"
                              value={q.points}
                              disabled={isLocked}
                              onChange={(e) =>
                                updateQuestionField(
                                  qIdx,
                                  "points",
                                  e.target.value,
                                )
                              }
                              className="w-16 rounded border border-surface-300 px-2 py-0.5 text-xs disabled:bg-surface-100"
                            />
                          </label>

                          {!isLocked && (
                            <>
                              <button
                                type="button"
                                disabled={qIdx === 0}
                                onClick={() => moveQuestion(qIdx, -1)}
                                aria-label={`Move question ${qIdx + 1} up`}
                                className="rounded p-1 text-xs text-ink-600 hover:bg-surface-100 disabled:opacity-30"
                              >
                                ↑
                              </button>
                              <button
                                type="button"
                                disabled={qIdx === questions.length - 1}
                                onClick={() => moveQuestion(qIdx, 1)}
                                aria-label={`Move question ${qIdx + 1} down`}
                                className="rounded p-1 text-xs text-ink-600 hover:bg-surface-100 disabled:opacity-30"
                              >
                                ↓
                              </button>
                              <button
                                type="button"
                                onClick={() => removeQuestion(qIdx)}
                                aria-label={`Remove question ${qIdx + 1}`}
                                className="rounded p-1 text-xs text-rose-600 hover:bg-rose-50"
                              >
                                ✕
                              </button>
                            </>
                          )}
                        </div>
                      </div>

                      {/* Prompt */}
                      <div className="mt-3">
                        <label className="block text-xs font-medium text-ink-600">
                          Prompt / Question Text
                        </label>
                        <textarea
                          rows={2}
                          value={q.prompt}
                          disabled={isLocked}
                          onChange={(e) =>
                            updateQuestionField(qIdx, "prompt", e.target.value)
                          }
                          placeholder="Enter question prompt..."
                          className="mt-1 w-full rounded-lg border border-surface-300 px-3 py-1.5 text-sm focus:border-brand-600 focus:outline-hidden disabled:bg-surface-100"
                        />
                      </div>

                      {/* Code Language for CODE type */}
                      {q.type === "CODE" && (
                        <div className="mt-3">
                          <label className="block text-xs font-medium text-ink-600">
                            Programming Language
                          </label>
                          <input
                            type="text"
                            value={q.codeLanguage || ""}
                            disabled={isLocked}
                            onChange={(e) =>
                              updateQuestionField(
                                qIdx,
                                "codeLanguage",
                                e.target.value,
                              )
                            }
                            placeholder="e.g. python, java, javascript"
                            className="mt-1 w-48 rounded border border-surface-300 px-2 py-1 text-xs disabled:bg-surface-100"
                          />
                        </div>
                      )}

                      {/* Options for MCQ */}
                      {(q.type === "MCQ_SINGLE" || q.type === "MCQ_MULTI") && (
                        <div className="mt-4 space-y-2 border-t border-surface-100 pt-3">
                          <div className="flex items-center justify-between">
                            <span className="text-xs font-semibold text-ink-700">
                              Options (Check correct option
                              {q.type === "MCQ_MULTI" ? "s" : ""}):
                            </span>
                            {!isLocked && (
                              <button
                                type="button"
                                onClick={() => addOption(qIdx)}
                                className="text-xs font-medium text-brand-600 hover:text-brand-700"
                              >
                                + Add Option
                              </button>
                            )}
                          </div>

                          {q.options?.map((opt, optIdx) => (
                            <div
                              key={opt.id || optIdx}
                              className="flex items-center gap-3"
                            >
                              <input
                                type={
                                  q.type === "MCQ_SINGLE" ? "radio" : "checkbox"
                                }
                                name={`q_${qIdx}_correct`}
                                checked={opt.correct}
                                disabled={isLocked}
                                onChange={(e) =>
                                  updateOption(
                                    qIdx,
                                    optIdx,
                                    "correct",
                                    e.target.checked,
                                  )
                                }
                                aria-label={`Option ${optIdx + 1} correct`}
                                className="h-4 w-4 text-brand-600 focus:ring-brand-500 disabled:opacity-40"
                              />
                              <input
                                type="text"
                                value={opt.text}
                                disabled={isLocked}
                                onChange={(e) =>
                                  updateOption(
                                    qIdx,
                                    optIdx,
                                    "text",
                                    e.target.value,
                                  )
                                }
                                placeholder={`Option ${optIdx + 1} text`}
                                className="flex-1 rounded border border-surface-300 px-2.5 py-1 text-sm focus:border-brand-600 focus:outline-hidden disabled:bg-surface-100"
                              />
                              {!isLocked && q.options.length > 2 && (
                                <button
                                  type="button"
                                  onClick={() => removeOption(qIdx, optIdx)}
                                  className="text-xs text-rose-500 hover:text-rose-700"
                                >
                                  ✕
                                </button>
                              )}
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}

                  {!isLocked && (
                    <div className="flex justify-end pt-2">
                      <Button
                        type="button"
                        variant="primary"
                        disabled={saveQuestionsMutation.isPending}
                        onClick={() => saveQuestionsMutation.mutate(questions)}
                      >
                        {saveQuestionsMutation.isPending
                          ? "Saving..."
                          : "Save Questions"}
                      </Button>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Delete Confirmation Dialog */}
      <Dialog.Root open={deleteOpen} onOpenChange={setDeleteOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs" />
          <Dialog.Content className="fixed top-1/2 left-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-white p-6 shadow-xl">
            <Dialog.Title className="text-lg font-bold text-ink-900">
              Delete Assignment
            </Dialog.Title>
            <Dialog.Description className="mt-2 text-sm text-ink-600">
              Are you sure you want to delete &ldquo;{assignment.title}&rdquo;?
              This action is permanent and cannot be undone.
            </Dialog.Description>

            <div className="mt-6 flex justify-end gap-3">
              <Button
                type="button"
                variant="outline"
                onClick={() => setDeleteOpen(false)}
              >
                Cancel
              </Button>
              <Button
                type="button"
                variant="danger"
                disabled={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate()}
              >
                {deleteMutation.isPending ? "Deleting..." : "Delete Assignment"}
              </Button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
}
