import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GlobalCommunityPage } from "./GlobalCommunityPage";
import { AdminCommunityPanel } from "../admin/AdminCommunityPanel";
import * as communityApi from "./communityApi";
import { useAuthStore } from "../auth/authStore";
import { visibleNavigation } from "../../app/registry/navigation";
import communityNav from "./navigation";

vi.mock("./communityApi", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    getGlobalCommunityMessages: vi.fn(),
    createGlobalCommunityMessage: vi.fn(),
    sendGlobalCommunityMessage: vi.fn(),
    deleteGlobalCommunityMessage: vi.fn(),
    getGlobalCommunityStatus: vi.fn(),
    getCommunityBlockStatus: vi.fn(),
    getAdminCommunityMessages: vi.fn(),
    adminDeleteCommunityMessage: vi.fn(),
    getAdminBlockedCommunityUsers: vi.fn(),
    adminBlockCommunityUser: vi.fn(),
    adminUnblockCommunityUser: vi.fn(),
    listDomainCommunities: vi.fn().mockResolvedValue([]),
  };
});

function renderWithClient(ui) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });

  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </MemoryRouter>
  );
}

describe("Global PeerGeek Community", () => {
  const mockStudentUser = {
    id: 101,
    username: "john_student",
    displayName: "John Student",
    role: "USER",
    mode: "STUDENT",
    avatarUrl: null,
  };

  const mockAdminUser = {
    id: 999,
    username: "admin_user",
    displayName: "Platform Admin",
    role: "ADMIN",
    mode: "STUDENT",
    avatarUrl: null,
  };

  const sampleMessages = [
    {
      id: 1,
      content: "First welcome message!",
      authorId: 202,
      authorUsername: "prof_jane",
      authorDisplayName: "Prof Jane",
      authorRole: "INSTRUCTOR",
      authorAvatarUrl: null,
      createdAt: new Date(Date.now() - 60000).toISOString(),
      updatedAt: new Date(Date.now() - 60000).toISOString(),
    },
    {
      id: 2,
      content: "Hello everyone from student!",
      authorId: 101,
      authorUsername: "john_student",
      authorDisplayName: "John Student",
      authorRole: "STUDENT",
      authorAvatarUrl: null,
      createdAt: new Date(Date.now() - 30000).toISOString(),
      updatedAt: new Date(Date.now() - 30000).toISOString(),
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({
      status: "authenticated",
      user: mockStudentUser,
    });
    const blockStatusObj = {
      blocked: false,
      reason: null,
      blockedAt: null,
    };
    communityApi.getGlobalCommunityStatus.mockResolvedValue(blockStatusObj);
    communityApi.getCommunityBlockStatus.mockResolvedValue(blockStatusObj);
    communityApi.getGlobalCommunityMessages.mockResolvedValue({
      messages: sampleMessages,
      hasMore: false,
      oldestCursor: 1,
      newestCursor: 2,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("Navigation & Visibility", () => {
    it("appears in navigation for both student and instructor modes", () => {
      // Student mode
      const studentNav = visibleNavigation(communityNav, {
        isAuthenticated: true,
        mode: "STUDENT",
      });
      expect(studentNav.some((item) => item.id === "global-community")).toBe(true);
      expect(studentNav.find((item) => item.id === "global-community").route).toBe("/community");

      // Instructor mode
      const instructorNav = visibleNavigation(communityNav, {
        isAuthenticated: true,
        mode: "INSTRUCTOR",
      });
      expect(instructorNav.some((item) => item.id === "global-community")).toBe(true);
    });
  });

  describe("GlobalCommunityPage UI & Feed", () => {
    it("renders page header, dynamic WebSocket status indicator, and initial message feed without permanent refresh button", async () => {
      renderWithClient(<GlobalCommunityPage />);

      expect(screen.getByText("PeerGeek Community")).toBeInTheDocument();
      expect(
        screen.getByText(/A shared space for students and instructors/i)
      ).toBeInTheDocument();

      // Ensure permanent Refresh button and static "Live Feed" badge are not present
      expect(screen.queryByRole("button", { name: /^Refresh$/i })).not.toBeInTheDocument();
      expect(screen.queryByText(/↻ Refresh/i)).not.toBeInTheDocument();
      expect(screen.queryByText("Live Feed")).not.toBeInTheDocument();

      // Dynamic connection indicator is present in header
      const header = screen.getByRole("banner");
      expect(within(header).getByText(/^(Connected|Connecting\.\.\.|Reconnecting\.\.\.|Disconnected)$/)).toBeInTheDocument();

      await waitFor(() => {
        expect(screen.getByText("First welcome message!")).toBeInTheDocument();
        expect(screen.getByText("Hello everyone from student!")).toBeInTheDocument();
      });

      // Role badges check
      expect(screen.getByText("INSTRUCTOR")).toBeInTheDocument();
      expect(screen.getByText("STUDENT")).toBeInTheDocument();
    });

    it("displays subtle Retry action when WebSocket connection is disconnected", async () => {
      renderWithClient(<GlobalCommunityPage />);

      await waitFor(() => {
        expect(screen.getByText("Disconnected")).toBeInTheDocument();
      });

      const retryBtn = screen.getByRole("button", { name: /Retry/i });
      expect(retryBtn).toBeInTheDocument();
      fireEvent.click(retryBtn);
    });

    it("allows sending a new message and updates feed", async () => {
      const user = userEvent.setup();
      const newMessageResponse = {
        id: 3,
        content: "My new posted question",
        authorId: 101,
        authorUsername: "john_student",
        authorDisplayName: "John Student",
        authorRole: "STUDENT",
        authorAvatarUrl: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      communityApi.createGlobalCommunityMessage.mockResolvedValue(newMessageResponse);
      communityApi.sendGlobalCommunityMessage.mockResolvedValue(newMessageResponse);

      renderWithClient(<GlobalCommunityPage />);

      await waitFor(() => {
        expect(screen.getByText("First welcome message!")).toBeInTheDocument();
      });

      const composer = screen.getByRole("textbox", { name: /Community message composer/i });
      await user.type(composer, "My new posted question");

      // Character count indicator check
      expect(screen.getByText(/22\/2000 characters/i)).toBeInTheDocument();

      const sendBtn = screen.getByRole("button", { name: /Send Message/i });
      await user.click(sendBtn);

      await waitFor(() => {
        expect(screen.getByText("My new posted question")).toBeInTheDocument();
      });
    });

    it("displays blocked banner and disables composer when user is blocked from community", async () => {
      const blockedStatus = {
        blocked: true,
        reason: "Violating code of conduct in chat",
        blockedAt: new Date().toISOString(),
      };
      communityApi.getGlobalCommunityStatus.mockResolvedValue(blockedStatus);
      communityApi.getCommunityBlockStatus.mockResolvedValue(blockedStatus);

      renderWithClient(<GlobalCommunityPage />);

      await waitFor(() => {
        expect(screen.getByText(/You are blocked from posting in the community/i)).toBeInTheDocument();
        expect(screen.getByText(/Violating code of conduct in chat/i)).toBeInTheDocument();
      });

      const composer = screen.getByPlaceholderText(/Posting is disabled while you are blocked/i);
      expect(composer).toBeDisabled();
      expect(screen.getByRole("button", { name: /Blocked/i })).toBeDisabled();
    });

    it("supports loading older messages when hasMore is true", async () => {
      const user = userEvent.setup();
      communityApi.getGlobalCommunityMessages
        .mockResolvedValueOnce({
          messages: [sampleMessages[1]],
          hasMore: true,
          oldestCursor: 2,
          newestCursor: 2,
        })
        .mockResolvedValueOnce({
          messages: [sampleMessages[0]],
          hasMore: false,
          oldestCursor: 1,
          newestCursor: 1,
        });

      renderWithClient(<GlobalCommunityPage />);

      await waitFor(() => {
        expect(screen.getByText("Hello everyone from student!")).toBeInTheDocument();
      });

      const loadOlderBtn = screen.getByRole("button", { name: /Load Older Messages/i });
      await user.click(loadOlderBtn);

      expect(communityApi.getGlobalCommunityMessages).toHaveBeenCalledWith(
        expect.objectContaining({ beforeId: 2 })
      );

      await waitFor(() => {
        expect(screen.getByText("First welcome message!")).toBeInTheDocument();
      });
    });

    it("allows author to delete their own message with confirmation", async () => {
      const user = userEvent.setup();
      communityApi.deleteGlobalCommunityMessage.mockResolvedValue({
        messageId: 2,
        deleted: true,
      });

      renderWithClient(<GlobalCommunityPage />);

      await waitFor(() => {
        expect(screen.getByText("Hello everyone from student!")).toBeInTheDocument();
      });

      // Message 2 is authored by john_student (mockStudentUser id 101)
      const deleteButtons = screen.getAllByRole("button", { name: /Delete/i });
      expect(deleteButtons.length).toBeGreaterThan(0);

      await user.click(deleteButtons[0]);

      // Confirm modal opens
      expect(screen.getByText(/Delete Community Message/i)).toBeInTheDocument();
      const confirmDeleteBtn = screen.getByRole("button", { name: /Confirm Delete/i });
      await user.click(confirmDeleteBtn);

      expect(communityApi.deleteGlobalCommunityMessage).toHaveBeenCalledWith(2, expect.any(Object));

      await waitFor(() => {
        expect(screen.queryByText("Hello everyone from student!")).not.toBeInTheDocument();
      });
    });
  });

  describe("Admin Community Panel", () => {
    beforeEach(() => {
      useAuthStore.setState({
        status: "authenticated",
        user: mockAdminUser,
      });
      communityApi.getAdminCommunityMessages.mockResolvedValue({
        content: [
          {
            id: 10,
            content: "Flagged content message",
            authorId: 303,
            authorUsername: "bad_actor",
            authorDisplayName: "Bad Actor",
            authorRole: "STUDENT",
            deleted: true,
            deleteReason: "Inappropriate language",
            deletedByUsername: "admin_user",
            deletedAt: new Date().toISOString(),
            createdAt: new Date().toISOString(),
          },
        ],
        totalElements: 1,
        totalPages: 1,
        number: 0,
      });
      communityApi.getAdminBlockedCommunityUsers.mockResolvedValue({
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
      });
    });

    it("displays admin audit feed including soft-deleted messages and delete reasons", async () => {
      renderWithClient(<AdminCommunityPanel />);

      expect(screen.getByText(/Community Moderation & Real-Time Monitoring/i)).toBeInTheDocument();

      await waitFor(() => {
        expect(screen.getByText("Flagged content message")).toBeInTheDocument();
        expect(screen.getByText("DELETED")).toBeInTheDocument();
        expect(screen.getByText(/Inappropriate language/)).toBeInTheDocument();
      });
    });

    it("allows switching to Blocked Participants tab and unblocking a user", async () => {
      const user = userEvent.setup();
      communityApi.getAdminBlockedCommunityUsers.mockResolvedValue({
        content: [
          {
            id: 1,
            userId: 303,
            username: "bad_actor",
            displayName: "Bad Actor",
            reason: "Repeated spamming",
            blockedByUsername: "admin_user",
            createdAt: new Date().toISOString(),
          },
        ],
        totalElements: 1,
        totalPages: 1,
        number: 0,
      });
      communityApi.adminUnblockCommunityUser.mockResolvedValue({
        userId: 303,
        blocked: false,
      });

      renderWithClient(<AdminCommunityPanel />);

      const blockedTab = screen.getByRole("button", { name: /Blocked Users/i });
      await user.click(blockedTab);

      await waitFor(() => {
        expect(screen.getByText("Bad Actor")).toBeInTheDocument();
        expect(screen.getByText("Repeated spamming")).toBeInTheDocument();
      });

      const unblockBtn = screen.getByRole("button", { name: /^Unblock$/i });
      await user.click(unblockBtn);

      const confirmBtn = screen.getByRole("button", { name: /Confirm Unblock/i });
      await user.click(confirmBtn);

      expect(communityApi.adminUnblockCommunityUser).toHaveBeenCalledWith(303, expect.anything());
    });
  });
});
