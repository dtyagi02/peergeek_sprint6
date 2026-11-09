import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BookmarkToggle } from "./BookmarkToggle";
import { BookmarksPage } from "./BookmarksPage";
import * as bookmarksApi from "./bookmarksApi";
import { useAuthStore } from "../auth/authStore";

vi.mock("./bookmarksApi", () => ({
  listBookmarks: vi.fn(),
  deleteBookmark: vi.fn(),
  getCourseBookmarkStatus: vi.fn(),
  bookmarkCourse: vi.fn(),
  removeCourseBookmark: vi.fn(),
  getLessonBookmarkStatus: vi.fn(),
  bookmarkLesson: vi.fn(),
  removeLessonBookmark: vi.fn(),
}));

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });
}

function renderWithProviders(
  ui,
  { queryClient = createTestQueryClient() } = {},
) {
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("Bookmarks Feature", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({
      status: "authenticated",
      user: { id: 1, email: "learner@test.local", lastMode: "STUDENT" },
    });
  });

  afterEach(() => {
    cleanup();
  });

  describe("BookmarkToggle", () => {
    it("renders unbookmarked state and toggles to bookmarked", async () => {
      const user = userEvent.setup();
      bookmarksApi.getCourseBookmarkStatus.mockResolvedValueOnce({
        bookmarked: false,
      });
      bookmarksApi.bookmarkCourse.mockResolvedValueOnce({
        id: 10,
        targetType: "COURSE",
        courseId: 42,
      });

      renderWithProviders(
        <BookmarkToggle targetType="COURSE" courseId={42} showLabel={true} />,
      );

      const toggleBtn = await screen.findByRole("button", {
        name: /bookmark this course/i,
      });
      expect(toggleBtn).toBeInTheDocument();
      expect(screen.getByText("Bookmark")).toBeInTheDocument();

      await user.click(toggleBtn);
      expect(bookmarksApi.bookmarkCourse).toHaveBeenCalledWith(42);
    });

    it("removes bookmark when already bookmarked", async () => {
      const user = userEvent.setup();
      bookmarksApi.getCourseBookmarkStatus.mockResolvedValueOnce({
        bookmarked: true,
      });
      bookmarksApi.removeCourseBookmark.mockResolvedValueOnce();

      renderWithProviders(
        <BookmarkToggle targetType="COURSE" courseId={42} showLabel={true} />,
      );

      const removeBtn = await screen.findByRole("button", {
        name: /remove course bookmark/i,
      });
      expect(removeBtn).toBeInTheDocument();
      expect(screen.getByText("Bookmarked")).toBeInTheDocument();

      await user.click(removeBtn);
      expect(bookmarksApi.removeCourseBookmark).toHaveBeenCalledWith(42);
    });

    it("works for lesson targets", async () => {
      const user = userEvent.setup();
      bookmarksApi.getLessonBookmarkStatus.mockResolvedValueOnce({
        bookmarked: false,
      });
      bookmarksApi.bookmarkLesson.mockResolvedValueOnce({
        id: 11,
        targetType: "LESSON",
        courseId: 42,
        lessonId: 99,
      });

      renderWithProviders(
        <BookmarkToggle
          targetType="LESSON"
          courseId={42}
          lessonId={99}
          showLabel={true}
        />,
      );

      const toggleBtn = await screen.findByRole("button", {
        name: /bookmark this lesson/i,
      });
      expect(toggleBtn).toBeInTheDocument();

      await user.click(toggleBtn);
      expect(bookmarksApi.bookmarkLesson).toHaveBeenCalledWith(42, 99);
    });
  });

  describe("BookmarksPage", () => {
    it("renders empty state when user has no bookmarks", async () => {
      bookmarksApi.listBookmarks.mockResolvedValueOnce([]);

      renderWithProviders(<BookmarksPage />);

      expect(
        await screen.findByText(/no bookmarks found/i),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: /explore courses/i }),
      ).toBeInTheDocument();
    });

    it("renders error state when request fails and allows retry", async () => {
      const user = userEvent.setup();
      bookmarksApi.listBookmarks.mockRejectedValueOnce(
        new Error("Network failed"),
      );

      renderWithProviders(<BookmarksPage />);

      expect(
        await screen.findByText(/unable to load bookmarks/i),
      ).toBeInTheDocument();

      bookmarksApi.listBookmarks.mockResolvedValueOnce([]);
      const retryBtn = screen.getByRole("button", { name: /try again/i });
      await user.click(retryBtn);

      expect(
        await screen.findByText(/no bookmarks found/i),
      ).toBeInTheDocument();
    });

    it("renders bookmarked courses and lessons, supports filter and deletion", async () => {
      const user = userEvent.setup();
      const mockBookmarks = [
        {
          id: 1,
          targetType: "COURSE",
          courseId: 101,
          courseTitle: "Distributed Systems in Go",
          createdAt: "2026-10-21T00:00:00Z",
        },
        {
          id: 2,
          targetType: "LESSON",
          courseId: 101,
          courseTitle: "Distributed Systems in Go",
          lessonId: 202,
          lessonTitle: "Consensus via Raft",
          createdAt: "2026-10-21T01:00:00Z",
        },
      ];

      bookmarksApi.listBookmarks.mockResolvedValue(mockBookmarks);
      bookmarksApi.deleteBookmark.mockResolvedValueOnce();

      renderWithProviders(<BookmarksPage />);

      expect(
        await screen.findByText("Distributed Systems in Go"),
      ).toBeInTheDocument();
      expect(screen.getByText("Consensus via Raft")).toBeInTheDocument();

      // Filter by Courses
      const coursesTab = screen.getByRole("button", { name: /courses/i });
      await user.click(coursesTab);

      expect(screen.getByText("Distributed Systems in Go")).toBeInTheDocument();
      expect(screen.queryByText("Consensus via Raft")).not.toBeInTheDocument();

      // Delete bookmark
      const removeButtons = screen.getAllByRole("button", {
        name: /remove bookmark/i,
      });
      await user.click(removeButtons[0]);
      expect(bookmarksApi.deleteBookmark).toHaveBeenCalledWith(1);
    });
  });
});
