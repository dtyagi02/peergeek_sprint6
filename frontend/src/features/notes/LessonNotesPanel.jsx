import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { deleteLessonNote, getLessonNote, saveLessonNote } from "./notesApi";
import { Button } from "../../shared/components/Button";

const MAX_NOTE_LENGTH = 50000;

export function LessonNotesPanel({ courseId, lessonId }) {
  if (!courseId || !lessonId) {
    return null;
  }
  const numCourseId = Number(courseId);
  const numLessonId = Number(lessonId);

  return (
    <LessonNotesEditor
      key={`${numCourseId}-${numLessonId}`}
      courseId={numCourseId}
      lessonId={numLessonId}
    />
  );
}

function LessonNotesEditor({ courseId, lessonId }) {
  const queryClient = useQueryClient();

  const [userText, setUserText] = useState(null);
  const [statusMessage, setStatusMessage] = useState("");
  const [confirmDelete, setConfirmDelete] = useState(false);

  const queryKey = ["lesson-note", courseId, lessonId];

  const {
    data: noteData,
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey,
    queryFn: () => getLessonNote(courseId, lessonId),
    enabled: Boolean(courseId && lessonId),
    staleTime: 30_000,
  });

  const serverText = noteData?.content ?? "";
  const noteContent = userText !== null ? userText : serverText;
  const hasUnsavedChanges = userText !== null && userText !== serverText;
  const hasExistingNote = Boolean(noteData?.id || serverText);

  // Save Mutation
  const saveMutation = useMutation({
    mutationFn: async ({ text, targetCourseId, targetLessonId }) => {
      return saveLessonNote(targetCourseId, targetLessonId, text);
    },
    onSuccess: (savedNote, { targetCourseId, targetLessonId }) => {
      setUserText(null);
      setStatusMessage("Saved ✓");
      queryClient.setQueryData(
        ["lesson-note", targetCourseId, targetLessonId],
        savedNote,
      );
      setTimeout(() => setStatusMessage(""), 3000);
    },
    onError: () => {
      setStatusMessage("Failed to save note");
    },
  });

  // Delete Mutation
  const deleteMutation = useMutation({
    mutationFn: async ({ targetCourseId, targetLessonId }) => {
      return deleteLessonNote(targetCourseId, targetLessonId);
    },
    onSuccess: (_data, { targetCourseId, targetLessonId }) => {
      setUserText("");
      setConfirmDelete(false);
      setStatusMessage("Note deleted");
      queryClient.setQueryData(
        ["lesson-note", targetCourseId, targetLessonId],
        null,
      );
      setTimeout(() => setStatusMessage(""), 3000);
    },
    onError: () => {
      setStatusMessage("Failed to delete note");
    },
  });

  function handleSave() {
    if (!courseId || !lessonId || saveMutation.isPending) return;
    setStatusMessage("Saving...");
    saveMutation.mutate({
      text: noteContent,
      targetCourseId: courseId,
      targetLessonId: lessonId,
    });
  }

  function handleDelete() {
    if (!courseId || !lessonId || deleteMutation.isPending) return;
    deleteMutation.mutate({
      targetCourseId: courseId,
      targetLessonId: lessonId,
    });
  }

  const charCount = noteContent.length;
  const wordCount = noteContent.trim()
    ? noteContent.trim().split(/\s+/).length
    : 0;

  return (
    <section
      aria-label="Private Lesson Notes"
      className="rounded-xl border border-surface-200 bg-white p-5 shadow-xs transition"
    >
      {/* Panel Header */}
      <div className="flex items-center justify-between border-b border-surface-100 pb-3 mb-4">
        <div className="flex items-center gap-2">
          <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-amber-100 text-amber-800 text-sm">
            📝
          </span>
          <div>
            <h2 className="text-sm font-bold text-ink-900">Private Notes</h2>
            <p className="text-[11px] text-ink-500">
              Only visible to you — never shared with others
            </p>
          </div>
        </div>

        {/* Status / Indicator */}
        <div className="flex items-center gap-2">
          {statusMessage && (
            <span
              className={`text-xs font-medium ${
                statusMessage.includes("Failed")
                  ? "text-rose-600"
                  : "text-emerald-600"
              }`}
            >
              {statusMessage}
            </span>
          )}
          {hasUnsavedChanges && !statusMessage && (
            <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700 border border-amber-200">
              Unsaved edits
            </span>
          )}
        </div>
      </div>

      {/* Loading Skeleton */}
      {isLoading && (
        <div className="animate-pulse space-y-3">
          <div className="h-28 rounded-lg bg-surface-100" />
          <div className="flex justify-between">
            <div className="h-4 w-24 rounded bg-surface-100" />
            <div className="h-8 w-20 rounded bg-surface-200" />
          </div>
        </div>
      )}

      {/* Error state */}
      {!isLoading && isError && (
        <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-center">
          <p className="text-xs text-rose-700">
            Unable to load note for this lesson.
          </p>
          <button
            type="button"
            onClick={() => refetch()}
            className="mt-2 text-xs font-semibold text-rose-800 underline hover:no-underline"
          >
            Retry
          </button>
        </div>
      )}

      {/* Notes Textarea & Actions */}
      {!isLoading && !isError && (
        <div>
          <label htmlFor={`lesson-note-${lessonId}`} className="sr-only">
            Notes for current lesson
          </label>
          <textarea
            id={`lesson-note-${lessonId}`}
            value={noteContent}
            onChange={(e) => setUserText(e.target.value)}
            maxLength={MAX_NOTE_LENGTH}
            placeholder="Write personal key takeaways, timestamps, formulas, or reminders for this lesson..."
            rows={5}
            className="w-full rounded-lg border border-surface-300 p-3 text-sm text-ink-900 shadow-xs placeholder:text-ink-400 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 font-mono text-xs leading-relaxed"
          />

          {/* Footer Controls */}
          <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
            <div className="text-[11px] text-ink-400">
              <span>{wordCount} words</span>
              <span className="mx-1.5">•</span>
              <span
                className={
                  charCount > MAX_NOTE_LENGTH * 0.9
                    ? "text-amber-600 font-bold"
                    : ""
                }
              >
                {charCount.toLocaleString()} /{" "}
                {MAX_NOTE_LENGTH.toLocaleString()} chars
              </span>
            </div>

            <div className="flex items-center gap-2">
              {/* Delete Note Option */}
              {hasExistingNote && !confirmDelete && (
                <button
                  type="button"
                  onClick={() => setConfirmDelete(true)}
                  className="rounded-md px-2.5 py-1.5 text-xs font-medium text-rose-600 hover:bg-rose-50 hover:text-rose-700 transition"
                >
                  Delete note
                </button>
              )}

              {confirmDelete && (
                <div className="flex items-center gap-1.5 bg-rose-50 border border-rose-200 rounded-md p-1">
                  <span className="text-[11px] text-rose-700 px-1">
                    Delete note?
                  </span>
                  <button
                    type="button"
                    onClick={handleDelete}
                    disabled={deleteMutation.isPending}
                    className="rounded bg-rose-600 px-2 py-0.5 text-[11px] font-bold text-white hover:bg-rose-700"
                  >
                    Confirm
                  </button>
                  <button
                    type="button"
                    onClick={() => setConfirmDelete(false)}
                    className="rounded bg-white px-2 py-0.5 text-[11px] font-medium text-ink-600 border border-surface-200 hover:bg-surface-100"
                  >
                    Cancel
                  </button>
                </div>
              )}

              {/* Save Button */}
              <Button
                type="button"
                variant="primary"
                size="sm"
                onClick={handleSave}
                disabled={
                  saveMutation.isPending || (!hasUnsavedChanges && !noteContent)
                }
              >
                {saveMutation.isPending ? "Saving..." : "Save note"}
              </Button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
