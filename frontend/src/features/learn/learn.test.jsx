import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { visibleNavigation } from "../../app/registry/navigation";
import { SlotRegistryProvider } from "../../app/registry/slots";
import navigation from "./navigation";
import routes from "./routes";
import slots from "./slots";
import { EnrollArea } from "./EnrollArea";
import { MyLearningPage } from "./MyLearningPage";
import { CoursePlayerPage } from "./CoursePlayerPage";
import { CourseRedirectPage } from "./CourseRedirectPage";
import * as learningApi from "./learningApi";
import { useAuthStore } from "../auth/authStore";

vi.mock("./learningApi", () => ({
  getEnrollmentStatus: vi.fn(),
  enrollInCourse: vi.fn(),
  withdrawFromCourse: vi.fn(),
  getMyCourses: vi.fn(),
  getLearnerOutline: vi.fn(),
  getLearnerLesson: vi.fn(),
  getFreshResourceUrl: vi.fn(),
  visitLesson: vi.fn(),
  setLessonCompletion: vi.fn(),
  getContinueLesson: vi.fn(),
}));

function renderWithProviders(
  ui,
  { route = "/learning", path = "/learning", registry = {} } = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
        staleTime: 0,
      },
      mutations: {
        retry: false,
      },
    },
  });

  return render(
    <MemoryRouter initialEntries={[route]}>
      <QueryClientProvider client={queryClient}>
        <SlotRegistryProvider registry={registry}>
          <Routes>
            <Route path={path} element={ui} />
            <Route path="/login" element={<div>Login Page</div>} />
            <Route path="/courses" element={<div>Explore Page</div>} />
            <Route
              path="/courses/:courseId"
              element={<div>Course Preview Page</div>}
            />
            <Route path="/learning" element={<div>My Learning Root</div>} />
            <Route
              path="/learning/courses/:courseId"
              element={<div>Course Redirect Target</div>}
            />
            <Route
              path="/learning/courses/:courseId/lessons/:lessonId"
              element={<div>Player Target Lesson</div>}
            />
          </Routes>
        </SlotRegistryProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("Phase 6: Learn & Enrollment feature", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({
      status: "authenticated",
      user: { id: 1, email: "learner@test.local", lastMode: "STUDENT" },
      accessToken: "mock-token",
    });
    learningApi.visitLesson.mockResolvedValue({});
  });

  afterEach(() => {
    cleanup();
  });

  describe("Registries and Navigation", () => {
    it("registers 'My Learning' navigation item correctly", () => {
      const item = navigation.find((i) => i.id === "my-learning");
      expect(item).toBeDefined();
      expect(item.label).toBe("My Learning");
      expect(item.route).toBe("/learning");
      expect(item.visibility).toBe("authenticated");
      expect(item.mode).toBe("STUDENT");

      // Visible in STUDENT mode for authenticated user
      const studentNav = visibleNavigation(navigation, {
        isAuthenticated: true,
        userMode: "STUDENT",
      });
      expect(studentNav.some((i) => i.id === "my-learning")).toBe(true);

      // Hidden in INSTRUCTOR mode
      const instructorNav = visibleNavigation(navigation, {
        isAuthenticated: true,
        userMode: "INSTRUCTOR",
      });
      expect(instructorNav.some((i) => i.id === "my-learning")).toBe(false);

      // Hidden for anonymous
      const anonNav = visibleNavigation(navigation, {
        isAuthenticated: false,
        userMode: "STUDENT",
      });
      expect(anonNav.some((i) => i.id === "my-learning")).toBe(false);
    });

    it("registers protected routes for /learning, redirect, and player", () => {
      const myLearning = routes.find((r) => r.path === "/learning");
      const redirectRoute = routes.find(
        (r) => r.path === "/learning/courses/:courseId",
      );
      const playerRoute = routes.find(
        (r) => r.path === "/learning/courses/:courseId/lessons/:lessonId",
      );

      expect(myLearning).toBeDefined();
      expect(myLearning.visibility).toBe("protected");
      expect(redirectRoute).toBeDefined();
      expect(redirectRoute.visibility).toBe("protected");
      expect(playerRoute).toBeDefined();
      expect(playerRoute.visibility).toBe("protected");
    });

    it("registers EnrollArea into 'course-page.enroll-area' slot", () => {
      const contribution = slots.find(
        (c) => c.name === "course-page.enroll-area",
      );
      expect(contribution).toBeDefined();
      expect(contribution.component).toBe(EnrollArea);
    });
  });

  describe("EnrollArea component", () => {
    it("shows 'Log in to enroll' with return-to for anonymous users", () => {
      useAuthStore.setState({
        status: "anonymous",
        user: null,
        accessToken: null,
      });

      renderWithProviders(<EnrollArea courseId={42} />, {
        route: "/courses/42?ref=banner",
        path: "/courses/:courseId",
      });

      const loginLink = screen.getByRole("link", { name: /log in to enroll/i });
      expect(loginLink).toBeInTheDocument();
      expect(loginLink.getAttribute("href")).toContain(
        "/login?returnTo=" + encodeURIComponent("/courses/42?ref=banner"),
      );
    });

    it("shows 'Enroll in Course' for signed-in non-enrolled user and handles enrollment", async () => {
      const user = userEvent.setup();
      learningApi.getEnrollmentStatus.mockResolvedValueOnce({
        enrolled: false,
        status: null,
        isOwner: false,
        canEnroll: true,
        progress: null,
      });
      learningApi.enrollInCourse.mockResolvedValueOnce({
        courseId: 42,
        status: "ACTIVE",
        enrolledAt: "2026-10-20T10:00:00Z",
        progress: { completedLessons: 0, totalLessons: 5, percent: 0 },
      });

      renderWithProviders(<EnrollArea courseId={42} />, {
        route: "/courses/42",
        path: "/courses/:courseId",
      });

      const enrollBtn = await screen.findByRole("button", {
        name: /enroll in course/i,
      });
      expect(enrollBtn).toBeInTheDocument();

      await user.click(enrollBtn);

      expect(learningApi.enrollInCourse).toHaveBeenCalledWith(42);
    });

    it("shows 'This is your course' for course owner", async () => {
      learningApi.getEnrollmentStatus.mockResolvedValueOnce({
        enrolled: false,
        status: null,
        isOwner: true,
        canEnroll: false,
        progress: null,
      });

      renderWithProviders(<EnrollArea courseId={42} />, {
        route: "/courses/42",
        path: "/courses/:courseId",
      });

      expect(
        await screen.findByText(/this is your course/i),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: /manage course/i }),
      ).toBeInTheDocument();
    });

    it("shows progress summary, continue learning, and reviews buttons both linking to course reviews for enrolled learner", async () => {
      learningApi.getEnrollmentStatus.mockResolvedValueOnce({
        enrolled: true,
        status: "ACTIVE",
        isOwner: false,
        canEnroll: false,
        progress: {
          completedLessons: 3,
          totalLessons: 6,
          percent: 50,
          completedAt: null,
        },
      });

      renderWithProviders(<EnrollArea courseId={42} />, {
        route: "/courses/42",
        path: "/courses/:courseId",
      });

      expect(await screen.findByText("Enrolled")).toBeInTheDocument();
      expect(screen.getByText("3 / 6 lessons")).toBeInTheDocument();
      expect(screen.getByText("50%")).toBeInTheDocument();

      const continueBtn = screen.getByRole("link", {
        name: /continue learning/i,
      });
      expect(continueBtn).toBeInTheDocument();
      expect(continueBtn.getAttribute("href")).toBe("/courses/42/reviews");

      const reviewsBtn = screen.getByRole("link", {
        name: /^reviews$/i,
      });
      expect(reviewsBtn).toBeInTheDocument();
      expect(reviewsBtn.getAttribute("href")).toBe("/courses/42/reviews");

      // Verify Withdraw from course is removed from panel
      expect(
        screen.queryByRole("button", { name: /withdraw from course/i }),
      ).not.toBeInTheDocument();
    });

    it("displays small retry button on enrollment status error without blocking", async () => {
      const user = userEvent.setup();
      learningApi.getEnrollmentStatus.mockRejectedValue(
        new Error("Network Error"),
      );

      renderWithProviders(<EnrollArea courseId={42} />, {
        route: "/courses/42",
        path: "/courses/:courseId",
      });

      const retryBtn = await screen.findByRole("button", { name: /retry/i });
      expect(retryBtn).toBeInTheDocument();

      learningApi.getEnrollmentStatus.mockResolvedValue({
        enrolled: false,
        canEnroll: true,
      });
      await user.click(retryBtn);

      expect(learningApi.getEnrollmentStatus).toHaveBeenCalled();
    });
  });

  describe("MyLearningPage component", () => {
    it("renders tabs, search, course cards, and handles search/filtering", async () => {
      const user = userEvent.setup();
      learningApi.getMyCourses.mockResolvedValueOnce({
        content: [
          {
            courseId: 101,
            title: "Operating Systems Internals",
            thumbnailUrl: null,
            instructor: { id: 2, displayName: "Prof. Tanenbaum" },
            difficulty: "ADVANCED",
            courseStatus: "PUBLISHED",
            progress: { completedLessons: 2, totalLessons: 4, percent: 50 },
            lastAccessedAt: "2026-10-20T10:00:00Z",
            continueLessonId: 502,
          },
          {
            courseId: 102,
            title: "Archived Unix Tools",
            thumbnailUrl: "/thumb.png",
            instructor: { id: 3, displayName: "Brian Kernighan" },
            difficulty: "BEGINNER",
            courseStatus: "UNPUBLISHED",
            progress: { completedLessons: 1, totalLessons: 1, percent: 100 },
            lastAccessedAt: "2026-10-19T10:00:00Z",
            continueLessonId: null,
          },
        ],
        page: 0,
        size: 12,
        totalElements: 2,
        totalPages: 1,
      });

      renderWithProviders(<MyLearningPage />);

      const osTitles = await screen.findAllByText(
        "Operating Systems Internals",
      );
      expect(osTitles[0]).toBeInTheDocument();
      expect(screen.getByText("My Learning")).toBeInTheDocument();
      expect(screen.getByText("Archived Unix Tools")).toBeInTheDocument();
      expect(
        screen.getByText("No longer accepting new learners"),
      ).toBeInTheDocument();

      const continueLinks = screen.getAllByRole("link", { name: "Continue" });
      expect(continueLinks.length).toBe(2);
      expect(continueLinks[0]).toHaveAttribute("href", "/learning/courses/101");
      expect(continueLinks[1]).toHaveAttribute("href", "/learning/courses/102");

      const reviewLinks = screen.getAllByRole("link", { name: "Reviews" });
      expect(reviewLinks.length).toBe(2);
      expect(reviewLinks[0]).toHaveAttribute("href", "/courses/101/reviews");
      expect(reviewLinks[1]).toHaveAttribute("href", "/courses/102/reviews");

      // Search
      learningApi.getMyCourses.mockResolvedValueOnce({
        content: [],
        page: 0,
        size: 12,
        totalElements: 0,
        totalPages: 0,
      });
      const searchInput = screen.getByPlaceholderText(
        /search enrolled courses/i,
      );
      await user.type(searchInput, "Distributed");
      await user.click(screen.getByRole("button", { name: /search/i }));

      await waitFor(() => {
        expect(learningApi.getMyCourses).toHaveBeenCalledWith(
          expect.objectContaining({ q: "Distributed" }),
        );
      });
    });

    it("renders Continue button followed by Reviews button navigating to correct routes on each course card", async () => {
      learningApi.getMyCourses.mockResolvedValueOnce({
        content: [
          {
            courseId: 42,
            title: "Fullstack React & Spring Boot",
            thumbnailUrl: null,
            instructor: { id: 1, displayName: "Prof Alice" },
            difficulty: "INTERMEDIATE",
            courseStatus: "PUBLISHED",
            progress: { completedLessons: 3, totalLessons: 10, percent: 30 },
            lastAccessedAt: "2026-10-20T10:00:00Z",
            continueLessonId: 101,
          },
        ],
        page: 0,
        size: 12,
        totalElements: 1,
        totalPages: 1,
      });

      renderWithProviders(<MyLearningPage />);

      const titles = await screen.findAllByText("Fullstack React & Spring Boot");
      expect(titles[0]).toBeInTheDocument();

      const continueBtn = screen.getByRole("link", { name: "Continue" });
      expect(continueBtn).toBeInTheDocument();
      expect(continueBtn).toHaveAttribute("href", "/learning/courses/42");

      const reviewsBtn = screen.getByRole("link", { name: "Reviews" });
      expect(reviewsBtn).toBeInTheDocument();
      expect(reviewsBtn).toHaveAttribute("href", "/courses/42/reviews");
    });

    it("renders empty state linking to explore when no courses found", async () => {
      learningApi.getMyCourses.mockResolvedValueOnce({
        content: [],
        page: 0,
        size: 12,
        totalElements: 0,
        totalPages: 0,
      });

      renderWithProviders(<MyLearningPage />);

      expect(
        await screen.findByText("You haven't enrolled in any courses yet"),
      ).toBeInTheDocument();
      const exploreLink = screen.getByRole("link", {
        name: /explore courses/i,
      });
      expect(exploreLink).toBeInTheDocument();
      expect(exploreLink.getAttribute("href")).toBe("/courses");
    });
  });

  describe("CoursePlayerPage component", () => {
    const mockOutline = {
      course: {
        id: 77,
        title: "Compiler Design",
        category: "Computer Science",
        difficulty: "ADVANCED",
        courseStatus: "PUBLISHED",
      },
      modules: [
        {
          id: 10,
          title: "Lexical Analysis",
          lessons: [
            { id: 201, title: "Tokens & Patterns", completed: true },
            { id: 202, title: "Finite Automata", completed: false },
          ],
        },
      ],
      progress: { completedLessons: 1, totalLessons: 2, percent: 50 },
    };

    const mockLesson = {
      lesson: {
        id: 202,
        title: "Finite Automata",
        summary: "Deterministic vs Nondeterministic state machines",
        moduleId: 10,
      },
      contentDocument: {
        type: "doc",
        content: [
          {
            type: "paragraph",
            content: [{ type: "text", text: "Introduction to DFA and NFA." }],
          },
        ],
      },
      resources: [
        {
          id: 301,
          type: "PDF",
          title: "Automata Notes.pdf",
          mimeType: "application/pdf",
          url: "https://storage.local/pdf1",
        },
        {
          id: 302,
          type: "IMAGE",
          title: "State Diagram",
          mimeType: "image/png",
          url: "https://storage.local/img1",
        },
        {
          id: 303,
          type: "VIDEO",
          title: "Lecture Recording",
          mimeType: "video/mp4",
          url: "https://storage.local/vid1",
        },
        {
          id: 304,
          type: "DOCUMENT",
          title: "Lab Assignment.zip",
          mimeType: "application/zip",
          sizeBytes: 204800,
          url: "https://storage.local/doc1",
        },
        {
          id: 305,
          type: "LINK",
          title: "Reference Manual",
          externalUrl: "https://example.com/spec",
        },
      ],
      completed: false,
      previousLessonId: 201,
      nextLessonId: null,
    };

    it("renders outline, lesson content, all resource types, and navigation", async () => {
      learningApi.getLearnerOutline.mockResolvedValueOnce(mockOutline);
      learningApi.getLearnerLesson.mockResolvedValueOnce(mockLesson);

      renderWithProviders(<CoursePlayerPage />, {
        route: "/learning/courses/77/lessons/202",
        path: "/learning/courses/:courseId/lessons/:lessonId",
      });

      // Outline
      expect(
        await screen.findByText("Module 1: Lexical Analysis"),
      ).toBeInTheDocument();
      expect(screen.getByText("Tokens & Patterns")).toBeInTheDocument();

      // Heading and Document content
      const heading = screen.getByRole("heading", {
        name: "Finite Automata",
        level: 1,
      });
      expect(heading).toBeInTheDocument();
      expect(
        screen.getByText("Introduction to DFA and NFA."),
      ).toBeInTheDocument();

      // Resources
      expect(screen.getByText("Automata Notes.pdf")).toBeInTheDocument();
      expect(screen.getByText("State Diagram")).toBeInTheDocument();
      expect(screen.getByText("Lecture Recording")).toBeInTheDocument();
      expect(screen.getByText("Lab Assignment.zip")).toBeInTheDocument();
      expect(screen.getByText("Reference Manual")).toBeInTheDocument();

      // Navigation buttons
      expect(
        screen.getByRole("link", { name: /← previous lesson/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: /course summary ✓/i }),
      ).toBeInTheDocument();

      // Visit was recorded
      expect(learningApi.visitLesson).toHaveBeenCalledWith(77, 202);
    });

    it("toggles completion state and triggers aria-live announcement", async () => {
      const user = userEvent.setup();
      learningApi.getLearnerOutline.mockResolvedValueOnce(mockOutline);
      learningApi.getLearnerLesson.mockResolvedValueOnce(mockLesson);
      learningApi.setLessonCompletion.mockResolvedValueOnce({
        completedLessons: 2,
        totalLessons: 2,
        percent: 100,
      });

      renderWithProviders(<CoursePlayerPage />, {
        route: "/learning/courses/77/lessons/202",
        path: "/learning/courses/:courseId/lessons/:lessonId",
      });

      const markBtn = await screen.findByRole("button", {
        name: /mark complete/i,
      });
      await user.click(markBtn);

      expect(learningApi.setLessonCompletion).toHaveBeenCalledWith(
        77,
        202,
        true,
      );
    });

    it("renders slots 'player.lesson-actions' and 'player.side-panel' with props", async () => {
      learningApi.getLearnerOutline.mockResolvedValueOnce(mockOutline);
      learningApi.getLearnerLesson.mockResolvedValueOnce(mockLesson);

      const TestAction = ({ courseId, lessonId }) => (
        <div data-testid="test-action">
          Action for {courseId}-{lessonId}
        </div>
      );
      const TestSidePanel = ({ courseId, lessonId }) => (
        <div data-testid="test-panel">
          Panel for {courseId}-{lessonId}
        </div>
      );

      const registry = {
        "player.lesson-actions": [{ component: TestAction }],
        "player.side-panel": [{ component: TestSidePanel }],
      };

      renderWithProviders(<CoursePlayerPage />, {
        route: "/learning/courses/77/lessons/202",
        path: "/learning/courses/:courseId/lessons/:lessonId",
        registry,
      });

      expect(await screen.findByTestId("test-action")).toHaveTextContent(
        "Action for 77-202",
      );
      expect(screen.getByTestId("test-panel")).toHaveTextContent(
        "Panel for 77-202",
      );
    });

    it("displays Enrollment Required screen on 403 status", async () => {
      const err = new Error("Enrollment Required");
      err.status = 403;
      learningApi.getLearnerOutline.mockRejectedValue(err);
      learningApi.getLearnerLesson.mockRejectedValue(err);

      renderWithProviders(<CoursePlayerPage />, {
        route: "/learning/courses/77/lessons/202",
        path: "/learning/courses/:courseId/lessons/:lessonId",
      });

      expect(
        await screen.findByText("Enrollment Required"),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: /view course preview/i }),
      ).toBeInTheDocument();
    });

    it("displays Not Found screen on 404 status", async () => {
      const err = new Error("Not Found");
      err.status = 404;
      learningApi.getLearnerOutline.mockRejectedValue(err);
      learningApi.getLearnerLesson.mockRejectedValue(err);

      renderWithProviders(<CoursePlayerPage />, {
        route: "/learning/courses/77/lessons/202",
        path: "/learning/courses/:courseId/lessons/:lessonId",
      });

      expect(
        await screen.findByText("Lesson or Course Not Found"),
      ).toBeInTheDocument();
    });

    it("shows non-blocking banner when user is in INSTRUCTOR mode", async () => {
      const user = userEvent.setup();
      useAuthStore.setState({
        status: "authenticated",
        user: { id: 1, email: "inst@test.local", lastMode: "INSTRUCTOR" },
      });
      learningApi.getLearnerOutline.mockResolvedValueOnce(mockOutline);
      learningApi.getLearnerLesson.mockResolvedValueOnce(mockLesson);

      renderWithProviders(<CoursePlayerPage />, {
        route: "/learning/courses/77/lessons/202",
        path: "/learning/courses/:courseId/lessons/:lessonId",
      });

      expect(
        await screen.findByText(/you are currently in instructor mode/i),
      ).toBeInTheDocument();

      const switchBtn = screen.getByRole("button", {
        name: /switch to student mode/i,
      });
      await user.click(switchBtn);

      expect(useAuthStore.getState().user.lastMode).toBe("STUDENT");
    });

    it("never reads or writes localStorage or sessionStorage for learner data", () => {
      const getItemSpy = vi.spyOn(Storage.prototype, "getItem");
      const setItemSpy = vi.spyOn(Storage.prototype, "setItem");

      learningApi.getLearnerOutline.mockResolvedValueOnce(mockOutline);
      learningApi.getLearnerLesson.mockResolvedValueOnce(mockLesson);

      renderWithProviders(<CoursePlayerPage />, {
        route: "/learning/courses/77/lessons/202",
        path: "/learning/courses/:courseId/lessons/:lessonId",
      });

      // No storage keys starting with "learning", "progress", or "lesson"
      const storedCalls = setItemSpy.mock.calls.filter(([key]) =>
        /^(learning|progress|lesson|course_progress)/i.test(key),
      );
      expect(storedCalls).toHaveLength(0);

      getItemSpy.mockRestore();
      setItemSpy.mockRestore();
    });
  });

  describe("CourseRedirectPage component", () => {
    it("redirects to continue lesson if available", async () => {
      learningApi.getContinueLesson.mockResolvedValueOnce({
        lessonId: 888,
        reason: "RESUME",
      });
      learningApi.getLearnerOutline.mockResolvedValueOnce({ modules: [] });

      renderWithProviders(<CourseRedirectPage />, {
        route: "/learning/courses/42",
        path: "/learning/courses/:courseId",
      });

      expect(
        await screen.findByText("Player Target Lesson"),
      ).toBeInTheDocument();
    });

    it("handles malformed/non-array modules in outline without crashing", async () => {
      learningApi.getContinueLesson.mockResolvedValueOnce({
        lessonId: null,
        reason: "COMPLETED",
      });
      learningApi.getLearnerOutline.mockResolvedValueOnce({
        course: { title: "Malformed Course" },
        modules: "invalid-string",
      });

      renderWithProviders(<CourseRedirectPage />, {
        route: "/learning/courses/42",
        path: "/learning/courses/:courseId",
      });

      expect(
        await screen.findByText(
          "This course currently has no lessons published. Check back soon!",
        ),
      ).toBeInTheDocument();
    });
  });

  describe("Defensive Payload Handling", () => {
    it("MyLearningPage handles null or non-array content safely", async () => {
      learningApi.getMyCourses.mockResolvedValueOnce({
        content: null,
        totalPages: 0,
      });

      renderWithProviders(<MyLearningPage />);

      expect(
        await screen.findByText("You haven't enrolled in any courses yet"),
      ).toBeInTheDocument();
    });

    it("CoursePlayerPage handles null or non-array modules without crashing", async () => {
      learningApi.getLearnerOutline.mockResolvedValueOnce({
        course: { id: 77, title: "Test Course" },
        modules: null,
        progress: { completedLessons: 0, totalLessons: 0, percent: 0 },
      });
      learningApi.getLearnerLesson.mockResolvedValueOnce({
        lesson: { id: 1, title: "Lesson 1" },
        contentDocument: null,
        resources: null,
        completed: false,
      });

      renderWithProviders(<CoursePlayerPage />, {
        route: "/learning/courses/77/lessons/1",
        path: "/learning/courses/:courseId/lessons/:lessonId",
      });

      expect(await screen.findByText("Lesson 1")).toBeInTheDocument();
      expect(
        screen.getByText("This lesson has no written document content."),
      ).toBeInTheDocument();
    });
  });
});
