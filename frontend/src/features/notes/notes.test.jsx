import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LessonNotesPanel } from "./LessonNotesPanel";
import * as notesApi from "./notesApi";

vi.mock("./notesApi", () => ({
  getLessonNote: vi.fn(),
  saveLessonNote: vi.fn(),
  deleteLessonNote: vi.fn(),
}));

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });
}

function renderPanel(props, { queryClient = createTestQueryClient() } = {}) {
  return render(
    <QueryClientProvider client={queryClient}>
      <LessonNotesPanel {...props} />
    </QueryClientProvider>,
  );
}

describe("LessonNotesPanel Feature", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("loads existing note and allows editing and saving", async () => {
    const user = userEvent.setup();
    notesApi.getLessonNote.mockResolvedValueOnce({
      id: 5,
      userId: 1,
      courseId: 42,
      lessonId: 101,
      content: "Existing takeaway notes.",
    });
    notesApi.saveLessonNote.mockResolvedValueOnce({
      id: 5,
      userId: 1,
      courseId: 42,
      lessonId: 101,
      content: "Existing takeaway notes. With additional points.",
    });

    renderPanel({ courseId: 42, lessonId: 101 });

    const textarea = await screen.findByRole("textbox", {
      name: /notes for current lesson/i,
    });
    expect(textarea).toHaveValue("Existing takeaway notes.");

    // Edit textarea
    await user.type(textarea, " With additional points.");
    expect(screen.getByText(/unsaved edits/i)).toBeInTheDocument();

    // Save
    const saveBtn = screen.getByRole("button", { name: /save note/i });
    await user.click(saveBtn);

    expect(notesApi.saveLessonNote).toHaveBeenCalledWith(
      42,
      101,
      "Existing takeaway notes. With additional points.",
    );
    expect(await screen.findByText(/saved/i)).toBeInTheDocument();
  });

  it("allows deleting an existing note with confirmation", async () => {
    const user = userEvent.setup();
    notesApi.getLessonNote.mockResolvedValueOnce({
      id: 8,
      userId: 1,
      courseId: 42,
      lessonId: 102,
      content: "Obsolete note to delete",
    });
    notesApi.deleteLessonNote.mockResolvedValueOnce();

    renderPanel({ courseId: 42, lessonId: 102 });

    const textarea = await screen.findByRole("textbox");
    expect(textarea).toHaveValue("Obsolete note to delete");

    const deleteBtn = screen.getByRole("button", { name: /delete note/i });
    await user.click(deleteBtn);

    expect(screen.getByText(/delete note\?/i)).toBeInTheDocument();
    const confirmBtn = screen.getByRole("button", { name: /confirm/i });
    await user.click(confirmBtn);

    expect(notesApi.deleteLessonNote).toHaveBeenCalledWith(42, 102);
    expect(await screen.findByText(/note deleted/i)).toBeInTheDocument();
    expect(textarea).toHaveValue("");
  });

  it("handles switching lessons smoothly without cross-contamination", async () => {
    notesApi.getLessonNote.mockImplementation((courseId, lessonId) => {
      if (lessonId === 101) {
        return Promise.resolve({
          id: 1,
          courseId: 42,
          lessonId: 101,
          content: "Notes for Lesson 101",
        });
      }
      return Promise.resolve({
        id: 2,
        courseId: 42,
        lessonId: 102,
        content: "Notes for Lesson 102",
      });
    });

    const queryClient = createTestQueryClient();
    const { rerender } = renderPanel(
      { courseId: 42, lessonId: 101 },
      { queryClient },
    );

    expect(
      await screen.findByDisplayValue("Notes for Lesson 101"),
    ).toBeInTheDocument();

    // Switch lesson to 102
    rerender(
      <QueryClientProvider client={queryClient}>
        <LessonNotesPanel courseId={42} lessonId={102} />
      </QueryClientProvider>,
    );

    expect(
      await screen.findByDisplayValue("Notes for Lesson 102"),
    ).toBeInTheDocument();
  });

  it("renders error state when load fails and allows retry", async () => {
    const user = userEvent.setup();
    notesApi.getLessonNote.mockRejectedValueOnce(new Error("Server error"));

    renderPanel({ courseId: 42, lessonId: 103 });

    expect(
      await screen.findByText(/unable to load note for this lesson/i),
    ).toBeInTheDocument();

    notesApi.getLessonNote.mockResolvedValueOnce(null);
    const retryBtn = screen.getByRole("button", { name: /retry/i });
    await user.click(retryBtn);

    expect(
      await screen.findByPlaceholderText(/write personal key takeaways/i),
    ).toBeInTheDocument();
  });
});
