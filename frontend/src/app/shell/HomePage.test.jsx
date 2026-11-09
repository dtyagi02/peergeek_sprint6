import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useAuthStore } from "../../features/auth/authStore";
import * as discoveryApi from "../../features/discovery/discoveryApi";
import { SlotRegistryProvider } from "../registry/slots";
import { HomePage } from "./HomePage";

vi.mock("../../features/discovery/discoveryApi", () => ({
  listPublishedCourses: vi.fn(),
  getPopularCourses: vi.fn(),
  getPublicCategories: vi.fn(),
}));

const mockCategories = [
  { id: 1, name: "Web Development", slug: "web-development", courseCount: 5 },
  { id: 2, name: "Data Science", slug: "data-science", courseCount: 2 },
];

const mockCourses = {
  content: [
    {
      id: 101,
      title: "React Fundamentals",
      shortDescription: "Learn React from scratch",
      category: { id: 1, name: "Web Development", slug: "web-development" },
      difficulty: "BEGINNER",
      instructor: { id: 1, displayName: "Jane Doe" },
      stats: { ratingAverage: 4.8, ratingCount: 15 },
    },
    {
      id: 102,
      title: "Advanced TypeScript",
      shortDescription: "Deep dive into types",
      category: { id: 1, name: "Web Development", slug: "web-development" },
      difficulty: "ADVANCED",
      instructor: { id: 2, displayName: "John Smith" },
      stats: { ratingAverage: 4.9, ratingCount: 20 },
    },
    {
      id: 201,
      title: "Python for Data Analysis",
      shortDescription: "Data manipulation with Pandas",
      category: { id: 2, name: "Data Science", slug: "data-science" },
      difficulty: "INTERMEDIATE",
      instructor: { id: 3, displayName: "Alan Turing" },
      stats: { ratingAverage: 4.7, ratingCount: 8 },
    },
  ],
  totalElements: 3,
  totalPages: 1,
};

function renderHomePage({ registry = {} } = {}) {
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
    <MemoryRouter initialEntries={["/"]}>
      <QueryClientProvider client={queryClient}>
        <SlotRegistryProvider registry={registry}>
          <HomePage />
        </SlotRegistryProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("HomePage Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ user: null, status: "anonymous" });
    discoveryApi.getPublicCategories.mockResolvedValue(mockCategories);
    discoveryApi.listPublishedCourses.mockResolvedValue(mockCourses);
    discoveryApi.getPopularCourses.mockResolvedValue(mockCourses.content);
  });

  afterEach(() => {
    cleanup();
  });

  it("renders anonymous welcome message when user is not logged in", async () => {
    renderHomePage();

    expect(
      screen.getByRole("heading", { name: /Learn together\. Grow together\./i }),
    ).toBeInTheDocument();
  });

  it("renders personalized welcome message with actual user display name", async () => {
    useAuthStore.setState({
      user: { id: 1, displayName: "Devansh Tyagi", username: "dtyagi" },
      status: "authenticated",
    });

    renderHomePage();

    expect(
      screen.getByRole("heading", { name: /Welcome, Devansh Tyagi/i }),
    ).toBeInTheDocument();
  });

  it("renders role recommendations banner and allows dismissing it", async () => {
    renderHomePage();

    expect(
      screen.getByText("Which role(s) are you interested in?"),
    ).toBeInTheDocument();
    expect(screen.getByText("Full-Stack Developer")).toBeInTheDocument();
    expect(screen.getByText("Data Scientist")).toBeInTheDocument();

    const dismissBtn = screen.getByRole("button", {
      name: "Dismiss role recommendations",
    });
    await userEvent.click(dismissBtn);

    expect(
      screen.queryByText("Which role(s) are you interested in?"),
    ).not.toBeInTheDocument();
  });

  it("renders Featured Learning section with Forest Green and Charcoal banner and courses", async () => {
    renderHomePage();

    await waitFor(() => {
      expect(
        screen.getByRole("heading", {
          name: "Discover Your Next Learning Path",
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          "Explore courses, build practical skills, and learn from the PeerGeek community.",
        ),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: /Explore all courses/i }),
      ).toBeInTheDocument();
    });
  });

  it("renders category-based course rows with cards and show more", async () => {
    renderHomePage();

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Web Development" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "Data Science" }),
      ).toBeInTheDocument();
    });

    // Check course cards inside categories
    expect(
      screen.getAllByText("React Fundamentals").length,
    ).toBeGreaterThanOrEqual(1);
    expect(
      screen.getAllByText("Advanced TypeScript").length,
    ).toBeGreaterThanOrEqual(1);
    expect(
      screen.getAllByText("Python for Data Analysis").length,
    ).toBeGreaterThanOrEqual(1);

    // Since mockCategories has Web Development courseCount: 5 (> 4), "Show more" button is present
    expect(
      screen.getByRole("button", {
        name: "Show more courses in Web Development",
      }),
    ).toBeInTheDocument();
  });

  it("renders slots registered to 'home.sections'", async () => {
    const mockComponent = () => (
      <div data-testid="custom-slot">Custom Section</div>
    );
    const registry = {
      "home.sections": [{ component: mockComponent, order: 1 }],
    };

    renderHomePage({ registry });

    expect(screen.getByTestId("custom-slot")).toBeInTheDocument();
    expect(screen.getByText("Custom Section")).toBeInTheDocument();
  });
});
