import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../shared/components/Toast";
import { PublishReadinessPanel } from "./PublishReadinessPanel";
import {
  getLatestReadinessRun,
  getReadinessRun,
  publishCourse,
  startReadinessRun,
} from "./publishApi";
import { getCourse } from "../instructor-courses/courseApi";

vi.mock("react-router-dom", () => ({
  Link: ({ children, to }) => <a href={to}>{children}</a>,
  useParams: () => ({ courseId: "17" }),
}));
vi.mock("./publishApi", () => ({
  getLatestReadinessRun: vi.fn(),
  getReadinessRun: vi.fn(),
  publishCourse: vi.fn(),
  startReadinessRun: vi.fn(),
  unpublishCourse: vi.fn(),
}));
vi.mock("../instructor-courses/courseApi", () => ({ getCourse: vi.fn() }));

function renderPanel() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <PublishReadinessPanel />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("publish readiness panel", () => {
  beforeEach(() => {
    cleanup();
    vi.clearAllMocks();
    getCourse.mockResolvedValue({ id: 17, status: "DRAFT" });
    getLatestReadinessRun.mockResolvedValue(null);
    startReadinessRun.mockResolvedValue({ id: 31, status: "RUNNING" });
    getReadinessRun.mockResolvedValue({
      id: 31,
      status: "COMPLETED",
      blockingCount: 0,
      warningCount: 1,
      issues: [
        {
          ruleCode: "COURSE_DESCRIPTION_SHORT",
          severity: "WARNING",
          scope: "COURSE",
          message: "The course description is brief.",
          fixHint: "Consider adding more detail.",
        },
      ],
    });
    publishCourse.mockResolvedValue({ id: 17, status: "PUBLISHED" });
  });

  it("clicking Check Course Readiness triggers check and allows publishing when ready", async () => {
    const user = userEvent.setup();
    renderPanel();

    const checkBtn = await screen.findByRole("button", {
      name: /Check Course Readiness|Run readiness check/i,
    });
    expect(checkBtn).toBeInTheDocument();

    await user.click(checkBtn);
    expect(startReadinessRun).toHaveBeenCalledWith("17");

    expect(
      await screen.findByText("Course is ready for publication!"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("The course description is brief."),
    ).toBeInTheDocument();

    const publishBtn = screen.getByRole("button", { name: "Publish" });
    expect(publishBtn).not.toBeDisabled();
    await user.click(publishBtn);

    await waitFor(() => expect(publishCourse).toHaveBeenCalledWith("17"));
  });

  it("keeps publishing disabled and renders navigable issues when readiness has blocking issues", async () => {
    startReadinessRun.mockResolvedValue({ id: 32, status: "RUNNING" });
    getReadinessRun.mockResolvedValue({
      id: 32,
      status: "COMPLETED",
      blockingCount: 2,
      warningCount: 0,
      issues: [
        {
          ruleCode: "MODULE_LESSON_REQUIRED",
          severity: "BLOCKING_ERROR",
          scope: "MODULE",
          moduleId: 101,
          moduleTitle: "Spring Security",
          message: "Module 'Spring Security' has no lessons.",
          fixHint: "Add at least one lesson to this module.",
        },
        {
          ruleCode: "LESSON_CONTENT_REQUIRED",
          severity: "BLOCKING_ERROR",
          scope: "LESSON",
          moduleId: 101,
          lessonId: 202,
          moduleTitle: "Spring Security",
          lessonTitle: "JWT Authentication",
          message: "Lesson 'JWT Authentication' has no required authored content.",
          fixHint: "Add content to this lesson.",
        },
      ],
    });
    const user = userEvent.setup();
    renderPanel();

    await user.click(
      await screen.findByRole("button", {
        name: /Check Course Readiness|Run readiness check/i,
      }),
    );

    expect(
      await screen.findByText(/Course is not ready to publish \(2 blocking issues\)/i),
    ).toBeInTheDocument();
    expect(screen.getByText("Module 'Spring Security' has no lessons.")).toBeInTheDocument();
    expect(
      screen.getByText("Lesson 'JWT Authentication' has no required authored content."),
    ).toBeInTheDocument();

    // Verify module and lesson context
    expect(screen.getAllByText("Spring Security").length).toBeGreaterThan(0);
    expect(screen.getByText("JWT Authentication")).toBeInTheDocument();

    // Verify navigation links
    const outlineLinks = screen.getAllByRole("link", { name: /Go to Outline/i });
    expect(outlineLinks.length).toBe(2);
    expect(outlineLinks[0]).toHaveAttribute("href", "/instructor/courses/17?tab=outline");

    // Publish button must remain disabled
    expect(screen.getByRole("button", { name: "Publish" })).toBeDisabled();
  });

  it("displays error state and allows instructor to retry when check fails to start", async () => {
    startReadinessRun.mockRejectedValueOnce(new Error("Network timeout contacting server"));
    const user = userEvent.setup();
    renderPanel();

    await user.click(
      await screen.findByRole("button", {
        name: /Check Course Readiness|Run readiness check/i,
      }),
    );

    expect(
      await screen.findByRole("alert"),
    ).toHaveTextContent("Network timeout contacting server");

    const retryBtn = screen.getByRole("button", { name: /Retry check/i });
    expect(retryBtn).toBeInTheDocument();

    // Now mock success for retry
    startReadinessRun.mockResolvedValueOnce({ id: 33, status: "RUNNING" });
    getReadinessRun.mockResolvedValueOnce({
      id: 33,
      status: "COMPLETED",
      blockingCount: 0,
      warningCount: 0,
      issues: [],
    });

    await user.click(retryBtn);
    expect(
      await screen.findByText("Course is ready for publication!"),
    ).toBeInTheDocument();
  });

  it("displays failed readiness run report with reason and retry option", async () => {
    startReadinessRun.mockResolvedValue({ id: 34, status: "RUNNING" });
    getReadinessRun.mockResolvedValue({
      id: 34,
      status: "FAILED",
      failureReason: "Database connection failed during deep inspection",
      blockingCount: 0,
      warningCount: 0,
      issues: [],
    });
    const user = userEvent.setup();
    renderPanel();

    await user.click(
      await screen.findByRole("button", {
        name: /Check Course Readiness|Run readiness check/i,
      }),
    );

    expect(
      await screen.findByText("Database connection failed during deep inspection"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Retry check/i })).toBeInTheDocument();
  });
});
