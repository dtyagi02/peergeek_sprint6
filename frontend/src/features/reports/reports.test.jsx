import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ReportButton } from "./ReportButton";
import * as reportsApi from "./reportsApi";

vi.mock("./reportsApi", () => ({
  submitReport: vi.fn(),
}));

function renderReportButton(courseId = 42) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <ReportButton courseId={courseId} />
    </QueryClientProvider>,
  );
}

describe("ReportButton and ReportModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders report button and opens modal when clicked", async () => {
    const user = userEvent.setup();
    renderReportButton(42);

    const button = screen.getByRole("button", {
      name: /report an issue with this course content/i,
    });
    expect(button).toBeInTheDocument();

    await user.click(button);

    expect(
      screen.getByRole("heading", { name: "Report Course Issue" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/issue category/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/details/i)).toBeInTheDocument();
  });

  it("submits report with selected category and details", async () => {
    const user = userEvent.setup();
    reportsApi.submitReport.mockResolvedValueOnce({
      id: 1,
      courseId: 42,
      category: "BROKEN_RESOURCE",
      details: "Video in lesson 2 does not play",
    });

    renderReportButton(42);

    await user.click(
      screen.getByRole("button", {
        name: /report an issue with this course content/i,
      }),
    );

    const detailsInput = screen.getByLabelText(/details/i);
    await user.type(detailsInput, "Video in lesson 2 does not play");

    const submitBtn = screen.getByRole("button", { name: /submit report/i });
    await user.click(submitBtn);

    expect(reportsApi.submitReport).toHaveBeenCalledWith(42, {
      category: "BROKEN_RESOURCE",
      details: "Video in lesson 2 does not play",
    });

    expect(
      await screen.findByText("Thank you for your report"),
    ).toBeInTheDocument();
  });
});
