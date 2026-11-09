import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import * as analyticsApi from "./analyticsApi";
import { InstructorAnalyticsPage } from "./InstructorAnalyticsPage";
import { CourseEditorAnalyticsHeaderLink } from "./AnalyticsSlotComponents";

vi.mock("./analyticsApi");

function renderWithProviders(
  ui,
  {
    route = "/instructor/courses/10/analytics",
    path = "/instructor/courses/:courseId/analytics",
  } = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
        staleTime: 0,
      },
    },
  });

  return {
    queryClient,
    ...render(
      <MemoryRouter initialEntries={[route]}>
        <QueryClientProvider client={queryClient}>
          <Routes>
            <Route path={path} element={ui} />
            <Route path="*" element={<div>Fallback Page</div>} />
          </Routes>
        </QueryClientProvider>
      </MemoryRouter>,
    ),
  };
}

describe("Phase 10: Instructor Analytics Feature", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  const mockAnalyticsData = {
    courseId: 10,
    courseTitle: "Distributed Systems Architecture",
    totalEnrollments: 25,
    activeLearners: 20,
    completionRate: 60.0,
    averageAssessmentScore: 82.5,
    averageRating: 4.8,
    ratingCount: 15,
    assignmentSubmissionRate: 75.0,
    pendingSubmissions: 3,
    lessonDropOff: [
      {
        lessonId: 101,
        lessonTitle: "Introduction to Raft",
        moduleTitle: "Consensus Algorithms",
        orderIndex: 1,
        completedCount: 20,
        completionPercent: 100.0,
        dropOffRate: 0.0,
      },
      {
        lessonId: 102,
        lessonTitle: "Leader Election",
        moduleTitle: "Consensus Algorithms",
        orderIndex: 2,
        completedCount: 15,
        completionPercent: 75.0,
        dropOffRate: 25.0,
      },
      {
        lessonId: 103,
        lessonTitle: "Log Replication",
        moduleTitle: "Consensus Algorithms",
        orderIndex: 3,
        completedCount: 12,
        completionPercent: 60.0,
        dropOffRate: 20.0,
      },
    ],
    assignmentStats: [
      {
        id: 1,
        title: "Raft RPC Simulator",
        status: "PUBLISHED",
        dueAt: "2026-11-01T00:00:00Z",
        maxPoints: 100.0,
        submittedCount: 18,
        gradedCount: 15,
        submissionRate: 90.0,
        averageScore: 85.0,
      },
      {
        id: 2,
        title: "KV Store over Raft",
        status: "PUBLISHED",
        dueAt: "2026-11-15T00:00:00Z",
        maxPoints: 50.0,
        submittedCount: 12,
        gradedCount: 12,
        submissionRate: 60.0,
        averageScore: 80.0,
      },
    ],
    scoreDistribution: [
      { bucket: "0-20%", count: 0, percentage: 0.0 },
      { bucket: "21-40%", count: 1, percentage: 3.7 },
      { bucket: "41-60%", count: 4, percentage: 14.8 },
      { bucket: "61-80%", count: 10, percentage: 37.0 },
      { bucket: "81-100%", count: 12, percentage: 44.5 },
    ],
  };

  it("renders loading skeleton while query is fetching", () => {
    analyticsApi.getCourseAnalytics.mockReturnValue(new Promise(() => {}));

    renderWithProviders(<InstructorAnalyticsPage />);

    expect(screen.getByLabelText("Loading analytics")).toBeInTheDocument();
  });

  it("renders error state with retry button on fetch failure", async () => {
    analyticsApi.getCourseAnalytics.mockRejectedValue(
      new Error("Unauthorized instructor access"),
    );

    renderWithProviders(<InstructorAnalyticsPage />);

    await waitFor(() => {
      expect(
        screen.getByText("Failed to Load Course Analytics"),
      ).toBeInTheDocument();
    });

    expect(
      screen.getByText(/Unauthorized instructor access/),
    ).toBeInTheDocument();

    const retryButton = screen.getByRole("button", { name: /retry/i });
    expect(retryButton).toBeInTheDocument();

    // Trigger retry
    analyticsApi.getCourseAnalytics.mockResolvedValueOnce(mockAnalyticsData);
    await userEvent.click(retryButton);

    await waitFor(() => {
      expect(screen.getByText("Course Analytics")).toBeInTheDocument();
    });
  });

  it("renders all 7 KPI cards with accurate values and formatting", async () => {
    analyticsApi.getCourseAnalytics.mockResolvedValue(mockAnalyticsData);

    renderWithProviders(<InstructorAnalyticsPage />);

    await waitFor(() => {
      expect(
        screen.getByText("Distributed Systems Architecture"),
      ).toBeInTheDocument();
    });

    // 1. Total Enrollments
    const enrollmentsCard = screen.getByTestId("kpi-total-enrollments");
    expect(enrollmentsCard).toHaveTextContent("25");

    // 2. Active Learners
    const activeCard = screen.getByTestId("kpi-active-learners");
    expect(activeCard).toHaveTextContent("20");
    expect(activeCard).toHaveTextContent("(80%)");

    // 3. Completion Rate
    const completionCard = screen.getByTestId("kpi-completion-rate");
    expect(completionCard).toHaveTextContent("60%");

    // 4. Avg Assessment Score
    const scoreCard = screen.getByTestId("kpi-average-score");
    expect(scoreCard).toHaveTextContent("82.5%");

    // 5. Rating
    const ratingCard = screen.getByTestId("kpi-average-rating");
    expect(ratingCard).toHaveTextContent("4.8");
    expect(ratingCard).toHaveTextContent("15 learner reviews");

    // 6. Submission Rate
    const subRateCard = screen.getByTestId("kpi-submission-rate");
    expect(subRateCard).toHaveTextContent("75%");

    // 7. Pending Submissions
    const pendingCard = screen.getByTestId("kpi-pending-submissions");
    expect(pendingCard).toHaveTextContent("3");
    expect(screen.getByRole("link", { name: /review queue/i })).toHaveAttribute(
      "href",
      "/instructor/courses/10/submissions?status=SUBMITTED",
    );
  });

  it("renders all 3 visual charts with complete datasets", async () => {
    analyticsApi.getCourseAnalytics.mockResolvedValue(mockAnalyticsData);

    renderWithProviders(<InstructorAnalyticsPage />);

    await waitFor(() => {
      expect(screen.getByTestId("chart-lesson-drop-off")).toBeInTheDocument();
    });

    // Chart 1: Lesson drop-off
    expect(screen.getByText("Introduction to Raft")).toBeInTheDocument();
    expect(screen.getByText("Leader Election")).toBeInTheDocument();
    expect(screen.getByText("-25% drop")).toBeInTheDocument();
    expect(screen.getByText("Log Replication")).toBeInTheDocument();
    expect(screen.getByText("-20% drop")).toBeInTheDocument();

    // Chart 2: Score distribution
    expect(screen.getByTestId("chart-score-distribution")).toBeInTheDocument();
    expect(screen.getByText("81-100%")).toBeInTheDocument();
    expect(screen.getByText("12 submissions (44.5%)")).toBeInTheDocument();

    // Chart 3: Assignment performance table
    expect(
      screen.getByTestId("chart-assignment-performance"),
    ).toBeInTheDocument();
    expect(screen.getByText("Raft RPC Simulator")).toBeInTheDocument();
    expect(screen.getByText("18")).toBeInTheDocument();
    expect(screen.getByText("85%")).toBeInTheDocument();
    expect(screen.getByText("KV Store over Raft")).toBeInTheDocument();
  });

  it("handles empty course and null metric states safely without crashing", async () => {
    const emptyAnalytics = {
      courseId: 99,
      courseTitle: "Brand New Course",
      totalEnrollments: 0,
      activeLearners: 0,
      completionRate: 0.0,
      averageAssessmentScore: null,
      averageRating: null,
      ratingCount: 0,
      assignmentSubmissionRate: 0.0,
      pendingSubmissions: 0,
      lessonDropOff: [],
      assignmentStats: [],
      scoreDistribution: [
        { bucket: "0-20%", count: 0, percentage: 0.0 },
        { bucket: "21-40%", count: 0, percentage: 0.0 },
        { bucket: "41-60%", count: 0, percentage: 0.0 },
        { bucket: "61-80%", count: 0, percentage: 0.0 },
        { bucket: "81-100%", count: 0, percentage: 0.0 },
      ],
    };

    analyticsApi.getCourseAnalytics.mockResolvedValue(emptyAnalytics);

    renderWithProviders(<InstructorAnalyticsPage />, {
      route: "/instructor/courses/99/analytics",
      path: "/instructor/courses/:courseId/analytics",
    });

    await waitFor(() => {
      expect(screen.getByTestId("empty-course-banner")).toBeInTheDocument();
    });

    expect(screen.getByText("No Learner Activity Yet")).toBeInTheDocument();

    // Null and zero metrics
    expect(screen.getByTestId("kpi-average-score")).toHaveTextContent("N/A");
    expect(screen.getByTestId("kpi-average-score")).toHaveTextContent(
      "No graded submissions yet",
    );

    expect(screen.getByTestId("kpi-average-rating")).toHaveTextContent("N/A");
    expect(screen.getByTestId("kpi-average-rating")).toHaveTextContent(
      "No reviews submitted",
    );

    expect(screen.getByTestId("kpi-pending-submissions")).toHaveTextContent(
      "0",
    );
    expect(screen.getByTestId("kpi-pending-submissions")).toHaveTextContent(
      "All submissions graded",
    );

    // Empty charts messages
    expect(
      screen.getByText("No lessons configured in this course."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("No assignments published for this course yet."),
    ).toBeInTheDocument();
  });

  it("CourseEditorAnalyticsHeaderLink renders link to course analytics", () => {
    render(
      <MemoryRouter initialEntries={["/instructor/courses/77"]}>
        <Routes>
          <Route
            path="/instructor/courses/:courseId"
            element={<CourseEditorAnalyticsHeaderLink />}
          />
        </Routes>
      </MemoryRouter>,
    );

    const link = screen.getByRole("link", { name: /analytics/i });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute("href", "/instructor/courses/77/analytics");
  });

  it("isolates query key by courseId", async () => {
    analyticsApi.getCourseAnalytics.mockResolvedValue(mockAnalyticsData);

    renderWithProviders(<InstructorAnalyticsPage />, {
      route: "/instructor/courses/42/analytics",
      path: "/instructor/courses/:courseId/analytics",
    });

    await waitFor(() => {
      expect(analyticsApi.getCourseAnalytics).toHaveBeenCalledWith("42");
    });
  });
});
