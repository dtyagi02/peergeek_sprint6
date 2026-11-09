import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../shared/components/Toast";
import { InstructorResourcePanel } from "./InstructorResourcePanel";
import {
  addResourceLink,
  listExpectedResources,
  listResources,
  uploadLessonResource,
} from "./resourcesApi";

vi.mock("react-router-dom", () => ({ useParams: () => ({ lessonId: "12" }) }));
vi.mock("./resourcesApi", () => ({
  addResourceLink: vi.fn(),
  deleteResource: vi.fn(),
  listExpectedResources: vi.fn(),
  listResources: vi.fn(),
  reorderResources: vi.fn(),
  replaceExpectedResources: vi.fn(),
  resourceTypeForFile: vi.fn(() => "PDF"),
  uploadLessonResource: vi.fn(),
}));

function renderPanel() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <InstructorResourcePanel />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("instructor resource panel", () => {
  beforeEach(() => {
    cleanup();
    vi.clearAllMocks();
    listResources.mockResolvedValue([]);
    listExpectedResources.mockResolvedValue([]);
    addResourceLink.mockResolvedValue({ id: 5 });
  });

  it("creates a lesson resource link and refreshes the list", async () => {
    const user = userEvent.setup();
    renderPanel();

    await user.type(await screen.findByLabelText("Link title"), "Course notes");
    await user.type(screen.getByLabelText("URL"), "https://example.test/notes");
    await user.click(screen.getByRole("button", { name: "Add link" }));

    await waitFor(() =>
      expect(addResourceLink).toHaveBeenCalledWith("12", {
        title: "Course notes",
        url: "https://example.test/notes",
      }),
    );
    expect(listResources).toHaveBeenCalledTimes(2);
  });

  it("uploads a selected file and refreshes resource state", async () => {
    const file = new File(["%PDF-1.7"], "notes.pdf", {
      type: "application/pdf",
    });
    uploadLessonResource.mockResolvedValue({ id: 6, status: "PROCESSING" });
    renderPanel();

    fireEvent.change(await screen.findByLabelText("Upload a file"), {
      target: { files: [file] },
    });

    await waitFor(() =>
      expect(uploadLessonResource).toHaveBeenCalledWith("12", file, "PDF"),
    );
    expect(listResources).toHaveBeenCalledTimes(2);
  });
});
