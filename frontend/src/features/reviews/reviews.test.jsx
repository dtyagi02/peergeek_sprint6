import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CourseReviewsSection } from "./CourseReviewsSection";
import { CourseReviewsPage } from "./CourseReviewsPage";
import * as reviewsApi from "./reviewsApi";
import * as learningApi from "../learn/learningApi";
import * as discoveryApi from "../discovery/discoveryApi";
import { useAuthStore } from "../auth/authStore";

vi.mock("./reviewsApi", () => ({
  getCourseReviews: vi.fn(),
  getCourseReviewSummary: vi.fn(),
  getMyReview: vi.fn(),
  submitReview: vi.fn(),
  deleteReview: vi.fn(),
}));

vi.mock("../learn/learningApi", () => ({
  getEnrollmentStatus: vi.fn(),
}));

vi.mock("../discovery/discoveryApi", () => ({
  getPublicCoursePreview: vi.fn(),
}));

function renderWithProviders(ui, { initialRoute = "/courses/42/reviews", path = "/courses/:courseId/reviews" } = {}) {
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
          <Route path={path} element={ui} />
          <Route path="/courses/:courseId" element={<div>Course Page</div>} />
          <Route path="/learning/courses/:courseId" element={<div>Player Page</div>} />
          <Route path="/profile/:username" element={<div>Profile Page</div>} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("Course Reviews Feature", () => {
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

  describe("CourseReviewsSection", () => {
    it("renders reviews list, ratings, and link to full reviews page", async () => {
      reviewsApi.getCourseReviews.mockResolvedValueOnce({
        content: [
          {
            id: 1,
            courseId: 42,
            reviewerName: "Alice Walker",
            reviewerUsername: "alicew",
            rating: 5,
            reviewText: "Phenomenal course structure!",
            createdAt: "2026-10-20T12:00:00Z",
          },
        ],
        totalElements: 1,
        totalPages: 1,
      });
      learningApi.getEnrollmentStatus.mockResolvedValueOnce({
        enrolled: false,
      });
      reviewsApi.getMyReview.mockResolvedValueOnce(null);

      renderWithProviders(<CourseReviewsSection courseId={42} />, {
        initialRoute: "/courses/42",
        path: "/courses/:courseId",
      });

      expect(
        await screen.findByText("Phenomenal course structure!"),
      ).toBeInTheDocument();
      expect(screen.getByText("Alice Walker")).toBeInTheDocument();
      expect(screen.getByText("@alicew")).toBeInTheDocument();
      expect(
        screen.getByText("1 review from enrolled peers"),
      ).toBeInTheDocument();

      const fullPageLink = screen.getByRole("link", {
        name: /see all reviews & breakdown/i,
      });
      expect(fullPageLink).toBeInTheDocument();
      expect(fullPageLink.getAttribute("href")).toBe("/courses/42/reviews");
    });

    it("shows empty state when no reviews exist", async () => {
      reviewsApi.getCourseReviews.mockResolvedValueOnce({
        content: [],
        totalElements: 0,
        totalPages: 0,
      });
      learningApi.getEnrollmentStatus.mockResolvedValueOnce({
        enrolled: false,
      });
      reviewsApi.getMyReview.mockResolvedValueOnce(null);

      renderWithProviders(<CourseReviewsSection courseId={42} />, {
        initialRoute: "/courses/42",
        path: "/courses/:courseId",
      });

      expect(
        await screen.findByText("No learner reviews submitted yet."),
      ).toBeInTheDocument();
    });

    it("allows enrolled learner to submit a review", async () => {
      const user = userEvent.setup();
      reviewsApi.getCourseReviews.mockResolvedValue({
        content: [],
        totalElements: 0,
        totalPages: 0,
      });
      learningApi.getEnrollmentStatus.mockResolvedValue({
        enrolled: true,
        status: "ACTIVE",
        isOwner: false,
      });
      reviewsApi.getMyReview.mockResolvedValue(null);
      reviewsApi.submitReview.mockResolvedValue({
        id: 2,
        courseId: 42,
        rating: 5,
        reviewText: "Great assignments!",
      });

      renderWithProviders(<CourseReviewsSection courseId={42} />, {
        initialRoute: "/courses/42",
        path: "/courses/:courseId",
      });

      const writeBtn = await screen.findByRole("button", {
        name: /write a review/i,
      });
      await user.click(writeBtn);

      const textarea = screen.getByPlaceholderText(
        /what did you think of the course content/i,
      );
      await user.type(textarea, "Great assignments!");

      const submitBtn = screen.getByRole("button", { name: /submit review/i });
      await user.click(submitBtn);

      expect(reviewsApi.submitReview).toHaveBeenCalledWith(42, {
        rating: 5,
        reviewText: "Great assignments!",
      });
    });
  });

  describe("Dedicated CourseReviewsPage", () => {
    const mockCourse = {
      id: 42,
      title: "Distributed Operating Systems",
      category: { id: 1, name: "Computer Science" },
      instructor: {
        id: 10,
        displayName: "Dr. Andrew Tanenbaum",
        username: "atanenbaum",
      },
    };

    const mockSummary = {
      courseId: 42,
      averageRating: 4.8,
      totalReviews: 5,
      distribution: { 5: 4, 4: 1, 3: 0, 2: 0, 1: 0 },
      distributionPercentages: { 5: 80.0, 4: 20.0, 3: 0.0, 2: 0.0, 1: 0.0 },
    };

    const mockReviewsList = {
      content: [
        {
          id: 101,
          courseId: 42,
          userId: 99,
          reviewerName: "Grace Hopper",
          reviewerUsername: "ghopper",
          reviewerAvatarUrl: null,
          rating: 5,
          reviewText: "Exceptional architecture concepts and assignments.",
          createdAt: "2026-10-22T14:30:00Z",
          updatedAt: "2026-10-22T14:30:00Z",
        },
      ],
      totalElements: 1,
      totalPages: 1,
    };

    it("displays course title, real rating summary, and distribution bars without NaN", async () => {
      discoveryApi.getPublicCoursePreview.mockResolvedValueOnce(mockCourse);
      reviewsApi.getCourseReviewSummary.mockResolvedValueOnce(mockSummary);
      reviewsApi.getCourseReviews.mockResolvedValueOnce(mockReviewsList);
      learningApi.getEnrollmentStatus.mockResolvedValueOnce({
        enrolled: true,
        status: "ACTIVE",
        isOwner: false,
      });
      reviewsApi.getMyReview.mockResolvedValueOnce(null);

      renderWithProviders(<CourseReviewsPage />);

      // Course title and instructor
      expect(
        await screen.findByRole("heading", {
          name: "Distributed Operating Systems",
        }),
      ).toBeInTheDocument();
      expect(screen.getByText("Dr. Andrew Tanenbaum")).toBeInTheDocument();

      // Overall average rating and count
      expect(screen.getByText("4.8")).toBeInTheDocument();
      expect(screen.getByText("Based on 5 reviews")).toBeInTheDocument();

      // Distribution bars
      expect(screen.getByText("4 (80%)")).toBeInTheDocument();
      expect(screen.getByText("1 (20%)")).toBeInTheDocument();

      // Navigation back to course & learning player
      expect(
        screen.getByRole("link", { name: /back to course page/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: /continue learning player/i }),
      ).toBeInTheDocument();
    });

    it("renders clean empty state when course has zero reviews", async () => {
      discoveryApi.getPublicCoursePreview.mockResolvedValueOnce(mockCourse);
      reviewsApi.getCourseReviewSummary.mockResolvedValueOnce({
        courseId: 42,
        averageRating: 0.0,
        totalReviews: 0,
        distribution: { 5: 0, 4: 0, 3: 0, 2: 0, 1: 0 },
        distributionPercentages: { 5: 0.0, 4: 0.0, 3: 0.0, 2: 0.0, 1: 0.0 },
      });
      reviewsApi.getCourseReviews.mockResolvedValueOnce({
        content: [],
        totalElements: 0,
        totalPages: 0,
      });
      learningApi.getEnrollmentStatus.mockResolvedValueOnce({
        enrolled: false,
      });
      reviewsApi.getMyReview.mockResolvedValueOnce(null);

      renderWithProviders(<CourseReviewsPage />);

      expect(
        await screen.findByRole("heading", { name: "No Course Reviews Yet" }),
      ).toBeInTheDocument();
      expect(screen.getByText("0.0")).toBeInTheDocument();
      expect(screen.getByText("No reviews yet")).toBeInTheDocument();
    });

    it("supports search, sorting, and rating filters", async () => {
      const user = userEvent.setup();
      discoveryApi.getPublicCoursePreview.mockResolvedValue(mockCourse);
      reviewsApi.getCourseReviewSummary.mockResolvedValue(mockSummary);
      reviewsApi.getCourseReviews.mockResolvedValue(mockReviewsList);
      learningApi.getEnrollmentStatus.mockResolvedValue({
        enrolled: false,
      });
      reviewsApi.getMyReview.mockResolvedValue(null);

      renderWithProviders(<CourseReviewsPage />);

      await screen.findByRole("heading", {
        name: "Distributed Operating Systems",
      });

      // Change sorting
      const sortSelect = screen.getByLabelText(/sort by/i);
      await user.selectOptions(sortSelect, "highest");

      await waitFor(() => {
        expect(reviewsApi.getCourseReviews).toHaveBeenCalledWith(
          42,
          expect.objectContaining({ sort: "highest" }),
        );
      });

      // Filter by rating 5
      const ratingFilterBtn = screen.getByRole("button", {
        name: /filter by 5 stars reviews/i,
      });
      await user.click(ratingFilterBtn);

      await waitFor(() => {
        expect(reviewsApi.getCourseReviews).toHaveBeenCalledWith(
          42,
          expect.objectContaining({ rating: 5 }),
        );
      });

      // Search
      const searchInput = screen.getByPlaceholderText(
        /search reviews by comment or reviewer name/i,
      );
      await user.type(searchInput, "compiler");

      await waitFor(() => {
        expect(reviewsApi.getCourseReviews).toHaveBeenCalledWith(
          42,
          expect.objectContaining({ search: "compiler" }),
        );
      });
    });

    it("allows enrolled student to submit and edit a review with star selector and feedback", async () => {
      const user = userEvent.setup();
      discoveryApi.getPublicCoursePreview.mockResolvedValue(mockCourse);
      reviewsApi.getCourseReviewSummary.mockResolvedValue(mockSummary);
      reviewsApi.getCourseReviews.mockResolvedValue(mockReviewsList);
      learningApi.getEnrollmentStatus.mockResolvedValue({
        enrolled: true,
        status: "ACTIVE",
        isOwner: false,
      });
      reviewsApi.getMyReview.mockResolvedValue(null);
      reviewsApi.submitReview.mockResolvedValue({
        id: 999,
        courseId: 42,
        rating: 5,
        reviewText: "Loved the kernel scheduling modules!",
      });

      renderWithProviders(<CourseReviewsPage />);

      const writeBtn = await screen.findByRole("button", {
        name: /write a review/i,
      });
      await user.click(writeBtn);

      expect(
        screen.getByRole("heading", { name: "Write a Course Review" }),
      ).toBeInTheDocument();

      // Pick 5 stars
      const star5 = screen.getByRole("button", { name: "5 stars" });
      await user.click(star5);

      // Type feedback
      const textarea = screen.getByPlaceholderText(
        /what did you think of the course content/i,
      );
      await user.type(textarea, "Loved the kernel scheduling modules!");

      // Submit
      const submitBtn = screen.getByRole("button", { name: /submit review/i });
      await user.click(submitBtn);

      expect(reviewsApi.submitReview).toHaveBeenCalledWith(42, {
        rating: 5,
        reviewText: "Loved the kernel scheduling modules!",
      });
    });

    it("displays reviewer profile links and badges correctly", async () => {
      discoveryApi.getPublicCoursePreview.mockResolvedValueOnce(mockCourse);
      reviewsApi.getCourseReviewSummary.mockResolvedValueOnce(mockSummary);
      reviewsApi.getCourseReviews.mockResolvedValueOnce({
        content: [
          {
            id: 101,
            courseId: 42,
            userId: 1, // matches current user
            reviewerName: "Student Test",
            reviewerUsername: "studenttest",
            reviewerAvatarUrl: null,
            rating: 5,
            reviewText: "My own review here.",
            createdAt: "2026-10-24T10:00:00Z",
          },
        ],
        totalElements: 1,
        totalPages: 1,
      });
      learningApi.getEnrollmentStatus.mockResolvedValueOnce({
        enrolled: true,
        status: "ACTIVE",
        isOwner: false,
      });
      reviewsApi.getMyReview.mockResolvedValueOnce({
        id: 101,
        rating: 5,
        reviewText: "My own review here.",
      });

      renderWithProviders(<CourseReviewsPage />);

      expect(await screen.findByText("Student Test")).toBeInTheDocument();
      expect(screen.getByText("@studenttest")).toBeInTheDocument();
      expect(screen.getByText("Your Review")).toBeInTheDocument();
      expect(screen.getByText("My own review here.")).toBeInTheDocument();

      const profileLink = screen.getByRole("link", { name: "@studenttest" });
      expect(profileLink.getAttribute("href")).toBe("/profile/studenttest");
    });
  });
});
