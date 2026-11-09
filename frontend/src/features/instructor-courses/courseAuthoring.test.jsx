import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../shared/components/Toast";
import { visibleNavigation } from "../../app/registry/navigation";
import navigation from "./navigation";
import {
  createModule,
  getCourse,
  getOutline,
  listCategories,
  listCourses,
  reorderLessons,
} from "./courseApi";
import { CourseDashboardPage, CourseEditorPage } from "./CoursePages";
import { CourseOutlineBuilder } from "./CourseOutlineBuilder";
import { getInstructorCourseSettings } from "../doubtsession/doubtSessionApi";

vi.mock("./courseApi", () => ({
  createCourse: vi.fn(),
  createLesson: vi.fn(),
  createModule: vi.fn(),
  deleteCourse: vi.fn(),
  deleteLesson: vi.fn(),
  deleteModule: vi.fn(),
  getCourse: vi.fn(),
  getLessonContent: vi.fn(),
  getOutline: vi.fn(),
  listCategories: vi.fn(),
  listCourses: vi.fn(),
  moveLesson: vi.fn(),
  reorderLessons: vi.fn(),
  reorderModules: vi.fn(),
  updateCourse: vi.fn(),
  updateLesson: vi.fn(),
  updateModule: vi.fn(),
}));

vi.mock("../doubtsession/doubtSessionApi", () => ({
  getInstructorCourseSettings: vi.fn(),
  updateInstructorCourseSettings: vi.fn(),
}));

