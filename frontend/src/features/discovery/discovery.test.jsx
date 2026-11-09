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
import { CourseCard } from "./CourseCard";
import { DiscoverPage } from "./DiscoverPage";
import { CoursePreviewPage } from "./CoursePreviewPage";
import { PopularCoursesSection } from "./PopularCoursesSection";
import * as discoveryApi from "./discoveryApi";

vi.mock("./discoveryApi", () => ({
  listPublishedCourses: vi.fn(),
  getPopularCourses: vi.fn(),
  getPublicCategories: vi.fn(),
  getPublicCoursePreview: vi.fn(),
}));

function renderWithProviders(ui, { route = "/courses", registry = {} } = {}) {
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
      <QueryClientProvider client={queryClient}>
        <SlotRegistryProvider registry={registry}>{ui}</SlotRegistryProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("Discovery feature", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  describe("Registries and Navigation", () => {
    it("registers 'Explore' in navigation visible to everyone", () => {
      const exploreItem = navigation.find((item) => item.id === "explore");
      expect(exploreItem).toBeDefined();
      expect(exploreItem.label).toBe("Explore");
      expect(exploreItem.route).toBe("/courses");

      const anonNav = visibleNavigation(navigation, { isAuthenticated: false });
      const authNav = visibleNavigation(navigation, { isAuthenticated: true });
      expect(anonNav.some((i) => i.id === "explore")).toBe(true);
      expect(authNav.some((i) => i.id === "explore")).toBe(true);
    });

    it("registers public routes for /courses and /courses/:courseId", () => {
      const discoverRoute = routes.find((r) => r.path === "/courses");
      const previewRoute = routes.find((r) => r.path === "/courses/:courseId");
      expect(discoverRoute).toBeDefined();
      expect(previewRoute).toBeDefined();
      expect(discoverRoute.visibility).not.toBe("protected");
      expect(previewRoute.visibility).not.toBe("protected");
    });

    it("registers PopularCoursesSection into 'home.sections' slot", () => {
      expect(
        slots.some(
          (c) =>
            c.name === "home.sections" && c.component === PopularCoursesSection,
        ),
      ).toBe(true);
    });
  });

  describe("CourseCard component", () => {
    const baseCourse = {
      id: 42,
      title: "Advanced React Systems",
      shortDescription:
        "Master modern component architecture and state design.",
      thumbnailUrl: "/api/v1/public/courses/42/thumbnail?v=1",
      category: { id: 1, name: "Web Development", slug: "web-development" },
      difficulty: "ADVANCED",
      instructor: { id: 10, displayName: "Grace Hopper" },
      moduleCount: 4,
      lessonCount: 16,
      stats: {
        enrollmentCount: 120,
        completionCount: 95,
        completionRate: 79,
        ratingAverage: 4.8,
        ratingCount: 48,
      },
    };

    it("renders thumbnail, category, difficulty, counts, ratings, and stats", () => {
      renderWithProviders(<CourseCard course={baseCourse} />);

      expect(
        screen.getByRole("img", { name: "Advanced React Systems" }),
      ).toHaveAttribute("src", "/api/v1/public/courses/42/thumbnail?v=1");
      expect(screen.getByText("Web Development")).toBeInTheDocument();
      expect(screen.getByText("Advanced")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "Advanced React Systems" }),
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          "Master modern component architecture and state design.",
        ),
      ).toBeInTheDocument();
      expect(screen.getByText("By Grace Hopper")).toBeInTheDocument();
      expect(screen.getByText("4 modules")).toBeInTheDocument();
      expect(screen.getByText("16 lessons")).toBeInTheDocument();

      // Rating (stars)
      expect(
        screen.getByLabelText(/Rated 4.8 out of 5 stars based on 48 reviews/),
      ).toBeInTheDocument();
      expect(screen.getByText("120 enrolled")).toBeInTheDocument();
      expect(screen.getByText("79% completion")).toBeInTheDocument();

      // Card is one link to preview
      const link = screen.getByRole("link");
      expect(link).toHaveAttribute("href", "/courses/42");
    });

    it("renders graceful placeholder when thumbnail is null and omits rating when ratingCount is 0", () => {
      const courseWithoutThumbOrRating = {
        ...baseCourse,
        thumbnailUrl: null,
        stats: {
          enrollmentCount: null,
          completionCount: null,
          completionRate: null,
          ratingAverage: null,
          ratingCount: 0,
        },
      };

      renderWithProviders(<CourseCard course={courseWithoutThumbOrRating} />);

      expect(screen.queryByRole("img")).not.toBeInTheDocument();
      expect(screen.getByText("A")).toBeInTheDocument(); // Initial of title
      expect(
        screen.queryByLabelText(/Rated.*out of 5 stars/),
      ).not.toBeInTheDocument();
      expect(screen.queryByText(/enrolled/)).not.toBeInTheDocument();
      expect(screen.queryByText(/completion/)).not.toBeInTheDocument();
    });
  });

  describe("DiscoverPage", () => {
    const mockCategories = [
      {
        id: 1,
        name: "Web Development",
        slug: "web-development",
        courseCount: 5,
      },
      { id: 2, name: "Computer Science", slug: "cs", courseCount: 3 },
    ];

    const mockCoursesResponse = {
      content: [
        {
          id: 1,
          title: "Learn Algorithms",
          shortDescription: "Fundamental algorithms and complexity analysis.",
          category: { id: 2, name: "Computer Science", slug: "cs" },
          difficulty: "BEGINNER",
          instructor: { id: 1, displayName: "Donald Knuth" },
          moduleCount: 2,
          lessonCount: 8,
          stats: {
            ratingAverage: 5.0,
            ratingCount: 12,
            enrollmentCount: 200,
            completionRate: 85,
          },
        },
      ],
      page: 0,
      size: 12,
      totalElements: 1,
      totalPages: 1,
    };

    beforeEach(() => {
      discoveryApi.getPublicCategories.mockResolvedValue(mockCategories);
      discoveryApi.listPublishedCourses.mockResolvedValue(mockCoursesResponse);
    });

    it("loads courses and categories, syncing filter state with URL", async () => {
      renderWithProviders(<DiscoverPage />, {
        route:
          "/courses?q=algo&category=cs&difficulty=BEGINNER&sort=title,asc&page=0",
      });

      expect(
        await screen.findByRole("heading", { name: "Explore Courses" }),
      ).toBeInTheDocument();
      expect(
        await screen.findByRole("heading", { name: "Learn Algorithms" }),
      ).toBeInTheDocument();

      // Check category select value
      const categorySelect = screen.getByLabelText("Category");
      expect(categorySelect.value).toBe("cs");

      // Check search input value from URL
      const searchInput = screen.getByRole("searchbox", {
        name: "Search courses",
      });
      expect(searchInput.value).toBe("algo");

      // Check sort select
      const sortSelect = screen.getByLabelText("Sort by");
      expect(sortSelect.value).toBe("title,asc");
      // Relevance should be an option because q is present
      expect(
        screen.getByRole("option", { name: "Most relevant" }),
      ).toBeInTheDocument();
    });

    it("hides 'relevance' sort option when no search query exists", async () => {
      renderWithProviders(<DiscoverPage />, { route: "/courses" });

      await screen.findByRole("heading", { name: "Learn Algorithms" });
      const sortSelect = screen.getByLabelText("Sort by");
      expect(sortSelect.value).toBe("popularity,desc");
      expect(
        screen.queryByRole("option", { name: "Most relevant" }),
      ).not.toBeInTheDocument();
    });

    it("falls back safely to defaults on invalid URL query parameters", async () => {
      renderWithProviders(<DiscoverPage />, {
        route: "/courses?page=-5&sort=malicious_sort&difficulty=INVALID_DIFF",
      });

      await screen.findByRole("heading", { name: "Learn Algorithms" });

      expect(discoveryApi.listPublishedCourses).toHaveBeenCalledWith(
        expect.objectContaining({
          page: 0,
          sort: "popularity,desc",
          difficulty: undefined,
        }),
      );
    });

    it("renders empty state with clear filters action when no courses match", async () => {
      discoveryApi.listPublishedCourses.mockResolvedValueOnce({
        content: [],
        page: 0,
        size: 12,
        totalElements: 0,
        totalPages: 0,
      });

      renderWithProviders(<DiscoverPage />, {
        route: "/courses?q=nonexistent",
      });

      expect(
        await screen.findByRole("heading", { name: "No courses match" }),
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          "Try adjusting your search terms or filters to find what you're looking for.",
        ),
      ).toBeInTheDocument();

      const clearBtn = screen.getByRole("button", { name: "Clear filters" });
      expect(clearBtn).toBeInTheDocument();
    });

    it("renders error state with retry action on API failure", async () => {
      discoveryApi.listPublishedCourses.mockRejectedValueOnce(
        new Error("Network error"),
      );

      renderWithProviders(<DiscoverPage />);

      expect(
        await screen.findByRole("heading", { name: "Failed to load courses" }),
      ).toBeInTheDocument();
      const retryBtn = screen.getByRole("button", { name: "Retry" });
      expect(retryBtn).toBeInTheDocument();

      discoveryApi.listPublishedCourses.mockResolvedValueOnce(
        mockCoursesResponse,
      );
      await userEvent.click(retryBtn);

      expect(
        await screen.findByRole("heading", { name: "Learn Algorithms" }),
      ).toBeInTheDocument();
    });

    it("supports clearing search input immediately via clear button", async () => {
      renderWithProviders(<DiscoverPage />, { route: "/courses?q=keyword" });

      await screen.findByRole("heading", { name: "Explore Courses" });
      const searchInput = screen.getByRole("searchbox", {
        name: "Search courses",
      });
      expect(searchInput.value).toBe("keyword");

      const clearSearchBtn = screen.getByRole("button", {
        name: "Clear search text",
      });
      await userEvent.click(clearSearchBtn);

      expect(searchInput.value).toBe("");
    });

    it("handles unexpected non-array content in API response gracefully", async () => {
      discoveryApi.listPublishedCourses.mockResolvedValueOnce({
        content: null,
        page: 0,
        size: 12,
        totalElements: 0,
        totalPages: 0,
      });

      renderWithProviders(<DiscoverPage />, { route: "/courses" });

      expect(
        await screen.findByRole("heading", { name: "No courses match" }),
      ).toBeInTheDocument();
    });

    it("renders courses grouped into category sections with prominent headings and horizontal rows", async () => {
      const multiCategoryCourses = {
        content: [
          {
            id: 1,
            title: "Learn Algorithms",
            shortDescription: "Fundamental algorithms.",
            category: { id: 2, name: "Computer Science", slug: "cs" },
            difficulty: "BEGINNER",
            instructor: { id: 1, displayName: "Donald Knuth" },
            moduleCount: 2,
            lessonCount: 8,
            stats: { ratingAverage: 5.0, ratingCount: 12 },
          },
          {
            id: 2,
            title: "Full-Stack React & Spring",
            shortDescription: "Build modern web apps.",
            category: {
              id: 1,
              name: "Web Development",
              slug: "web-development",
            },
            difficulty: "INTERMEDIATE",
            instructor: { id: 2, displayName: "Grace Hopper" },
            moduleCount: 4,
            lessonCount: 16,
            stats: { ratingAverage: 4.8, ratingCount: 20 },
          },
        ],
        page: 0,
        size: 50,
        totalElements: 2,
        totalPages: 1,
      };

      discoveryApi.listPublishedCourses.mockResolvedValueOnce(
        multiCategoryCourses,
      );

      renderWithProviders(<DiscoverPage />, { route: "/courses" });

      // Category headings should be visible
      expect(
        await screen.findByRole("heading", { name: "Computer Science" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "Web Development" }),
      ).toBeInTheDocument();

      // Courses appear within their respective sections
      expect(
        screen.getByRole("heading", { name: "Learn Algorithms" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "Full-Stack React & Spring" }),
      ).toBeInTheDocument();
    });

    it("displays 'Show more' button when category has additional courses and navigates to category view on click", async () => {
      const singleCourseWithMoreInDb = {
        content: [
          {
            id: 10,
            title: "Modern Frontend Architecture",
            shortDescription: "Architect scalable apps.",
            category: {
              id: 1,
              name: "Web Development",
              slug: "web-development",
            },
            difficulty: "ADVANCED",
            instructor: { id: 2, displayName: "Grace Hopper" },
            moduleCount: 3,
            lessonCount: 12,
            stats: { ratingAverage: 4.9, ratingCount: 15 },
          },
        ],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
      };

      discoveryApi.listPublishedCourses.mockResolvedValueOnce(
        singleCourseWithMoreInDb,
      );

      renderWithProviders(<DiscoverPage />, { route: "/courses" });

      // In mockCategories, Web Development has courseCount: 5.
      // 5 total in category - 1 displayed = 4 remaining -> "Show 4 more"
      const showMoreBtn = await screen.findByRole("button", {
        name: /Show (4 )?more courses in Web Development/i,
      });
      expect(showMoreBtn).toBeInTheDocument();

      // Clicking Show more navigates to the dedicated category view
      await userEvent.click(showMoreBtn);

      // Verify that the view switched to the category filter
      expect(
        await screen.findByRole("button", { name: "View all categories" }),
      ).toBeInTheDocument();
    });

    it("omits 'Show more' button when a category has 4 or fewer courses and all are displayed", async () => {
      const allCoursesDisplayed = {
        content: [
          {
            id: 1,
            title: "Learn Algorithms",
            shortDescription: "Fundamental algorithms.",
            category: { id: 2, name: "Computer Science", slug: "cs" },
            difficulty: "BEGINNER",
            instructor: { id: 1, displayName: "Donald Knuth" },
            moduleCount: 2,
            lessonCount: 8,
            stats: { ratingAverage: 5.0, ratingCount: 12 },
          },
        ],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
      };

      // Set category cs to have courseCount: 1
      discoveryApi.getPublicCategories.mockResolvedValueOnce([
        { id: 2, name: "Computer Science", slug: "cs", courseCount: 1 },
      ]);
      discoveryApi.listPublishedCourses.mockResolvedValueOnce(
        allCoursesDisplayed,
      );

      renderWithProviders(<DiscoverPage />, { route: "/courses" });

      await screen.findByRole("heading", { name: "Learn Algorithms" });
      expect(
        screen.queryByRole("button", {
          name: /Show.*more courses in Computer Science/i,
        }),
      ).not.toBeInTheDocument();
    });

    it("allows returning to all categories from single-category view via breadcrumb or back button", async () => {
      renderWithProviders(<DiscoverPage />, {
        route: "/courses?category=cs",
      });

      expect(
        await screen.findByRole("heading", { name: "Computer Science" }),
      ).toBeInTheDocument();

      const backBtn = screen.getByRole("button", {
        name: "View all categories",
      });
      await userEvent.click(backBtn);

      expect(
        await screen.findByRole("heading", { name: "Explore Courses" }),
      ).toBeInTheDocument();
    });
  });

  describe("CoursePreviewPage", () => {
    const mockPreview = {
      id: 101,
      title: "Distributed Systems & Consensus",
      description:
        "First paragraph of deep architecture.\n\nSecond paragraph with line breaks preserved.\n- Bullet point 1\n- Bullet point 2",
      category: { id: 2, name: "Computer Science", slug: "cs" },
      difficulty: "ADVANCED",
      topics: ["distributed-systems", "raft", "paxos"],
      instructor: {
        id: 5,
        displayName: "Leslie Lamport",
        headline: "Turing Award Laureate & Distributed Systems Researcher",
        bio: "Pioneered foundational concepts in concurrent and distributed computing.",
      },
      moduleCount: 2,
      lessonCount: 4,
      thumbnailUrl: "/api/v1/public/courses/101/thumbnail?v=1",
      stats: {
        enrollmentCount: 500,
        completionCount: 350,
        completionRate: 70,
        ratingAverage: 4.95,
        ratingCount: 150,
      },
      outline: [
        {
          moduleTitle: "Module 1: Time, Clocks and Ordering",
          lessons: [{ title: "Logical Clocks" }, { title: "Vector Clocks" }],
        },
        {
          moduleTitle: "Module 2: Consensus Protocols",
          lessons: [
            { title: "Paxos Fundamentals" },
            { title: "Raft Protocol" },
          ],
        },
      ],
    };

    it("renders course preview header, plain-text description, outline accordion, and instructor card", async () => {
      discoveryApi.getPublicCoursePreview.mockResolvedValueOnce(mockPreview);

      renderWithProviders(
        <Routes>
          <Route element={<CoursePreviewPage />} path="/courses/:courseId" />
        </Routes>,
        { route: "/courses/101" },
      );

      expect(
        await screen.findByRole("heading", {
          name: "Distributed Systems & Consensus",
        }),
      ).toBeInTheDocument();
      expect(document.title).toBe("Distributed Systems & Consensus | PeerGeek");

      // Plain text description with preserved newlines
      expect(
        screen.getByText(/Second paragraph with line breaks preserved/),
      ).toHaveClass("whitespace-pre-wrap");

      // Topics
      expect(screen.getByText("#distributed-systems")).toBeInTheDocument();
      expect(screen.getByText("#raft")).toBeInTheDocument();

      // Outline accordion and note
      expect(
        screen.getByText(
          /Full lesson content and assignments unlock after enrolling/,
        ),
      ).toBeInTheDocument();
      expect(
        screen.getByText("Module 1: Time, Clocks and Ordering"),
      ).toBeInTheDocument();
      expect(screen.getByText("Logical Clocks")).toBeInTheDocument();
      expect(screen.getByText("Vector Clocks")).toBeInTheDocument();

      // Instructor card
      expect(
        screen.getByRole("heading", { name: "About the instructor" }),
      ).toBeInTheDocument();
      const headings = screen.getAllByRole("heading");
      expect(headings.some((h) => h.textContent === "Leslie Lamport")).toBe(
        true,
      );
      expect(
        screen.getByText(
          "Turing Award Laureate & Distributed Systems Researcher",
        ),
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          "Pioneered foundational concepts in concurrent and distributed computing.",
        ),
      ).toBeInTheDocument();
    });

    it("renders friendly 404 state when course is not found or unpublished", async () => {
      discoveryApi.getPublicCoursePreview.mockRejectedValueOnce({
        status: 404,
        message: "Not Found",
      });

      renderWithProviders(
        <Routes>
          <Route element={<CoursePreviewPage />} path="/courses/:courseId" />
        </Routes>,
        { route: "/courses/999" },
      );

      expect(
        await screen.findByRole("heading", {
          name: "This course isn't available",
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "Explore available courses" }),
      ).toBeInTheDocument();
    });

    it("renders enroll-area slot passing courseId, and renders nothing extra when empty", async () => {
      discoveryApi.getPublicCoursePreview.mockResolvedValue(mockPreview);

      // Test 1: Empty slot -> no enroll button in DOM
      const { unmount } = renderWithProviders(
        <Routes>
          <Route element={<CoursePreviewPage />} path="/courses/:courseId" />
        </Routes>,
        { route: "/courses/101", registry: {} },
      );

      await screen.findByRole("heading", {
        name: "Distributed Systems & Consensus",
      });
      expect(
        screen.queryByTestId("mock-enroll-button"),
      ).not.toBeInTheDocument();
      unmount();

      // Test 2: Registered slot component -> receives courseId
      const MockEnrollComponent = ({ courseId }) => (
        <button data-testid="mock-enroll-button" type="button">
          Enroll in Course #{courseId}
        </button>
      );

      const customRegistry = {
        "course-page.enroll-area": [
          { id: "enroll", component: MockEnrollComponent },
        ],
      };

      renderWithProviders(
        <Routes>
          <Route element={<CoursePreviewPage />} path="/courses/:courseId" />
        </Routes>,
        { route: "/courses/101", registry: customRegistry },
      );

      const enrollBtn = await screen.findByTestId("mock-enroll-button");
      expect(enrollBtn).toHaveTextContent("Enroll in Course #101");
    });

    it("handles missing or non-array outline and topics gracefully without crashing", async () => {
      discoveryApi.getPublicCoursePreview.mockResolvedValueOnce({
        ...mockPreview,
        topics: null,
        outline: null,
      });

      renderWithProviders(
        <Routes>
          <Route element={<CoursePreviewPage />} path="/courses/:courseId" />
        </Routes>,
        { route: "/courses/101" },
      );

      expect(
        await screen.findByRole("heading", {
          name: "Distributed Systems & Consensus",
        }),
      ).toBeInTheDocument();
      expect(screen.queryByText("Topics covered")).not.toBeInTheDocument();
    });
  });

  describe("PopularCoursesSection", () => {
    it("renders popular course cards when data is available", async () => {
      const mockPopular = [
        {
          id: 1,
          title: "Popular Course 1",
          shortDescription: "Top rated course 1",
          difficulty: "BEGINNER",
          moduleCount: 2,
          lessonCount: 6,
          stats: { ratingAverage: 4.9, ratingCount: 20 },
        },
      ];

      discoveryApi.getPopularCourses.mockResolvedValueOnce(mockPopular);

      renderWithProviders(<PopularCoursesSection />);

      expect(
        await screen.findByRole("heading", { name: "Popular Courses" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "Popular Course 1" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: "View all courses →" }),
      ).toBeInTheDocument();
    });

    it("hides itself when courses list is empty", async () => {
      discoveryApi.getPopularCourses.mockResolvedValueOnce([]);

      const { container } = renderWithProviders(<PopularCoursesSection />);

      await waitFor(() => {
        expect(container).toBeEmptyDOMElement();
      });
    });

    it("hides itself on query error", async () => {
      discoveryApi.getPopularCourses.mockRejectedValueOnce(new Error("Failed"));

      const { container } = renderWithProviders(<PopularCoursesSection />);

      await waitFor(() => {
        expect(container).toBeEmptyDOMElement();
      });
    });

    it("hides itself when data is not an array", async () => {
      discoveryApi.getPopularCourses.mockResolvedValueOnce(
        "<!doctype html><html></html>",
      );

      const { container } = renderWithProviders(<PopularCoursesSection />);

      await waitFor(() => {
        expect(container).toBeEmptyDOMElement();
      });
    });
  });
});
