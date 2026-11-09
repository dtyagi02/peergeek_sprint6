import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../shared/components/Toast";
import { LessonEditor } from "./LessonEditor";
import { updateLesson } from "./courseApi";

vi.mock("./courseApi", () => ({
  getLessonContent: vi.fn(),
  updateLesson: vi.fn(),
}));

function renderLessonEditor(initialContent) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const onSaved = vi.fn();
  render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <LessonEditor
            courseId="8"
            initialContent={initialContent}
            lesson={{ id: 12, title: "Lesson One", summary: "" }}
            lessonId="12"
            onSaved={onSaved}
            queryClient={queryClient}
          />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
  return { onSaved };
}

describe("lesson editor", () => {
  beforeEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders text-only formatting controls and displays the stored word count", async () => {
    renderLessonEditor({
      document: {
        type: "doc",
        content: [
          {
            type: "paragraph",
            content: [{ type: "text", text: "Initial lesson text" }],
          },
        ],
      },
      version: 2,
      wordCount: 3,
    });

    expect(
      await screen.findByRole("textbox", { name: "Lesson content" }),
    ).toBeVisible();
    expect(screen.getByText(/3 words/)).toBeVisible();
    expect(screen.getByRole("button", { name: "Bold" })).toBeVisible();
    expect(
      screen.queryByRole("button", { name: /underline|strike/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText(/Images and embedded resources are not supported/),
    ).toBeVisible();
  });

  it("saves changed lesson metadata", async () => {
    const user = userEvent.setup();
    updateLesson.mockResolvedValue({
      title: "Updated lesson",
      summary: "A short summary",
    });
    renderLessonEditor({
      document: {
        type: "doc",
        content: [
          {
            type: "paragraph",
            content: [{ type: "text", text: "Initial lesson text" }],
          },
        ],
      },
      version: 2,
      wordCount: 3,
    });

    const titleInput = screen.getByLabelText("Lesson title");
    await user.clear(titleInput);
    await user.type(titleInput, "Updated lesson");
    await user.clear(screen.getByLabelText("Summary"));
    await user.type(screen.getByLabelText("Summary"), "A short summary");
    await user.click(
      screen.getByRole("button", { name: "Save title and summary" }),
    );

    await waitFor(() =>
      expect(updateLesson).toHaveBeenCalledWith("12", {
        title: "Updated lesson",
        summary: "A short summary",
      }),
    );
  });
});
