import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DomainCommunityEntryButton } from "./DomainCommunityEntryButton";
import { DomainChatPage } from "./DomainChatPage";
import { CategorySection } from "../discovery/CategorySection";
import { MyLearningPage } from "../learn/MyLearningPage";
import * as communityApi from "./communityApi";
import * as learningApi from "../learn/learningApi";
import { useAuthStore } from "../auth/authStore";

vi.mock("./communityApi", () => ({
  getDomainCommunity: vi.fn(),
  getDomainMembers: vi.fn(),
  getDomainMessages: vi.fn(),
  joinDomainCommunity: vi.fn(),
  leaveDomainCommunity: vi.fn(),
  sendDomainMessage: vi.fn(),
  getMentionSuggestions: vi.fn(),
}));

vi.mock("../learn/learningApi", () => ({
  getMyCourses: vi.fn(),
}));

function renderWithRouter(ui, { initialRoute = "/" } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });

  return render(
    <MemoryRouter initialEntries={[initialRoute]}>
      <QueryClientProvider client={queryClient}>
        {ui}
      </QueryClientProvider>
    </MemoryRouter>
  );
}

describe("Domain-Based Community Chat Feature", () => {
  const mockUser = {
    id: 1,
    username: "devansh_dev",
    displayName: "Devansh Tyagi",
    avatarUrl: null,
  };

  const mockDomain = {
    id: 10,
    categoryId: 1,
    name: "Java & Backend Development",
    slug: "java-backend",
    description: "Learn Java, Spring Boot, APIs, and backend engineering together.",
    memberCount: 42,
    isMember: false,
    joinedAt: null,
    createdAt: "2026-01-01T00:00:00Z",
  };

  const mockMessages = [
    {
      id: 101,
      domainId: 10,
      sender: {
        id: 2,
        username: "aarav_k",
        displayName: "Aarav Kumar",
        avatarUrl: null,
      },
      messageType: "SYSTEM_JOIN",
      content: "Aarav Kumar joined Java & Backend Development.",
      isSender: false,
      createdAt: "2026-01-01T10:00:00Z",
    },
    {
      id: 102,
      domainId: 10,
      sender: {
        id: 2,
        username: "aarav_k",
        displayName: "Aarav Kumar",
        avatarUrl: null,
      },
      messageType: "CHAT",
      content: "Hello everyone! Working on Spring Boot microservices.",
      isSender: false,
      createdAt: "2026-01-01T10:05:00Z",
    },
    {
      id: 103,
      domainId: 10,
      sender: {
        id: 1,
        username: "devansh_dev",
        displayName: "Devansh Tyagi",
        avatarUrl: null,
      },
      messageType: "CHAT",
      content: "Welcome @aarav_k! Happy to help if you have questions.",
      isSender: true,
      createdAt: "2026-01-01T10:07:00Z",
    },
  ];

  const mockMembers = {
    content: [
      {
        id: 1,
        username: "devansh_dev",
        displayName: "Devansh Tyagi",
        avatarUrl: null,
        joinedAt: "2026-01-01T09:00:00Z",
      },
      {
        id: 2,
        username: "aarav_k",
        displayName: "Aarav Kumar",
        avatarUrl: null,
        joinedAt: "2026-01-01T10:00:00Z",
      },
    ],
    totalElements: 2,
    totalPages: 1,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({
      status: "authenticated",
      user: mockUser,
    });

    communityApi.getDomainCommunity.mockResolvedValue({ ...mockDomain, isMember: true });
    communityApi.getDomainMessages.mockResolvedValue(mockMessages);
    communityApi.getDomainMembers.mockResolvedValue(mockMembers);
    communityApi.getMentionSuggestions.mockResolvedValue([]);
  });

  describe("DomainCommunityEntryButton", () => {
    it("renders button variant linking to domain chat route", () => {
      renderWithRouter(
        <DomainCommunityEntryButton
          domainSlug="java-backend"
          domainName="Java & Backend Development"
          label="Open Community"
        />
      );

      const link = screen.getByRole("link", { name: /open java & backend development community chat/i });
      expect(link).toBeInTheDocument();
      expect(link).toHaveAttribute("href", "/domains/java-backend/community");
      expect(screen.getByText("Open Community")).toBeInTheDocument();
    });

    it("renders card variant with domain information and link", () => {
      renderWithRouter(
        <DomainCommunityEntryButton
          domainSlug="frontend-dev"
          domainName="Frontend Development"
          variant="card"
        />
      );

      const link = screen.getByRole("link", { name: /join frontend development community chat/i });
      expect(link).toBeInTheDocument();
      expect(link).toHaveAttribute("href", "/domains/frontend-dev/community");
      expect(screen.getByText("Frontend Development Community")).toBeInTheDocument();
      expect(screen.getByText(/join chat →/i)).toBeInTheDocument();
    });
  });

  describe("Explore CategorySection Community Entry Point", () => {
    it("renders Join Domain Chat button for the category section", () => {
      const section = {
        name: "Java & Backend Development",
        slug: "java-backend",
        courseCount: 3,
        courses: [
          {
            id: 1,
            title: "Java Fundamentals",
            slug: "java-fundamentals",
            summary: "Learn Java",
            level: "BEGINNER",
            estimatedDurationHours: 10,
            instructor: { id: 9, displayName: "Prof Smith" },
          },
        ],
      };

      renderWithRouter(
        <CategorySection
          section={section}
          onShowMore={vi.fn()}
          onCategoryClick={vi.fn()}
        />
      );

      const communityLink = screen.getByRole("link", {
        name: /open java & backend development community chat/i,
      });
      expect(communityLink).toBeInTheDocument();
      expect(communityLink).toHaveAttribute("href", "/domains/java-backend/community");
      expect(screen.getByText("Join Domain Chat")).toBeInTheDocument();
    });
  });

  describe("MyLearningPage Domain Grouping and Community Integration", () => {
    it("groups enrolled courses by domain and displays Open Community entry point for each domain", async () => {
      learningApi.getMyCourses.mockResolvedValue({
        content: [
          {
            courseId: 1,
            title: "Spring Boot Mastery",
            courseSlug: "spring-boot-mastery",
            instructorDisplayName: "Jane Doe",
            difficulty: "INTERMEDIATE",
            progress: { percent: 50, completedLessons: 5, totalLessons: 10 },
            category: { id: 1, name: "Java & Backend Development", slug: "java-backend" },
          },
          {
            courseId: 2,
            title: "Advanced Java",
            courseSlug: "advanced-java",
            instructorDisplayName: "John Smith",
            difficulty: "ADVANCED",
            progress: { percent: 20, completedLessons: 2, totalLessons: 10 },
            category: { id: 1, name: "Java & Backend Development", slug: "java-backend" },
          },
          {
            courseId: 3,
            title: "React Fundamentals",
            courseSlug: "react-fundamentals",
            instructorDisplayName: "Alice Brown",
            difficulty: "BEGINNER",
            progress: { percent: 100, completedLessons: 8, totalLessons: 8 },
            category: { id: 2, name: "Frontend Development", slug: "frontend-dev" },
          },
        ],
        totalPages: 1,
        totalElements: 3,
      });

      renderWithRouter(<MyLearningPage />);

      await waitFor(() => {
        expect(screen.getAllByText("Spring Boot Mastery")[0]).toBeInTheDocument();
      });

      // Domain headings
      expect(screen.getByRole("heading", { name: "Java & Backend Development" })).toBeInTheDocument();
      expect(screen.getByRole("heading", { name: "Frontend Development" })).toBeInTheDocument();

      // Open Community buttons per domain
      const javaCommunityLink = screen.getByRole("link", {
        name: /open java & backend development community chat/i,
      });
      expect(javaCommunityLink).toHaveAttribute("href", "/domains/java-backend/community");

      const frontendCommunityLink = screen.getByRole("link", {
        name: /open frontend development community chat/i,
      });
      expect(frontendCommunityLink).toHaveAttribute("href", "/domains/frontend-dev/community");

      // Verify Continue and Reviews buttons remain functional on course cards
      expect(screen.getAllByRole("link", { name: /continue/i }).length).toBe(3);
      expect(screen.getAllByRole("link", { name: /reviews/i }).length).toBe(3);
    });
  });

  describe("DomainChatPage Group Chat Experience", () => {
    it("renders domain header with name, description, and member count", async () => {
      renderWithRouter(
        <Routes>
          <Route path="/domains/:slug/community" element={<DomainChatPage />} />
        </Routes>,
        { initialRoute: "/domains/java-backend/community" }
      );

      await waitFor(() => {
        expect(screen.getByRole("heading", { name: "Java & Backend Development" })).toBeInTheDocument();
      });

      expect(screen.getByText(/learn java, spring boot, apis/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /view members \(42\)/i })).toBeInTheDocument();
    });

    it("renders system join activity and chat messages in chronological order", async () => {
      renderWithRouter(
        <Routes>
          <Route path="/domains/:slug/community" element={<DomainChatPage />} />
        </Routes>,
        { initialRoute: "/domains/java-backend/community" }
      );

      await waitFor(() => {
        expect(screen.getByText("Aarav Kumar joined Java & Backend Development.")).toBeInTheDocument();
      });

      expect(screen.getByText("Hello everyone! Working on Spring Boot microservices.")).toBeInTheDocument();
      expect(screen.getByText(/welcome/i)).toBeInTheDocument();
    });

    it("displays Join Community prompt when user is not a member", async () => {
      const user = userEvent.setup();
      communityApi.getDomainCommunity.mockResolvedValue({
        ...mockDomain,
        isMember: false,
      });

      renderWithRouter(
        <Routes>
          <Route path="/domains/:slug/community" element={<DomainChatPage />} />
        </Routes>,
        { initialRoute: "/domains/java-backend/community" }
      );

      await waitFor(() => {
        expect(screen.getByRole("heading", { name: /join java & backend development community/i })).toBeInTheDocument();
      });

      const joinBtn = screen.getByRole("button", { name: "Join Community" });
      expect(joinBtn).toBeInTheDocument();

      // Click Join Community
      communityApi.joinDomainCommunity.mockResolvedValue({
        id: 99,
        domainId: 10,
        userId: 1,
      });

      await user.click(joinBtn);
      await waitFor(() => {
        expect(communityApi.joinDomainCommunity).toHaveBeenCalledWith("java-backend");
      });
    });

    it("allows members to send messages", async () => {
      const user = userEvent.setup();
      communityApi.sendDomainMessage.mockResolvedValue({
        id: 104,
        domainId: 10,
        sender: mockUser,
        messageType: "CHAT",
        content: "Excited to be here!",
        isSender: true,
        createdAt: "2026-01-01T10:10:00Z",
      });

      renderWithRouter(
        <Routes>
          <Route path="/domains/:slug/community" element={<DomainChatPage />} />
        </Routes>,
        { initialRoute: "/domains/java-backend/community" }
      );

      await waitFor(() => {
        expect(screen.getByPlaceholderText(/message #java & backend development/i)).toBeInTheDocument();
      });

      const input = screen.getByPlaceholderText(/message #java & backend development/i);
      await user.type(input, "Excited to be here!");

      const sendBtn = screen.getByRole("button", { name: /send message/i });
      await user.click(sendBtn);

      expect(communityApi.sendDomainMessage).toHaveBeenCalledWith("java-backend", {
        content: "Excited to be here!",
      });
    });

    it("opens member drawer and displays domain members", async () => {
      const user = userEvent.setup();

      renderWithRouter(
        <Routes>
          <Route path="/domains/:slug/community" element={<DomainChatPage />} />
        </Routes>,
        { initialRoute: "/domains/java-backend/community" }
      );

      await waitFor(() => {
        expect(screen.getByRole("button", { name: /view members \(42\)/i })).toBeInTheDocument();
      });

      const memberToggleBtn = screen.getByRole("button", { name: /view members \(42\)/i });
      await user.click(memberToggleBtn);

      await waitFor(() => {
        expect(screen.getByText("Domain Members")).toBeInTheDocument();
      });

      expect(screen.getAllByText("Aarav Kumar").length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText("@aarav_k").length).toBeGreaterThanOrEqual(1);
    });

    it("allows members to leave the community after confirmation", async () => {
      const user = userEvent.setup();
      communityApi.leaveDomainCommunity.mockResolvedValue({});

      renderWithRouter(
        <Routes>
          <Route path="/domains/:slug/community" element={<DomainChatPage />} />
        </Routes>,
        { initialRoute: "/domains/java-backend/community" }
      );

      await waitFor(() => {
        expect(screen.getByRole("button", { name: "Leave" })).toBeInTheDocument();
      });

      // Click Leave button
      await user.click(screen.getByRole("button", { name: "Leave" }));
      expect(screen.getByText("Leave?")).toBeInTheDocument();

      // Confirm Leave
      await user.click(screen.getByRole("button", { name: "Yes" }));
      expect(communityApi.leaveDomainCommunity).toHaveBeenCalledWith("java-backend");
    });
  });
});
