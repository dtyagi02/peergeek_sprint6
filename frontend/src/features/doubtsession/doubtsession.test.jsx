import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";
import { BrowserRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CourseDoubtSessionCard } from "./CourseDoubtSessionCard";
import { InstructorActiveSessionBanner } from "./InstructorActiveSessionBanner";
import { InstructorCourseSettings } from "./InstructorCourseSettings";
import * as doubtSessionApi from "./doubtSessionApi";
import * as walletApi from "../wallet/walletApi";
import { useAuthStore } from "../auth/authStore";
import { slotRegistry } from "../../app/registry/slotRegistry";

vi.mock("./doubtSessionApi", () => ({
  getCourseAvailability: vi.fn(),
  requestSession: vi.fn(),
  cancelSession: vi.fn(),
  getActiveStudentSession: vi.fn(),
  getInstructorCourseSettings: vi.fn(),
  updateInstructorCourseSettings: vi.fn(),
  getActiveInstructorSession: vi.fn(),
  acceptSession: vi.fn(),
  rejectSession: vi.fn(),
}));

vi.mock("../wallet/walletApi", () => ({
  getWalletBalance: vi.fn(),
}));

function renderWithProviders(ui) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        {ui}
      </BrowserRouter>
    </QueryClientProvider>,
  );
}

