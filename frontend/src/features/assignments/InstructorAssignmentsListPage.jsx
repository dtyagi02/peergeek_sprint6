import { useState } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  listInstructorAssignments,
  createAssignment,
  deleteAssignment,
} from "./assignmentApi";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { EmptyState } from "../../shared/components/EmptyState";
import { ErrorState } from "../../shared/components/ErrorState";
import * as Dialog from "@radix-ui/react-dialog";

export function InstructorAssignmentsListPage() {
  const { courseId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [createOpen, setCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [errorMessage, setErrorMessage] = useState("");

  const [newTitle, setNewTitle] = useState("");
  const [newInstructions, setNewInstructions] = useState("");

  const assignmentsQuery = useQuery({
    queryKey: ["instructor", "assignments", courseId],
    queryFn: () => listInstructorAssignments(courseId),
  });

  const createMutation = useMutation({
    mutationFn: (data) => createAssignment(courseId, data),
    onSuccess: (created) => {
      queryClient.invalidateQueries({
        queryKey: ["instructor", "assignments", courseId],
      });
      setCreateOpen(false);
      setNewTitle("");
      setNewInstructions("");
      navigate(`/instructor/courses/${courseId}/assignments/${created.id}`);
    },
    onError: (err) => {
      setErrorMessage(err.message || "Failed to create assignment");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id) => deleteAssignment(id),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["instructor", "assignments", courseId],
      });
      setDeleteTarget(null);
    },
    onError: (err) => {
      setErrorMessage(err.message || "Failed to delete assignment");
    },
  });

  const handleCreateSubmit = (e) => {
    e.preventDefault();
    if (!newTitle.trim()) return;

    const payload = {
      kind: "QUESTIONS",
      title: newTitle.trim(),
      instructions: newInstructions.trim() || undefined,
      allowLate: true,
      allowText: false,
      allowFiles: false,
    };

    createMutation.mutate(payload);
  };

  if (assignmentsQuery.isLoading) {
    return <Spinner label="Loading assignments..." />;
  }

  if (assignmentsQuery.isError) {
    return (
      <ErrorState
        title="Failed to load assignments"
        message={assignmentsQuery.error?.message}
        onRetry={() => assignmentsQuery.refetch()}
      />
    );
  }

  const items = assignmentsQuery.data?.content || [];

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <Link
            to={`/instructor/courses/${courseId}`}
            className="text-sm font-medium text-brand-600 hover:text-brand-700"
          >
            ← Back to Course Editor
          </Link>
          <h1 className="mt-1 text-2xl font-bold text-ink-900">
            Assignments & Assessments
          </h1>
          <p className="mt-1 text-sm text-ink-600">
            Manage multiple-choice quizzes, learner submissions, and auto-grading for this course.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Link
            to={`/instructor/courses/${courseId}/submissions`}
            className="rounded-lg border border-surface-300 bg-white px-4 py-2 text-sm font-semibold text-ink-800 shadow-xs hover:bg-surface-50"
          >
            Submissions Queue
          </Link>
          <Button
            type="button"
            variant="primary"
            onClick={() => setCreateOpen(true)}
          >
            + Create Assignment
          </Button>
        </div>
      </div>

      {errorMessage && (
        <div
          role="alert"
          className="mb-6 rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800"
        >
          {errorMessage}
        </div>
      )}

      {items.length === 0 ? (
        <EmptyState
          title="No assignments yet"
          message="Create your first quiz or project assessment to evaluate learner progress."
          action={
            <Button
              type="button"
              variant="primary"
              onClick={() => setCreateOpen(true)}
            >
              Create Assignment
            </Button>
          }
        />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {items.map((item) => (
            <div
              key={item.id}
              className="flex flex-col justify-between rounded-xl border border-surface-200 bg-white p-5 shadow-xs transition hover:shadow-md"
            >
              <div>
                <div className="flex items-center justify-between gap-2">
                  <span
                    className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                      item.status === "PUBLISHED"
                        ? "bg-emerald-100 text-emerald-800"
                        : item.status === "UNPUBLISHED"
                          ? "bg-zinc-100 text-zinc-700"
                          : "bg-amber-100 text-amber-800"
                    }`}
                  >
                    {item.status}
                  </span>
                  <span className="text-xs font-medium uppercase tracking-wider text-ink-500">
                    {item.kind}
                  </span>
                </div>

                <h3 className="mt-3 text-lg font-bold text-ink-900 line-clamp-2">
                  {item.title}
                </h3>

                <div className="mt-3 space-y-1 text-xs text-ink-600">
                  <div>
                    <span className="font-medium text-ink-800">Due: </span>
                    {item.dueAt
                      ? new Date(item.dueAt).toLocaleString()
                      : "No deadline"}
                  </div>
                  <div>
                    <span className="font-medium text-ink-800">
                      Time Limit:{" "}
                    </span>
                    {item.timeLimitMinutes
                      ? `${item.timeLimitMinutes} min`
                      : "Untimed"}
                  </div>
                  <div>
                    <span className="font-medium text-ink-800">
                      Max Points:{" "}
                    </span>
                    {item.maxPoints ?? 0}
                  </div>
                </div>

                <div className="mt-4 grid grid-cols-3 rounded-lg bg-surface-50 p-2.5 text-center text-xs">
                  <div>
                    <div className="font-bold text-ink-900">
                      {item.counts?.submitted ?? 0}
                    </div>
                    <div className="text-ink-500">Submitted</div>
                  </div>
                  <div>
                    <div className="font-bold text-ink-900">
                      {item.counts?.graded ?? 0}
                    </div>
                    <div className="text-ink-500">Graded</div>
                  </div>
                  <div>
                    <div className="font-bold text-ink-900">
                      {item.counts?.inProgress ?? 0}
                    </div>
                    <div className="text-ink-500">Active</div>
                  </div>
                </div>
              </div>

              <div className="mt-5 flex items-center justify-between border-t border-surface-100 pt-4">
                <Link
                  to={`/instructor/courses/${courseId}/assignments/${item.id}`}
                  className="text-sm font-semibold text-brand-600 hover:text-brand-700"
                >
                  Edit / Builder →
                </Link>

                <div className="flex items-center gap-2">
                  <Link
                    to={`/instructor/courses/${courseId}/submissions?assignmentId=${item.id}`}
                    className="rounded-md border border-surface-200 px-2.5 py-1 text-xs font-medium text-ink-700 hover:bg-surface-50"
                  >
                    Submissions
                  </Link>
                  <button
                    type="button"
                    onClick={() => setDeleteTarget(item)}
                    className="rounded-md border border-rose-200 px-2.5 py-1 text-xs font-medium text-rose-700 hover:bg-rose-50"
                  >
                    Delete
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create Assignment Dialog */}
      <Dialog.Root open={createOpen} onOpenChange={setCreateOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs" />
          <Dialog.Content className="fixed top-1/2 left-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-white p-6 shadow-xl">
            <Dialog.Title className="text-lg font-bold text-ink-900">
              Create New MCQ Assessment
            </Dialog.Title>
            <Dialog.Description className="mt-1 text-sm text-ink-600">
              Create an auto-graded multiple-choice questions (MCQ) assessment for your learners.
            </Dialog.Description>

            <form onSubmit={handleCreateSubmit} className="mt-5 space-y-4">
              <div className="rounded-lg border border-brand-200 bg-brand-50/60 p-3 text-xs text-brand-900">
                <span className="font-semibold">Assessment Type:</span> Multiple-Choice Questions (MCQ). You can add single-choice and multiple-choice questions, options, and points in the next step.
              </div>

              <div>
                <label
                  htmlFor="assign-title"
                  className="block text-sm font-medium text-ink-800"
                >
                  Title
                </label>
                <input
                  id="assign-title"
                  type="text"
                  required
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  placeholder="e.g. Distributed Consensus Quiz"
                  className="mt-1 w-full rounded-lg border border-surface-300 px-3 py-2 text-sm focus:border-brand-600 focus:outline-hidden"
                />
              </div>

              <div>
                <label
                  htmlFor="assign-instructions"
                  className="block text-sm font-medium text-ink-800"
                >
                  Instructions
                </label>
                <textarea
                  id="assign-instructions"
                  rows={3}
                  value={newInstructions}
                  onChange={(e) => setNewInstructions(e.target.value)}
                  placeholder="Provide instructions or guidance for learners..."
                  className="mt-1 w-full rounded-lg border border-surface-300 px-3 py-2 text-sm focus:border-brand-600 focus:outline-hidden"
                />
              </div>

              <div className="mt-6 flex justify-end gap-3 pt-3">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setCreateOpen(false)}
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  variant="primary"
                  disabled={createMutation.isPending || !newTitle.trim()}
                >
                  {createMutation.isPending
                    ? "Creating..."
                    : "Create & Open Builder"}
                </Button>
              </div>
            </form>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>

      {/* Delete Confirmation Dialog */}
      <Dialog.Root
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs" />
          <Dialog.Content className="fixed top-1/2 left-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-white p-6 shadow-xl">
            <Dialog.Title className="text-lg font-bold text-ink-900">
              Delete Assignment
            </Dialog.Title>
            <Dialog.Description className="mt-2 text-sm text-ink-600">
              Are you sure you want to delete &ldquo;{deleteTarget?.title}
              &rdquo;? This action cannot be undone. Assignments with existing
              learner attempts cannot be deleted and must be unpublished
              instead.
            </Dialog.Description>

            <div className="mt-6 flex justify-end gap-3">
              <Button
                type="button"
                variant="outline"
                onClick={() => setDeleteTarget(null)}
              >
                Cancel
              </Button>
              <Button
                type="button"
                variant="danger"
                disabled={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate(deleteTarget.id)}
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
