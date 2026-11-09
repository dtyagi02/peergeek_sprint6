import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BrowserRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NotificationBell } from "./NotificationBell";
import { NotificationsPage } from "./NotificationsPage";
import * as notificationsApi from "./notificationsApi";
import { useAuthStore } from "../auth/authStore";

vi.mock("./notificationsApi", () => ({
  getNotifications: vi.fn(),
  getUnreadCount: vi.fn(),
  markAsRead: vi.fn(),
  markAllAsRead: vi.fn(),
}));

function renderWithClient(ui) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>,
  );
}

describe("Notifications Feature", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({
      status: "authenticated",
      user: { id: 1, email: "student@test.local", lastMode: "STUDENT" },
    });
  });

  afterEach(() => {
    cleanup();
  });

  describe("NotificationBell", () => {
    it("renders bell and displays unread badge when count > 0", async () => {
      notificationsApi.getUnreadCount.mockResolvedValueOnce({ unreadCount: 3 });

      renderWithClient(<NotificationBell />);

      expect(
        await screen.findByRole("button", { name: /notifications, 3 unread/i }),
      ).toBeInTheDocument();
      expect(screen.getByTestId("unread-badge")).toHaveTextContent("3");
    });

    it("opens dropdown on click and displays recent notifications", async () => {
      const user = userEvent.setup();
      notificationsApi.getUnreadCount.mockResolvedValueOnce({ unreadCount: 1 });
      notificationsApi.getNotifications.mockResolvedValueOnce({
        content: [
          {
            id: 10,
            type: "SUBMISSION_GRADED",
            title: "Assignment graded",
            message: "Your submission scored 95/100",
            actionUrl: "/learning/courses/1/assignments/2",
            isRead: false,
            createdAt: "2026-10-20T12:00:00Z",
          },
        ],
        totalElements: 1,
        totalPages: 1,
      });

      renderWithClient(<NotificationBell />);

      const bellBtn = await screen.findByRole("button", {
        name: /notifications, 1 unread/i,
      });
      await user.click(bellBtn);

      expect(await screen.findByText("Assignment graded")).toBeInTheDocument();
      expect(
        screen.getByText("Your submission scored 95/100"),
      ).toBeInTheDocument();
      expect(screen.getByText(/view all notifications/i)).toBeInTheDocument();
    });

    it("marks notification as read when clicked in dropdown", async () => {
      const user = userEvent.setup();
      notificationsApi.getUnreadCount.mockResolvedValueOnce({ unreadCount: 1 });
      notificationsApi.getNotifications.mockResolvedValueOnce({
        content: [
          {
            id: 15,
            type: "COURSE_PUBLISHED",
            title: "Course published",
            message: "Course is now live",
            actionUrl: "/courses/1",
            isRead: false,
            createdAt: "2026-10-20T12:00:00Z",
          },
        ],
        totalElements: 1,
        totalPages: 1,
      });
      notificationsApi.markAsRead.mockResolvedValueOnce({
        id: 15,
        isRead: true,
      });

      renderWithClient(<NotificationBell />);

      const bellBtn = await screen.findByRole("button", {
        name: /notifications, 1 unread/i,
      });
      await user.click(bellBtn);

      const notifItem = await screen.findByText("Course published");
      await user.click(notifItem);

      expect(notificationsApi.markAsRead).toHaveBeenCalledWith(15);
    });
  });

  describe("NotificationsPage", () => {
    it("renders full notifications list and handles mark all as read", async () => {
      const user = userEvent.setup();
      notificationsApi.getNotifications.mockResolvedValueOnce({
        content: [
          {
            id: 20,
            type: "REVIEW_CREATED",
            title: "New review received",
            message: "A learner gave you 5 stars",
            actionUrl: "/courses/5",
            isRead: false,
            createdAt: "2026-10-20T14:00:00Z",
          },
        ],
        totalElements: 1,
        totalPages: 1,
      });
      notificationsApi.markAllAsRead.mockResolvedValueOnce({ markedCount: 1 });

      renderWithClient(<NotificationsPage />);

      expect(
        await screen.findByText("New review received"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("A learner gave you 5 stars"),
      ).toBeInTheDocument();

      const markAllBtn = screen.getByRole("button", {
        name: /mark all as read/i,
      });
      await user.click(markAllBtn);

      expect(notificationsApi.markAllAsRead).toHaveBeenCalled();
    });

    it("displays empty state when no notifications are returned", async () => {
      notificationsApi.getNotifications.mockResolvedValueOnce({
        content: [],
        totalElements: 0,
        totalPages: 0,
      });

      renderWithClient(<NotificationsPage />);

      expect(
        await screen.findByText("No notifications yet"),
      ).toBeInTheDocument();
    });
  });
});