describe("Live Doubt-Clearing Feature", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({
      status: "authenticated",
      user: { id: 1, email: "student@test.local", displayName: "Student One", lastMode: "STUDENT" },
    });
  });

  afterEach(() => {
    cleanup();
  });

  describe("CourseDoubtSessionCard", () => {
    it("renders inactive state for students when doubt clearing is disabled for the course", async () => {
      doubtSessionApi.getCourseAvailability.mockResolvedValueOnce({
        courseId: 10,
        enabled: false,
        available: false,
        hourlyRatePgc: 600,
        isOwner: false,
      });
      walletApi.getWalletBalance.mockResolvedValueOnce({ balance: 500.00 });
      doubtSessionApi.getActiveStudentSession.mockResolvedValueOnce(null);

      renderWithProviders(
        <CourseDoubtSessionCard courseId={10} courseTitle="Java 101" isEnrolled={true} />
      );

      expect(await screen.findByText("Not Currently Active")).toBeInTheDocument();
      expect(screen.getByText(/not currently enabled by the instructor/i)).toBeInTheDocument();
    });

    it("renders instructor live badge, rate, and duration options when enabled", async () => {
      doubtSessionApi.getCourseAvailability.mockResolvedValueOnce({
        courseId: 10,
        enabled: true,
        available: true,
        hourlyRatePgc: 600,
        instructorDisplayName: "Prof. Ada",
        instructorHasActiveSession: false,
        isEnrolled: true,
      });
      walletApi.getWalletBalance.mockResolvedValueOnce({ balance: 500.00 });
      doubtSessionApi.getActiveStudentSession.mockResolvedValueOnce(null);

      renderWithProviders(
        <CourseDoubtSessionCard courseId={10} courseTitle="Java 101" isEnrolled={true} />
      );

      expect(await screen.findByText("Instructor Live Now")).toBeInTheDocument();
      expect(screen.getByText("Prof. Ada", { exact: false })).toBeInTheDocument();
      expect(screen.getAllByText(/150.*PGC/i).length).toBeGreaterThan(0);
    });

    it("disables request button and shows warning if student has insufficient funds", async () => {
      doubtSessionApi.getCourseAvailability.mockResolvedValueOnce({
        courseId: 10,
        enabled: true,
        available: true,
        hourlyRatePgc: 600,
        instructorDisplayName: "Prof. Ada",
        instructorHasActiveSession: false,
        isEnrolled: true,
      });
      // Student has only 20 PGC, but 15m session requires 150 PGC
      walletApi.getWalletBalance.mockResolvedValueOnce({ balance: 20.00 });
      doubtSessionApi.getActiveStudentSession.mockResolvedValueOnce(null);

      renderWithProviders(
        <CourseDoubtSessionCard courseId={10} courseTitle="Java 101" isEnrolled={true} />
      );

      const insufficientBtn = await screen.findByText(/Insufficient GeekCoins/i);
      expect(insufficientBtn).toBeDisabled();
    });

    it("submits session request and shows waiting countdown", async () => {
      doubtSessionApi.getCourseAvailability.mockResolvedValueOnce({
        courseId: 10,
        enabled: true,
        available: true,
        hourlyRatePgc: 600,
        instructorDisplayName: "Prof. Ada",
        instructorHasActiveSession: false,
        isEnrolled: true,
      });
      walletApi.getWalletBalance.mockResolvedValueOnce({ balance: 500.00 });
      doubtSessionApi.getActiveStudentSession.mockResolvedValueOnce(null);
      doubtSessionApi.requestSession.mockResolvedValueOnce({
        id: 42,
        sessionUid: "test-uid-42",
        status: "REQUESTED",
        maxCost: 150.00,
        expiresAt: new Date(Date.now() + 60000).toISOString(),
      });

      renderWithProviders(
        <CourseDoubtSessionCard courseId={10} courseTitle="Java 101" isEnrolled={true} />
      );

      const requestBtn = await screen.findByRole("button", { name: /Request Instant Session/i });
      expect(requestBtn).not.toBeDisabled();

      fireEvent.click(requestBtn);

      expect(await screen.findByText(/Waiting for instructor to accept/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Cancel Request/i })).toBeInTheDocument();
    });
  });

  describe("InstructorActiveSessionBanner", () => {
    it("renders incoming request modal for instructor with countdown and accept button", async () => {
      useAuthStore.setState({
        status: "authenticated",
        user: { id: 2, email: "inst@test.local", displayName: "Instructor Ada", lastMode: "INSTRUCTOR" },
      });

      doubtSessionApi.getActiveInstructorSession.mockResolvedValueOnce({
        id: 42,
        sessionUid: "test-uid-42",
        status: "REQUESTED",
        studentDisplayName: "Student John",
        courseTitle: "Java 101",
        maxDurationMinutes: 15,
        reservedAmount: 150.00,
        expiresAt: new Date(Date.now() + 60000).toISOString(),
      });

      renderWithProviders(<InstructorActiveSessionBanner />);

      expect(await screen.findByTestId("instructor-incoming-request-modal")).toBeInTheDocument();
      expect(screen.getByText("Student John")).toBeInTheDocument();
      expect(screen.getByText("Java 101")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Accept & Join/i })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Decline/i })).toBeInTheDocument();
    });

    it("does not render when instructor has no active request", async () => {
      useAuthStore.setState({
        status: "authenticated",
        user: { id: 2, email: "inst@test.local", displayName: "Instructor Ada", lastMode: "INSTRUCTOR" },
      });

      doubtSessionApi.getActiveInstructorSession.mockResolvedValueOnce(null);

      const { container } = renderWithProviders(<InstructorActiveSessionBanner />);

      await waitFor(() => {
        expect(container.firstChild).toBeNull();
      });
    });
  });

  describe("InstructorCourseSettings", () => {
    it("renders settings form and allows toggling availability and updating hourly rate", async () => {
      doubtSessionApi.getInstructorCourseSettings.mockResolvedValueOnce({
        courseId: 10,
        enabled: true,
        available: false,
        hourlyRatePgc: 600,
      });

      renderWithProviders(<InstructorCourseSettings courseId={10} />);

      expect(await screen.findByTestId("instructor-course-doubt-settings")).toBeInTheDocument();
      expect(screen.getByLabelText(/Enable Live Doubt Sessions/i)).toBeChecked();
      expect(screen.getByLabelText(/Hourly Rate in GeekCoins/i)).toHaveValue(600);
      expect(screen.getByLabelText(/I am Currently Available/i)).not.toBeChecked();

      doubtSessionApi.updateInstructorCourseSettings.mockResolvedValueOnce({
        courseId: 10,
        enabled: true,
        available: true,
        hourlyRatePgc: 750,
      });

      // Change rate to 750
      fireEvent.change(screen.getByLabelText(/Hourly Rate in GeekCoins/i), { target: { value: "750" } });
      // Toggle availability ON
      fireEvent.click(screen.getByLabelText(/I am Currently Available/i));

      fireEvent.click(screen.getByRole("button", { name: /Save Settings/i }));

      await waitFor(() => {
        expect(doubtSessionApi.updateInstructorCourseSettings).toHaveBeenCalledWith(10, {
          enabled: true,
          available: true,
          hourlyRatePgc: 750,
        });
      });
    });

    it("displays error state when settings retrieval fails and prevents saving until loaded", async () => {
      doubtSessionApi.getInstructorCourseSettings.mockRejectedValueOnce(
        new Error("The requested resource was not found")
      );

      renderWithProviders(<InstructorCourseSettings courseId={10} />);

      expect(await screen.findByText(/Unable to load live doubt-clearing settings/i)).toBeInTheDocument();
      expect(screen.getByText(/The requested resource was not found/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Retry/i })).toBeInTheDocument();
      // Form should not be rendered, so Save Settings button does not exist
      expect(screen.queryByRole("button", { name: /Save Settings/i })).not.toBeInTheDocument();
      expect(doubtSessionApi.updateInstructorCourseSettings).not.toHaveBeenCalled();
    });

    it("allows retry after settings fetch error and loads form on success", async () => {
      doubtSessionApi.getInstructorCourseSettings
        .mockRejectedValueOnce(new Error("Network Error"))
        .mockResolvedValueOnce({
          courseId: 10,
          enabled: false,
          available: false,
          hourlyRatePgc: 600,
        });

      renderWithProviders(<InstructorCourseSettings courseId={10} />);

      const retryButton = await screen.findByRole("button", { name: /Retry/i });
      fireEvent.click(retryButton);

      expect(await screen.findByRole("button", { name: /Save Settings/i })).toBeInTheDocument();
      expect(screen.getByLabelText(/Enable Live Doubt Sessions/i)).not.toBeChecked();
    });

    it("disables Save Settings and displays validation message when hourly rate is invalid", async () => {
      doubtSessionApi.getInstructorCourseSettings.mockResolvedValueOnce({
        courseId: 10,
        enabled: true,
        available: false,
        hourlyRatePgc: 600,
      });

      renderWithProviders(<InstructorCourseSettings courseId={10} />);

      expect(await screen.findByRole("button", { name: /Save Settings/i })).toBeEnabled();

      // Enter 0 (invalid)
      fireEvent.change(screen.getByLabelText(/Hourly Rate in GeekCoins/i), { target: { value: "0" } });
      expect(screen.getByText(/Hourly rate must be a positive whole number/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Save Settings/i })).toBeDisabled();

      // Enter negative number (invalid)
      fireEvent.change(screen.getByLabelText(/Hourly Rate in GeekCoins/i), { target: { value: "-50" } });
      expect(screen.getByText(/Hourly rate must be a positive whole number/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Save Settings/i })).toBeDisabled();

      // Enter decimal number (invalid)
      fireEvent.change(screen.getByLabelText(/Hourly Rate in GeekCoins/i), { target: { value: "50.5" } });
      expect(screen.getByText(/Hourly rate must be a positive whole number/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Save Settings/i })).toBeDisabled();

      // Enter empty (invalid)
      fireEvent.change(screen.getByLabelText(/Hourly Rate in GeekCoins/i), { target: { value: "" } });
      expect(screen.getByText(/Hourly rate must be a positive whole number/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Save Settings/i })).toBeDisabled();

      // Enter valid positive integer (valid)
      fireEvent.change(screen.getByLabelText(/Hourly Rate in GeekCoins/i), { target: { value: "900" } });
      expect(screen.queryByText(/Hourly rate must be a positive whole number/i)).not.toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Save Settings/i })).toBeEnabled();
    });
  });

  describe("Slot Contribution", () => {
    it("registers course-doubt-session-card in course-page.enroll-area slot", () => {
      const enrollAreaSlots = slotRegistry["course-page.enroll-area"] || [];
      const doubtSlot = enrollAreaSlots.find((item) => item.id === "course-doubt-session-card");

      expect(doubtSlot).toBeDefined();
      expect(doubtSlot.order).toBe(20);

      const playerSlots = slotRegistry["player.header-actions"] || [];
      const playerDoubtSlot = playerSlots.find((item) => item.id === "player-doubt-button");
      expect(playerDoubtSlot).toBeDefined();
      expect(playerDoubtSlot.order).toBe(5);
    });
  });
});
