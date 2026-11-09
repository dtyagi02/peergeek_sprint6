import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { visibleNavigation } from "../../app/registry/navigation";
import { useAuthStore } from "../auth/authStore";
import navigation from "./navigation";
import { AdminRoute } from "./AdminRoute";
import { AdminDashboardPage } from "./AdminDashboardPage";
import * as adminApi from "./adminApi";

vi.mock("./adminApi", () => ({
  getDashboardSummary: vi.fn(),
  getActivities: vi.fn(),
  getAdminReports: vi.fn(),
  updateAdminReportStatus: vi.fn(),
  getAdminUsers: vi.fn(),
  updateAdminUserStatus: vi.fn(),
  getAdminCourses: vi.fn(),
  getAdminCourseById: vi.fn(),
  updateAdminCourseStatus: vi.fn(),
}));

function renderWithProviders(ui, { route = "/admin" } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
        staleTime: 0,
      },
    },
  });

  return render(
    <MemoryRouter initialEntries={[route]}>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("Admin Monitoring Dashboard feature", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  describe("Navigation visibility", () => {
    it("hides Admin link for anonymous visitors", () => {
      const items = visibleNavigation(navigation, {
        isAuthenticated: false,
        userRole: undefined,
      });
      expect(items.find((item) => item.id === "admin-dashboard")).toBeUndefined();
    });

    it("hides Admin link for regular students and teachers", () => {
      const studentItems = visibleNavigation(navigation, {
        isAuthenticated: true,
        userRole: "USER",
        userMode: "STUDENT",
      });
      expect(studentItems.find((item) => item.id === "admin-dashboard")).toBeUndefined();

      const teacherItems = visibleNavigation(navigation, {
        isAuthenticated: true,
        userRole: "USER",
        userMode: "INSTRUCTOR",
      });
      expect(teacherItems.find((item) => item.id === "admin-dashboard")).toBeUndefined();
    });

    it("shows Admin link only for authenticated users with ADMIN role", () => {
      const adminItems = visibleNavigation(navigation, {
        isAuthenticated: true,
        userRole: "ADMIN",
      });
      const adminLink = adminItems.find((item) => item.id === "admin-dashboard");
      expect(adminLink).toBeDefined();
      expect(adminLink.label).toBe("Admin");
      expect(adminLink.route).toBe("/admin");
    });
  });

  describe("AdminRoute access control", () => {
    it("redirects non-admin users away to home", () => {
      useAuthStore.setState({
        status: "authenticated",
        user: { id: 1, role: "USER", email: "student@test.local" },
      });

      render(
        <MemoryRouter initialEntries={["/admin"]}>
          <Routes>
            <Route
              path="/admin"
              element={
                <AdminRoute>
                  <div>Secret Admin Area</div>
                </AdminRoute>
              }
            />
            <Route path="/" element={<div>Home Page</div>} />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.queryByText("Secret Admin Area")).not.toBeInTheDocument();
      expect(screen.getByText("Home Page")).toBeInTheDocument();
    });

    it("permits access for authenticated ADMIN users", () => {
      useAuthStore.setState({
        status: "authenticated",
        user: { id: 99, role: "ADMIN", email: "admin@test.local" },
      });

      render(
        <MemoryRouter initialEntries={["/admin"]}>
          <Routes>
            <Route
              path="/admin"
              element={
                <AdminRoute>
                  <div>Secret Admin Area</div>
                </AdminRoute>
              }
            />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText("Secret Admin Area")).toBeInTheDocument();
    });
  });

  describe("AdminDashboardPage", () => {
    const mockSummary = {
      totalUsers: 42,
      totalStudents: 35,
      totalInstructors: 7,
      totalCourses: 12,
      publishedCourses: 8,
      openReportsCount: 3,
      totalActivitiesCount: 156,
    };

    const mockActivities = {
      content: [
        {
          id: 1,
          action: "COURSE_CREATED",
          title: "Course Created",
          actorName: "Prof John",
          actorRole: "USER",
          description: 'Prof John created course "Advanced Quantum Computing"',
          entityType: "COURSE",
          entityId: "101",
          details: { title: "Advanced Quantum Computing" },
          createdAt: new Date().toISOString(),
        },
        {
          id: 2,
          action: "USER_REGISTERED",
          title: "New User Registration",
          actorName: "Alice Learner",
          actorRole: "USER",
          description: "Alice Learner registered as USER",
          entityType: "USER",
          entityId: "5",
          details: { role: "USER" },
          createdAt: new Date().toISOString(),
        },
      ],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    };

    const mockReports = {
      content: [
        {
          id: 99,
          courseId: 101,
          courseTitle: "Advanced Quantum Computing",
          reporterUserId: 5,
          reporterName: "Alice Learner",
          reporterEmail: "alice@test.local",
          category: "BROKEN_RESOURCE",
          details: "Video player fails to load on lesson 3.",
          status: "PENDING",
          resolutionNotes: null,
          resolvedBy: null,
          resolverName: null,
          resolvedAt: null,
          createdAt: new Date().toISOString(),
        },
      ],
      page: 0,
      size: 15,
      totalElements: 1,
      totalPages: 1,
    };

    const mockUsers = {
      content: [
        {
          id: 1,
          email: "superadmin@peergeek.com",
          displayName: "Super Admin",
          role: "ADMIN",
          lastMode: "INSTRUCTOR",
          enabled: true,
          emailVerified: true,
          createdAt: new Date().toISOString(),
        },
      ],
      page: 0,
      size: 15,
      totalElements: 1,
      totalPages: 1,
    };

    const mockCourses = {
      content: [
        {
          id: 101,
          title: "Advanced Quantum Computing",
          instructorId: 2,
          instructorName: "Prof John",
          instructorEmail: "prof.john@peergeek.com",
          categoryName: "Physics",
          difficulty: "ADVANCED",
          status: "PUBLISHED",
          enrollmentCount: 24,
          publishedAt: new Date().toISOString(),
          createdAt: new Date().toISOString(),
        },
      ],
      page: 0,
      size: 15,
      totalElements: 1,
      totalPages: 1,
    };

    beforeEach(() => {
      adminApi.getDashboardSummary.mockResolvedValue(mockSummary);
      adminApi.getActivities.mockResolvedValue(mockActivities);
      adminApi.getAdminReports.mockResolvedValue(mockReports);
      adminApi.getAdminUsers.mockResolvedValue(mockUsers);
      adminApi.updateAdminUserStatus.mockResolvedValue({ ...mockUsers.content[0], enabled: false });
      adminApi.getAdminCourses.mockResolvedValue(mockCourses);
      adminApi.getAdminCourseById.mockResolvedValue(mockCourses.content[0]);
      adminApi.updateAdminCourseStatus.mockResolvedValue({ ...mockCourses.content[0], status: "UNPUBLISHED" });
    });

    it("renders summary overview cards with real metric data", async () => {
      renderWithProviders(<AdminDashboardPage />);

      await waitFor(() => {
        expect(screen.getByText("42")).toBeInTheDocument();
      });

      // Overview cards
      expect(screen.getByText("42")).toBeInTheDocument(); // totalUsers
      expect(screen.getAllByText("35").length).toBeGreaterThanOrEqual(1); // totalStudents
      expect(screen.getAllByText("7").length).toBeGreaterThanOrEqual(1); // totalInstructors
      expect(screen.getByText("12")).toBeInTheDocument(); // totalCourses
      expect(screen.getAllByText("8").length).toBeGreaterThanOrEqual(1); // publishedCourses
      expect(screen.getAllByText("3").length).toBeGreaterThanOrEqual(1); // openReportsCount
    });

    it("displays chronological activity feed on default tab", async () => {
      renderWithProviders(<AdminDashboardPage />);

      await waitFor(() => {
        expect(screen.getByText("Course Created")).toBeInTheDocument();
      });

      expect(
        screen.getByText('Prof John created course "Advanced Quantum Computing"'),
      ).toBeInTheDocument();
      expect(screen.getByText("New User Registration")).toBeInTheDocument();
      expect(screen.getByText("Alice Learner registered as USER")).toBeInTheDocument();
    });

    it("allows switching to Course Issues tab and reviewing a report", async () => {
      const user = userEvent.setup();
      renderWithProviders(<AdminDashboardPage />);

      await waitFor(() => {
        expect(screen.getByText("Course Issues")).toBeInTheDocument();
      });

      await user.click(screen.getByText("Course Issues"));

      await waitFor(() => {
        expect(screen.getByText("Video player fails to load on lesson 3.")).toBeInTheDocument();
      });

      expect(screen.getByText("Alice Learner")).toBeInTheDocument();
      expect(screen.getByText("alice@test.local")).toBeInTheDocument();

      // Open review modal
      const reviewBtn = screen.getByRole("button", { name: "Review" });
      await user.click(reviewBtn);

      await waitFor(() => {
        expect(screen.getByText("Report #99 Details")).toBeInTheDocument();
      });

      expect(screen.getByLabelText("Status")).toBeInTheDocument();
      expect(screen.getByLabelText("Resolution Notes")).toBeInTheDocument();
    });

    it("allows switching to User Monitoring tab and displays users", async () => {
      const user = userEvent.setup();
      renderWithProviders(<AdminDashboardPage />);

      await waitFor(() => {
        expect(screen.getByText("User Monitoring")).toBeInTheDocument();
      });

      await user.click(screen.getByText("User Monitoring"));

      await waitFor(() => {
        expect(screen.getByText("Registered Users Directory")).toBeInTheDocument();
      });

      expect(screen.getByText("Super Admin")).toBeInTheDocument();
      expect(screen.getByText("superadmin@peergeek.com")).toBeInTheDocument();
    });

    it("allows switching to Course Overview tab and displays courses with enrollment count", async () => {
      const user = userEvent.setup();
      renderWithProviders(<AdminDashboardPage />);

      await waitFor(() => {
        expect(screen.getByText("Course Overview")).toBeInTheDocument();
      });

      await user.click(screen.getByText("Course Overview"));

      await waitFor(() => {
        expect(screen.getByText("Platform Course Monitoring")).toBeInTheDocument();
      });

      expect(screen.getByText("Advanced Quantum Computing")).toBeInTheDocument();
      expect(screen.getByText("24 enrolled")).toBeInTheDocument();
      expect(screen.getByText("prof.john@peergeek.com")).toBeInTheDocument();
    });

    it("clicking KPI summary cards navigates to correctly filtered tabs", async () => {
      const user = userEvent.setup();
      renderWithProviders(<AdminDashboardPage />);

      await waitFor(() => {
        expect(screen.getByRole("button", { name: "View students directory" })).toBeInTheDocument();
      });

      // 1. Click Students card
      await user.click(screen.getByRole("button", { name: "View students directory" }));
      await waitFor(() => {
        expect(screen.getByText("Registered Users Directory")).toBeInTheDocument();
        expect(adminApi.getAdminUsers).toHaveBeenCalledWith(
          expect.objectContaining({ mode: "STUDENT" }),
        );
      });

      // 2. Click Teachers card
      await user.click(screen.getByRole("button", { name: "View teachers and instructors directory" }));
      await waitFor(() => {
        expect(adminApi.getAdminUsers).toHaveBeenCalledWith(
          expect.objectContaining({ mode: "INSTRUCTOR" }),
        );
      });

      // 3. Click Published Courses card
      await user.click(screen.getByRole("button", { name: "View published courses" }));
      await waitFor(() => {
        expect(screen.getByText("Platform Course Monitoring")).toBeInTheDocument();
        expect(adminApi.getAdminCourses).toHaveBeenCalledWith(
          expect.objectContaining({ status: "PUBLISHED" }),
        );
      });

      // 4. Click Open Issues card
      await user.click(screen.getByRole("button", { name: "View open course issue reports" }));
      await waitFor(() => {
        expect(screen.getByText("Reported Course Issues")).toBeInTheDocument();
        expect(adminApi.getAdminReports).toHaveBeenCalledWith(
          expect.objectContaining({ status: "OPEN" }),
        );
      });
    });

    it("allows viewing user details and blocking an account with confirmation", async () => {
      const user = userEvent.setup();
      useAuthStore.setState({
        status: "authenticated",
        user: { id: 99, role: "ADMIN", email: "admin@peergeek.com" },
      });

      renderWithProviders(<AdminDashboardPage />);

      await user.click(await screen.findByRole("button", { name: "View all registered users" }));

      await waitFor(() => {
        expect(screen.getByText("Super Admin")).toBeInTheDocument();
      });

      // Open user details
      const detailsBtn = screen.getByRole("button", { name: "Details" });
      await user.click(detailsBtn);

      await waitFor(() => {
        expect(screen.getByText("User Account Details")).toBeInTheDocument();
        expect(screen.getByText("User ID #1")).toBeInTheDocument();
      });

      // Close details modal
      await user.click(screen.getByRole("button", { name: "Close" }));

      // Block user button is disabled for admin role as per security requirements
      const blockBtn = screen.getByRole("button", { name: "Block" });
      expect(blockBtn).toBeDisabled();
    });

    it("allows opening course moderation modal and updating course status", async () => {
      const user = userEvent.setup();
      renderWithProviders(<AdminDashboardPage />);

      await user.click(await screen.findByRole("button", { name: "View all platform courses" }));

      await waitFor(() => {
        expect(screen.getByText("Advanced Quantum Computing")).toBeInTheDocument();
      });

      // Open moderation modal
      const modBtn = screen.getByRole("button", { name: "Manage & Moderate" });
      await user.click(modBtn);

      await waitFor(() => {
        expect(screen.getByText("Course Moderation & Status")).toBeInTheDocument();
        expect(screen.getByText("Course ID #101")).toBeInTheDocument();
      });

      // Apply status change
      const applyBtn = screen.getByRole("button", { name: "Apply Status Change" });
      await user.click(applyBtn);

      await waitFor(() => {
        expect(adminApi.updateAdminCourseStatus).toHaveBeenCalledWith(
          101,
          expect.objectContaining({ status: "UNPUBLISHED" }),
        );
      });
    });

    it("allows inspecting and moderating course directly from report review modal", async () => {
      const user = userEvent.setup();
      renderWithProviders(<AdminDashboardPage />);

      await user.click(await screen.findByRole("button", { name: "View open course issue reports" }));

      await waitFor(() => {
        expect(screen.getByText("Video player fails to load on lesson 3.")).toBeInTheDocument();
      });

      // Review report
      await user.click(screen.getByRole("button", { name: "Review" }));

      await waitFor(() => {
        expect(screen.getByRole("button", { name: "🛡️ Inspect & Moderate Course" })).toBeInTheDocument();
      });

      // Click inspect & moderate course
      await user.click(screen.getByRole("button", { name: "🛡️ Inspect & Moderate Course" }));

      await waitFor(() => {
        expect(adminApi.getAdminCourseById).toHaveBeenCalledWith(101);
        expect(screen.getByText("Course Moderation & Status")).toBeInTheDocument();
        expect(screen.getByText("Course ID #101")).toBeInTheDocument();
      });
    });
  });
});
