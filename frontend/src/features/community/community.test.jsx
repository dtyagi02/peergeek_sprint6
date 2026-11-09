import { useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CommunityPage } from "./CommunityPage";
import { MentionRenderer } from "./MentionRenderer";
import { MentionTextarea } from "./MentionTextarea";
import * as communityApi from "./communityApi";
import { useAuthStore } from "../auth/authStore";

vi.mock("./communityApi", () => ({
  listPosts: vi.fn(),
  getPost: vi.fn(),
  createPost: vi.fn(),
  deletePost: vi.fn(),
  listComments: vi.fn(),
  createComment: vi.fn(),
  deleteComment: vi.fn(),
  getMentionSuggestions: vi.fn(),
}));

function renderWithProviders(ui, { initialRoute = "/community" } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });

  return render(
    <MemoryRouter initialEntries={[initialRoute]}>
      <QueryClientProvider client={queryClient}>
        <Routes>
          <Route path="/community" element={ui} />
          <Route path="/profile/:username" element={<div>Profile Page Mock</div>} />
          <Route path="/login" element={<div>Login Page Mock</div>} />
          <Route path="/register" element={<div>Register Page Mock</div>} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("PeerGeek Community Feature", () => {
  const mockUser = {
    id: 1,
    username: "devansh_dev",
    displayName: "Devansh Tyagi",
    avatarUrl: null,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({
      status: "authenticated",
      user: mockUser,
    });
  });

  afterEach(() => {
    cleanup();
  });

  describe("CommunityPage Feed & Empty State", () => {
    it("renders page heading, supporting text, and empty state when feed has no posts", async () => {
      communityApi.listPosts.mockResolvedValueOnce({
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      });

      renderWithProviders(<CommunityPage />);

      expect(screen.getByRole("heading", { name: /^community$/i })).toBeInTheDocument();
      expect(
        screen.getByText("Share ideas, ask questions, and learn together."),
      ).toBeInTheDocument();

      await waitFor(() => {
        expect(screen.getByText("No posts yet")).toBeInTheDocument();
      });
      expect(
        screen.getByText(/be the first to ask a question/i),
      ).toBeInTheDocument();
    });

    it("renders posts with author info, mentions, and relative timestamps", async () => {
      communityApi.listPosts.mockResolvedValueOnce({
        content: [
          {
            id: 101,
            author: {
              id: 2,
              displayName: "Aarav Sharma",
              username: "aarav_dev",
              avatarUrl: null,
            },
            content: "How should we structure Spring Boot? Ask @riya_dev!",
            createdAt: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
            updatedAt: null,
            commentCount: 1,
            isAuthor: false,
            canDelete: false,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      });

      communityApi.listComments.mockResolvedValueOnce([
        {
          id: 501,
          postId: 101,
          parentCommentId: null,
          author: {
            id: 3,
            displayName: "Riya Patel",
            username: "riya_dev",
            avatarUrl: null,
          },
          content: "I recommend package-by-feature!",
          createdAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
          updatedAt: null,
          isAuthor: false,
          canDelete: false,
          replies: [],
        },
      ]);

      renderWithProviders(<CommunityPage />);

      await waitFor(() => {
        expect(screen.getByText("Aarav Sharma")).toBeInTheDocument();
      });
      expect(screen.getByText("@aarav_dev")).toBeInTheDocument();
      expect(screen.getByText("10 min ago")).toBeInTheDocument();

      // Check mention is rendered and links to profile
      const mentionLink = screen.getByRole("link", { name: "@riya_dev" });
      expect(mentionLink).toBeInTheDocument();
      expect(mentionLink).toHaveAttribute("href", "/profile/riya_dev");
    });
  });

  describe("Create Post", () => {
    it("allows authenticated user to create a post with validation", async () => {
      const user = userEvent.setup();

      communityApi.listPosts.mockResolvedValue({
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      });
      communityApi.createPost.mockResolvedValueOnce({
        id: 102,
        author: mockUser,
        content: "Excited to learn React and Spring Boot!",
        createdAt: new Date().toISOString(),
        updatedAt: null,
        commentCount: 0,
        isAuthor: true,
        canDelete: true,
      });

      renderWithProviders(<CommunityPage />);

      const postButton = screen.getByRole("button", { name: /^post$/i });
      expect(postButton).toBeDisabled();

      const textarea = screen.getByPlaceholderText(
        "What's on your mind? Share something with the community...",
      );
      await user.type(textarea, "Excited to learn React and Spring Boot!");

      expect(postButton).not.toBeDisabled();
      await user.click(postButton);

      expect(communityApi.createPost).toHaveBeenCalledWith({
        content: "Excited to learn React and Spring Boot!",
      });
    });
  });

  describe("Comments and Replies", () => {
    it("allows adding a comment and replying to a specific comment", async () => {
      const user = userEvent.setup();

      communityApi.listPosts.mockResolvedValue({
        content: [
          {
            id: 200,
            author: mockUser,
            content: "What is your favorite database?",
            createdAt: new Date().toISOString(),
            updatedAt: null,
            commentCount: 1,
            isAuthor: true,
            canDelete: true,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      });

      communityApi.listComments.mockResolvedValue([
        {
          id: 601,
          postId: 200,
          parentCommentId: null,
          author: {
            id: 4,
            displayName: "Priya Singh",
            username: "priya_s",
            avatarUrl: null,
          },
          content: "PostgreSQL is amazing for complex queries.",
          createdAt: new Date().toISOString(),
          updatedAt: null,
          isAuthor: false,
          canDelete: false,
          replies: [],
        },
      ]);

      communityApi.createComment.mockResolvedValueOnce({
        id: 602,
        postId: 200,
        parentCommentId: 601,
        author: mockUser,
        content: "@priya_s Completely agree with that!",
        createdAt: new Date().toISOString(),
        updatedAt: null,
        isAuthor: true,
        canDelete: true,
        replies: [],
      });

      renderWithProviders(<CommunityPage />);

      await waitFor(() => {
        expect(screen.getByText("Priya Singh")).toBeInTheDocument();
      });

      // Click subtle Reply button under Priya's comment
      const replyButton = screen.getByRole("button", { name: /^reply$/i });
      await user.click(replyButton);

      // Reply box should open with prefilled @priya_s
      const replyTextarea = screen.getByPlaceholderText(
        "Write a reply... use @username to mention",
      );
      expect(replyTextarea.value).toContain("@priya_s ");

      await user.type(replyTextarea, "Completely agree with that!");

      const submitReplyBtn = screen.getByRole("button", { name: "Submit reply" });
      await user.click(submitReplyBtn);

      expect(communityApi.createComment).toHaveBeenCalledWith(200, {
        content: "@priya_s Completely agree with that!",
        parentCommentId: 601,
      });
    });

    it("displays nested replies beneath the parent comment", async () => {
      communityApi.listPosts.mockResolvedValue({
        content: [
          {
            id: 300,
            author: mockUser,
            content: "Microservices discussion",
            createdAt: new Date().toISOString(),
            updatedAt: null,
            commentCount: 2,
            isAuthor: true,
            canDelete: true,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      });

      communityApi.listComments.mockResolvedValue([
        {
          id: 701,
          postId: 300,
          parentCommentId: null,
          author: {
            id: 5,
            displayName: "Vikram Das",
            username: "vikram_d",
            avatarUrl: null,
          },
          content: "When should we break a monolith into microservices?",
          createdAt: new Date().toISOString(),
          updatedAt: null,
          isAuthor: false,
          canDelete: false,
          replies: [
            {
              id: 702,
              postId: 300,
              parentCommentId: 701,
              author: {
                id: 6,
                displayName: "Anita Roy",
                username: "anita_r",
                avatarUrl: null,
              },
              content: "@vikram_d When team boundaries require independent deployment.",
              createdAt: new Date().toISOString(),
              updatedAt: null,
              isAuthor: false,
              canDelete: false,
              replies: [],
            },
          ],
        },
      ]);

      renderWithProviders(<CommunityPage />);

      await waitFor(() => {
        expect(screen.getByText("Vikram Das")).toBeInTheDocument();
      });
      expect(screen.getByText("Anita Roy")).toBeInTheDocument();
      expect(screen.getByText("@anita_r")).toBeInTheDocument();
      expect(
        screen.getByText(/When team boundaries require independent deployment/i),
      ).toBeInTheDocument();
    });
  });

  describe("Mention Autocomplete & Rendering", () => {
    it("suggests matching usernames when typing @ and inserts selection", async () => {
      communityApi.getMentionSuggestions.mockResolvedValue([
        {
          id: 10,
          username: "rahul_code",
          displayName: "Rahul Kumar",
          avatarUrl: null,
        },
      ]);

      const user = userEvent.setup();

      let text = "";
      const handleChange = vi.fn((val) => {
        text = val;
      });

      function TestWrapper() {
        const [val, setVal] = useState("");
        return (
          <MemoryRouter>
            <MentionTextarea
              id="test-textarea"
              value={val}
              onChange={(next) => {
                setVal(next);
                handleChange(next);
              }}
              placeholder="Type @..."
            />
          </MemoryRouter>
        );
      }

      render(<TestWrapper />);

      const textarea = screen.getByRole("textbox");
      await user.type(textarea, "@r");

      await waitFor(() => {
        expect(communityApi.getMentionSuggestions).toHaveBeenCalledWith("r");
      });

      // Suggestion dropdown should appear
      await waitFor(() => {
        expect(screen.getByText("Rahul Kumar")).toBeInTheDocument();
      });
      expect(screen.getByText("@rahul_code")).toBeInTheDocument();

      // Click the suggestion
      const suggestionOption = screen.getByRole("option");
      await user.click(suggestionOption);

      expect(handleChange).toHaveBeenCalledWith("@rahul_code ");
    });

    it("renders mentions as links and preserves surrounding text and line breaks", () => {
      const content = "Hello @alice_dev!\nCheck this out @bob_123.";

      render(
        <MemoryRouter>
          <MentionRenderer content={content} />
        </MemoryRouter>,
      );

      const aliceLink = screen.getByRole("link", { name: "@alice_dev" });
      const bobLink = screen.getByRole("link", { name: "@bob_123" });

      expect(aliceLink).toHaveAttribute("href", "/profile/alice_dev");
      expect(bobLink).toHaveAttribute("href", "/profile/bob_123");
      expect(screen.getByText(/Hello/)).toBeInTheDocument();
      expect(screen.getByText(/Check this out/)).toBeInTheDocument();
    });
  });

  describe("Post & Comment Deletion", () => {
    it("allows authorized users to delete posts with confirmation", async () => {
      const user = userEvent.setup();

      communityApi.listPosts.mockResolvedValue({
        content: [
          {
            id: 400,
            author: mockUser,
            content: "Post to be deleted",
            createdAt: new Date().toISOString(),
            updatedAt: null,
            commentCount: 0,
            isAuthor: true,
            canDelete: true,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      });
      communityApi.deletePost.mockResolvedValueOnce({});

      renderWithProviders(<CommunityPage />);

      await waitFor(() => {
        expect(screen.getByText("Post to be deleted")).toBeInTheDocument();
      });

      const deleteIconBtn = screen.getByRole("button", { name: "Delete post" });
      await user.click(deleteIconBtn);

      expect(screen.getByText("Delete post?")).toBeInTheDocument();

      const confirmYes = screen.getByRole("button", { name: "Yes" });
      await user.click(confirmYes);

      expect(communityApi.deletePost).toHaveBeenCalledWith(400);
    });
  });

  describe("Anonymous Visitors", () => {
    it("displays prompt to sign in when visitor is unauthenticated", async () => {
      useAuthStore.setState({
        status: "anonymous",
        user: null,
      });

      communityApi.listPosts.mockResolvedValueOnce({
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      });

      renderWithProviders(<CommunityPage />);

      expect(screen.getByText("Join the conversation")).toBeInTheDocument();
      expect(screen.getByRole("link", { name: "Log in" })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: "Sign up" })).toBeInTheDocument();
    });
  });
});