function renderWithProviders(ui, { initialEntries = ["/"] } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <QueryClientProvider client={queryClient}>
        <ToastProvider>{ui}</ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("instructor course authoring", () => {
  it("shows the authenticated course list and handles an empty state", async () => {
    listCourses.mockResolvedValueOnce({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    renderWithProviders(<CourseDashboardPage />);
    expect(
      await screen.findByRole("heading", { name: "My Courses" }),
    ).toBeVisible();
    expect(await screen.findByText("No courses yet")).toBeVisible();
  });

  it("shows courses with status and structure counts", async () => {
    listCourses.mockResolvedValueOnce({
      content: [
        {
          id: 17,
          title: "Accessible Web Design",
          status: "DRAFT",
          moduleCount: 2,
          lessonCount: 5,
          updatedAt: "2026-10-16T00:00:00Z",
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    renderWithProviders(<CourseDashboardPage />);
    expect(
      await screen.findByRole("link", { name: "Accessible Web Design" }),
    ).toHaveAttribute("href", "/instructor/courses/17");
    expect(screen.getByText("2 modules · 5 lessons")).toBeVisible();
    expect(screen.getByText("DRAFT")).toBeVisible();
  });

  it("exposes instructor navigation only in INSTRUCTOR UI mode", () => {
    expect(
      visibleNavigation(navigation, {
        isAuthenticated: false,
        userMode: "INSTRUCTOR",
      }),
    ).toHaveLength(0);
    expect(
      visibleNavigation(navigation, {
        isAuthenticated: true,
        userMode: "STUDENT",
      }),
    ).toHaveLength(0);
    expect(
      visibleNavigation(navigation, {
        isAuthenticated: true,
        userMode: "INSTRUCTOR",
      }).map((item) => item.label),
    ).toEqual(["My Courses"]);
  });

  it("adds a module and uses the accessible lesson reorder buttons", async () => {
    const user = userEvent.setup();
    const module = {
      id: 4,
      title: "Module One",
      description: null,
      position: 0,
      lessons: [
        {
          id: 31,
          title: "Lesson One",
          summary: null,
          position: 0,
          hasContent: false,
          wordCount: 0,
        },
        {
          id: 32,
          title: "Lesson Two",
          summary: null,
          position: 1,
          hasContent: false,
          wordCount: 0,
        },
      ],
    };
    const { getOutline } = await import("./courseApi");
    getOutline.mockResolvedValue({ courseId: 8, modules: [module] });
    createModule.mockResolvedValue({ id: 5 });
    reorderLessons.mockResolvedValue(undefined);
    renderWithProviders(<CourseOutlineBuilder course={{ id: 8 }} />);

    await screen.findByRole("heading", { name: "Module One" });
    await user.type(screen.getByLabelText("New module"), "Second module");
    await user.click(screen.getByRole("button", { name: "Add module" }));
    await waitFor(() =>
      expect(createModule).toHaveBeenCalledWith(8, {
        title: "Second module",
        description: "",
      }),
    );

    await user.click(
      screen.getByRole("button", { name: "Move Lesson Two up" }),
    );
    await waitFor(() =>
      expect(reorderLessons).toHaveBeenCalledWith(4, [32, 31]),
    );
  });

  it("displays a recoverable list error", async () => {
    listCourses.mockRejectedValueOnce(new Error("offline"));
    renderWithProviders(<CourseDashboardPage />);
    expect(
      await screen.findByRole("heading", { name: "Courses unavailable" }),
    ).toBeVisible();
    expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
  });

  describe("CourseEditorPage tabs", () => {
    const mockCourse = {
      id: 42,
      title: "Interactive Fullstack Architecture",
      subtitle: "Learn modern patterns",
      description: "Comprehensive guide to fullstack",
      status: "DRAFT",
      version: 1,
      category: null,
      difficulty: "INTERMEDIATE",
      topics: [],
    };

    beforeEach(() => {
      getCourse.mockResolvedValue(mockCourse);
      listCategories.mockResolvedValue([]);
      getOutline.mockResolvedValue({ courseId: 42, modules: [] });
      getInstructorCourseSettings.mockResolvedValue({
        enabled: true,
        available: true,
        hourlyRatePgc: 750,
      });
    });

    it("defaults to details tab when tab query param is omitted", async () => {
      renderWithProviders(
        <Routes>
          <Route
            path="/instructor/courses/:courseId"
            element={<CourseEditorPage />}
          />
        </Routes>,
        { initialEntries: ["/instructor/courses/42"] },
      );

      expect(
        await screen.findByRole("heading", {
          name: "Interactive Fullstack Architecture",
        }),
      ).toBeVisible();
      const detailsTab = screen.getByRole("tab", { name: "Details" });
      expect(detailsTab).toHaveAttribute("data-state", "active");
      expect(screen.getByLabelText("Course title")).toBeVisible();
    });

    it("activates details tab when tab=details", async () => {
      renderWithProviders(
        <Routes>
          <Route
            path="/instructor/courses/:courseId"
            element={<CourseEditorPage />}
          />
        </Routes>,
        { initialEntries: ["/instructor/courses/42?tab=details"] },
      );

      expect(
        await screen.findByRole("heading", {
          name: "Interactive Fullstack Architecture",
        }),
      ).toBeVisible();
      expect(screen.getByRole("tab", { name: "Details" })).toHaveAttribute(
        "data-state",
        "active",
      );
      expect(screen.getByLabelText("Course title")).toBeVisible();
    });

    it("activates outline tab when tab=outline", async () => {
      renderWithProviders(
        <Routes>
          <Route
            path="/instructor/courses/:courseId"
            element={<CourseEditorPage />}
          />
        </Routes>,
        { initialEntries: ["/instructor/courses/42?tab=outline"] },
      );

      expect(
        await screen.findByRole("heading", {
          name: "Interactive Fullstack Architecture",
        }),
      ).toBeVisible();
      expect(screen.getByRole("tab", { name: "Outline" })).toHaveAttribute(
        "data-state",
        "active",
      );
      expect(
        await screen.findByRole("button", { name: "Add module" }),
      ).toBeVisible();
    });

    it("activates doubts tab and displays InstructorCourseSettings when tab=doubts (refresh / direct URL)", async () => {
      renderWithProviders(
        <Routes>
          <Route
            path="/instructor/courses/:courseId"
            element={<CourseEditorPage />}
          />
        </Routes>,
        { initialEntries: ["/instructor/courses/42?tab=doubts"] },
      );

      expect(
        await screen.findByRole("heading", {
          name: "Interactive Fullstack Architecture",
        }),
      ).toBeVisible();
      const doubtsTab = screen.getByRole("tab", { name: "Doubt Sessions 💬" });
      expect(doubtsTab).toHaveAttribute("data-state", "active");
      expect(
        await screen.findByTestId("instructor-course-doubt-settings"),
      ).toBeVisible();
      expect(
        screen.getByText(/Live Doubt[- ]Clearing Settings/i),
      ).toBeVisible();
      expect(screen.getByLabelText("Enable Live Doubt Sessions")).toBeVisible();
    });

    it("defaults to details tab when tab query param is invalid", async () => {
      renderWithProviders(
        <Routes>
          <Route
            path="/instructor/courses/:courseId"
            element={<CourseEditorPage />}
          />
        </Routes>,
        { initialEntries: ["/instructor/courses/42?tab=unknownTab"] },
      );

      expect(
        await screen.findByRole("heading", {
          name: "Interactive Fullstack Architecture",
        }),
      ).toBeVisible();
      expect(screen.getByRole("tab", { name: "Details" })).toHaveAttribute(
        "data-state",
        "active",
      );
      expect(screen.getByLabelText("Course title")).toBeVisible();
    });

    it("navigates through all three tabs on click", async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <Routes>
          <Route
            path="/instructor/courses/:courseId"
            element={<CourseEditorPage />}
          />
        </Routes>,
        { initialEntries: ["/instructor/courses/42"] },
      );

      expect(
        await screen.findByRole("heading", {
          name: "Interactive Fullstack Architecture",
        }),
      ).toBeVisible();
      const detailsTab = screen.getByRole("tab", { name: "Details" });
      const outlineTab = screen.getByRole("tab", { name: "Outline" });
      const doubtsTab = screen.getByRole("tab", { name: "Doubt Sessions 💬" });

      expect(detailsTab).toHaveAttribute("data-state", "active");

      // Click Outline
      await user.click(outlineTab);
      expect(outlineTab).toHaveAttribute("data-state", "active");
      expect(detailsTab).toHaveAttribute("data-state", "inactive");
      expect(
        await screen.findByRole("button", { name: "Add module" }),
      ).toBeVisible();

      // Click Doubt Sessions
      await user.click(doubtsTab);
      expect(doubtsTab).toHaveAttribute("data-state", "active");
      expect(outlineTab).toHaveAttribute("data-state", "inactive");
      expect(
        await screen.findByTestId("instructor-course-doubt-settings"),
      ).toBeVisible();
      expect(
        screen.getByText(/Live Doubt[- ]Clearing Settings/i),
      ).toBeVisible();

      // Click Details
      await user.click(detailsTab);
      expect(detailsTab).toHaveAttribute("data-state", "active");
      expect(doubtsTab).toHaveAttribute("data-state", "inactive");
      expect(screen.getByLabelText("Course title")).toBeVisible();
    });
  });
});
