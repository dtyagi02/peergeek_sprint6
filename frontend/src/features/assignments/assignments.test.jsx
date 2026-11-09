import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import * as assignmentApi from "./assignmentApi";
import { AssignmentBuilderPage } from "./AssignmentBuilderPage";
import { InstructorAssignmentsListPage } from "./InstructorAssignmentsListPage";
import { TakeAssignmentPage } from "./TakeAssignmentPage";
import { ManualGradingPage } from "./ManualGradingPage";
import {
  CourseEditorAssignmentsHeaderLink,
  PlayerHeaderAssignmentsLink,
  LessonAssignmentsAction,
} from "./AssignmentSlotComponents";

vi.mock("./assignmentApi");

function renderWithProviders(ui, { route = "/test", path = "/test" } = {}) {
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

  return {
    queryClient,
    ...render(
      <MemoryRouter initialEntries={[route]}>
        <QueryClientProvider client={queryClient}>
          <Routes>
            <Route path={path} element={ui} />
            <Route path="*" element={<div>Fallback Page</div>} />
          </Routes>
        </QueryClientProvider>
      </MemoryRouter>,
    ),
  };
}

describe("Phase 7: Assignments & Assessments Feature", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
  });

  afterEach(() => {
    cleanup();
  });

  describe("AssignmentBuilderPage", () => {
    const mockAssignment = {
      id: 101,
      courseId: 1,
      title: "Midterm Exam",
      kind: "QUESTIONS",
      instructions: "Answer all questions thoroughly.",
      status: "DRAFT",
      maxPoints: 50,
      timeLimitMinutes: 60,
      allowLate: true,
      allowText: true,
      allowFiles: false,
      showCorrectAnswers: false,
      attemptCount: 0,
      version: 1,
      questions: [
        {
          id: 1,
          position: 1,
          type: "MCQ_SINGLE",
          prompt: "What is 2 + 2?",
          points: 10,
          options: [
            { id: "opt-1", text: "3", correct: false },
            { id: "opt-2", text: "4", correct: true },
          ],
        },
        {
          id: 2,
          position: 2,
          type: "DESCRIPTIVE",
          prompt: "Explain gravity.",
          points: 40,
        },
      ],
    };

    it("renders existing assignment details and questions in builder", async () => {
      assignmentApi.getInstructorAssignment.mockResolvedValueOnce(
        mockAssignment,
      );

      renderWithProviders(<AssignmentBuilderPage />, {
        route: "/instructor/courses/1/assignments/101",
        path: "/instructor/courses/:courseId/assignments/:assignmentId",
      });

      expect(
        await screen.findByDisplayValue("Midterm Exam"),
      ).toBeInTheDocument();
      expect(
        screen.getByDisplayValue("Answer all questions thoroughly."),
      ).toBeInTheDocument();
      expect(screen.getByText("Question 1")).toBeInTheDocument();
      expect(screen.getByDisplayValue("What is 2 + 2?")).toBeInTheDocument();
      expect(screen.getByText("Question 2")).toBeInTheDocument();
      expect(screen.getByDisplayValue("Explain gravity.")).toBeInTheDocument();
    });

    it("displays locked banner and disables locked controls when attempts exist", async () => {
      const lockedAssignment = {
        ...mockAssignment,
        attemptCount: 3, // locked!
        status: "PUBLISHED",
      };
      assignmentApi.getInstructorAssignment.mockResolvedValueOnce(
        lockedAssignment,
      );

      renderWithProviders(<AssignmentBuilderPage />, {
        route: "/instructor/courses/1/assignments/101",
        path: "/instructor/courses/:courseId/assignments/:assignmentId",
      });

      expect(await screen.findByText(/Assignment Locked/i)).toBeInTheDocument();
      expect(
        screen.getByText(/Learners have started attempts/i),
      ).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Delete" })).toBeDisabled();
    });

    it("displays publish validation errors when publishing fails", async () => {
      assignmentApi.getInstructorAssignment.mockResolvedValueOnce(
        mockAssignment,
      );
      const publishError = new Error("Publish validation failed");
      publishError.fieldErrors = [
        {
          field: "questions[0].options",
          message: "Options must have at least 2 entries",
        },
        { field: "maxPoints", message: "Max points must be greater than zero" },
      ];
      assignmentApi.publishAssignment.mockRejectedValueOnce(publishError);

      const user = userEvent.setup();
      renderWithProviders(<AssignmentBuilderPage />, {
        route: "/instructor/courses/1/assignments/101",
        path: "/instructor/courses/:courseId/assignments/:assignmentId",
      });

      const publishBtn = await screen.findByRole("button", {
        name: /Publish Assignment/i,
      });
      await user.click(publishBtn);

      await waitFor(() => {
        expect(
          screen.getByText(/Please fix the following validation errors/i),
        ).toBeInTheDocument();
      });
      expect(
        screen.getByText(/Options must have at least 2 entries/i),
      ).toBeInTheDocument();
    });

    it("allows adding a new question and toggling options", async () => {
      assignmentApi.getInstructorAssignment.mockResolvedValueOnce({
        ...mockAssignment,
        questions: [],
      });
      assignmentApi.replaceQuestions.mockResolvedValueOnce({
        ...mockAssignment,
        questions: [
          {
            id: 10,
            position: 1,
            type: "MCQ_SINGLE",
            prompt: "New question prompt",
            points: 10,
            options: [
              { id: "o1", text: "Option A", correct: true },
              { id: "o2", text: "Option B", correct: false },
            ],
          },
        ],
      });

      const user = userEvent.setup();
      renderWithProviders(<AssignmentBuilderPage />, {
        route: "/instructor/courses/1/assignments/101",
        path: "/instructor/courses/:courseId/assignments/:assignmentId",
      });

      const addBtn = await screen.findByRole("button", {
        name: /\+ Single Choice/i,
      });
      await user.click(addBtn);

      expect(screen.getByText("Question 1")).toBeInTheDocument();
    });

    it("restricts question creation to MCQ types only (no descriptive or code buttons)", async () => {
      assignmentApi.getInstructorAssignment.mockResolvedValueOnce({
        ...mockAssignment,
        questions: [],
      });

      renderWithProviders(<AssignmentBuilderPage />, {
        route: "/instructor/courses/1/assignments/101",
        path: "/instructor/courses/:courseId/assignments/:assignmentId",
      });

      expect(
        await screen.findByRole("button", { name: /\+ Single Choice \(MCQ\)/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /\+ Multi Choice \(MCQ\)/i }),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: /\+ Descriptive/i }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: /\+ Code/i }),
      ).not.toBeInTheDocument();
    });
  });

  describe("InstructorAssignmentsListPage", () => {
    it("creates MCQ assessment and does not offer project assignment option", async () => {
      assignmentApi.listInstructorAssignments.mockResolvedValueOnce({
        content: [],
        totalElements: 0,
      });
      assignmentApi.createAssignment.mockResolvedValueOnce({
        id: 105,
        title: "Database Quiz",
      });

      const user = userEvent.setup();
      renderWithProviders(<InstructorAssignmentsListPage />, {
        route: "/instructor/courses/1/assignments",
        path: "/instructor/courses/:courseId/assignments",
      });

      const createBtn = await screen.findByRole("button", {
        name: /\+ Create Assignment/i,
      });
      await user.click(createBtn);

      expect(
        await screen.findByRole("heading", { name: "Create New MCQ Assessment" }),
      ).toBeInTheDocument();
      expect(screen.queryByText("Project Assignment")).not.toBeInTheDocument();

      const titleInput = screen.getByPlaceholderText(/Distributed Consensus Quiz/i);
      await user.type(titleInput, "Database Quiz");

      const submitBtn = screen.getByRole("button", {
        name: "Create & Open Builder",
      });
      await user.click(submitBtn);

      expect(assignmentApi.createAssignment).toHaveBeenCalledWith(
        "1",
        expect.objectContaining({
          kind: "QUESTIONS",
          title: "Database Quiz",
          allowFiles: false,
          allowText: false,
        }),
      );
    });
  });

  describe("TakeAssignmentPage (Learner Flow)", () => {
    const mockDetail = {
      id: 201,
      courseId: 1,
      title: "Algorithms Assessment",
      kind: "QUESTIONS",
      instructions: "Solve the algorithmic questions.",
      maxPoints: 100,
      timeLimitMinutes: 45,
      allowLate: false,
      allowText: false,
      allowFiles: false,
      hasAttempt: false,
      attemptStatus: null,
    };

    it("shows Before Start view with rules and confirms timed start", async () => {
      assignmentApi.getLearnerAssignmentDetail.mockResolvedValueOnce(
        mockDetail,
      );
      assignmentApi.startAttempt.mockResolvedValueOnce({
        submissionId: 501,
        assignmentId: 201,
        startedAt: new Date().toISOString(),
        deadlineAt: new Date(Date.now() + 45 * 60 * 1000).toISOString(),
        serverNow: new Date().toISOString(),
      });

      const user = userEvent.setup();
      renderWithProviders(<TakeAssignmentPage />, {
        route: "/learning/assignments/201",
        path: "/learning/assignments/:assignmentId",
      });

      expect(
        await screen.findByText("Algorithms Assessment"),
      ).toBeInTheDocument();
      expect(screen.getByText("45 minutes")).toBeInTheDocument();
      expect(screen.getByText("Strict deadline")).toBeInTheDocument();

      // Click Start Timed Assessment
      const startBtn = screen.getByRole("button", {
        name: /Start Timed Assessment/i,
      });
      await user.click(startBtn);

      // Verify timed confirmation dialog appears
      expect(
        await screen.findByRole("heading", { name: "Start Timed Assessment" }),
      ).toBeInTheDocument();
      expect(screen.getByText(/cannot be paused/i)).toBeInTheDocument();

      // Confirm start
      const confirmBtn = screen.getByRole("button", {
        name: /I'm Ready, Begin/i,
      });
      await user.click(confirmBtn);

      expect(assignmentApi.startAttempt).toHaveBeenCalledWith(201);
    });

    it("renders in-progress questions and allows answering MCQ and code", async () => {
      const inProgressDetail = {
        ...mockDetail,
        hasAttempt: true,
        attemptStatus: "IN_PROGRESS",
      };
      const mockAttempt = {
        submissionId: 501,
        assignmentId: 201,
        startedAt: new Date().toISOString(),
        deadlineAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
        serverNow: new Date().toISOString(),
        version: 1,
        answers: [],
        questions: [
          {
            id: 1,
            position: 1,
            type: "MCQ_SINGLE",
            prompt: "What is Big-O of binary search?",
            points: 20,
            options: [
              { id: "o1", text: "O(n)" },
              { id: "o2", text: "O(log n)" },
            ],
          },
          {
            id: 2,
            position: 2,
            type: "CODE",
            prompt: "Write binary search function",
            points: 80,
            codeLanguage: "python",
          },
        ],
      };

      assignmentApi.getLearnerAssignmentDetail.mockResolvedValueOnce(
        inProgressDetail,
      );
      assignmentApi.getAttempt.mockResolvedValueOnce(mockAttempt);
      assignmentApi.saveDraft.mockResolvedValue({
        version: 2,
        savedAt: new Date().toISOString(),
      });

      const user = userEvent.setup();
      renderWithProviders(<TakeAssignmentPage />, {
        route: "/learning/assignments/201",
        path: "/learning/assignments/:assignmentId",
      });

      expect(
        await screen.findByText("What is Big-O of binary search?"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("Write binary search function"),
      ).toBeInTheDocument();
      expect(screen.getByText("Language: python")).toBeInTheDocument();

      // Answer MCQ
      const opt2 = screen.getByLabelText("O(log n)");
      await user.click(opt2);
      expect(opt2).toBeChecked();

      // Answer Code (monospace textarea)
      const codeTextarea = screen.getByPlaceholderText(
        "// Enter your solution here...",
      );
      await user.type(codeTextarea, "def binary_search(arr): pass");
      expect(codeTextarea).toHaveValue("def binary_search(arr): pass");
    });

    it("opens submit confirmation dialog and warns about unanswered questions", async () => {
      const inProgressDetail = {
        ...mockDetail,
        hasAttempt: true,
        attemptStatus: "IN_PROGRESS",
      };
      const mockAttempt = {
        submissionId: 501,
        assignmentId: 201,
        startedAt: new Date().toISOString(),
        deadlineAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
        serverNow: new Date().toISOString(),
        version: 1,
        answers: [],
        questions: [
          {
            id: 1,
            position: 1,
            type: "MCQ_SINGLE",
            prompt: "Q1 prompt",
            points: 10,
            options: [{ id: "o1", text: "Option A" }],
          },
          {
            id: 2,
            position: 2,
            type: "DESCRIPTIVE",
            prompt: "Q2 prompt",
            points: 10,
          },
        ],
      };

      assignmentApi.getLearnerAssignmentDetail.mockResolvedValueOnce(
        inProgressDetail,
      );
      assignmentApi.getAttempt.mockResolvedValueOnce(mockAttempt);
      assignmentApi.saveDraft.mockResolvedValue({
        version: 1,
        savedAt: new Date().toISOString(),
      });

      const user = userEvent.setup();
      renderWithProviders(<TakeAssignmentPage />, {
        route: "/learning/assignments/201",
        path: "/learning/assignments/:assignmentId",
      });

      const submitBtn = await screen.findByRole("button", {
        name: "Submit Assignment",
      });
      await user.click(submitBtn);

      // Confirmation dialog should warn about unanswered questions
      expect(await screen.findByText(/Unanswered Items:/i)).toBeInTheDocument();
      expect(screen.getByText(/Question 1/i)).toBeInTheDocument();
      expect(screen.getByText(/Question 2/i)).toBeInTheDocument();
    });

    it("displays result view with graded score and shows correct answers only when provided", async () => {
      const gradedDetail = {
        ...mockDetail,
        hasAttempt: true,
        attemptStatus: "GRADED",
      };
      const mockSubmission = {
        submissionId: 501,
        assignmentId: 201,
        assignmentTitle: "Algorithms Assessment",
        kind: "QUESTIONS",
        maxPoints: 100,
        status: "GRADED",
        totalPoints: 95,
        overallFeedback: "Excellent understanding of data structures!",
        questions: [
          {
            id: 1,
            prompt: "What is Big-O of binary search?",
            points: 20,
            type: "MCQ_SINGLE",
            options: [
              { id: "o1", text: "O(n)", correct: false },
              { id: "o2", text: "O(log n)", correct: true },
            ],
          },
        ],
        answers: [
          {
            questionId: 1,
            selectedOptionIds: ["o2"],
            autoPoints: 20,
            points: 20,
            feedback: "Correct!",
          },
        ],
      };

      assignmentApi.getLearnerAssignmentDetail.mockResolvedValueOnce(
        gradedDetail,
      );
      assignmentApi.getLearnerSubmission.mockResolvedValueOnce(mockSubmission);

      renderWithProviders(<TakeAssignmentPage />, {
        route: "/learning/assignments/201",
        path: "/learning/assignments/:assignmentId",
      });

      expect(await screen.findByText("Final Score")).toBeInTheDocument();
      expect(screen.getByText(/95 \/ 100 pts/i)).toBeInTheDocument();
      expect(
        screen.getByText("Excellent understanding of data structures!"),
      ).toBeInTheDocument();
      expect(screen.getByText("✓ Correct answer")).toBeInTheDocument();
    });
  });

  describe("ManualGradingPage (Instructor Flow)", () => {
    const mockSubmission = {
      submissionId: 701,
      assignmentId: 101,
      assignmentTitle: "Midterm Exam",
      kind: "QUESTIONS",
      maxPoints: 50,
      status: "SUBMITTED",
      learner: { id: 99, displayName: "Jane Doe" },
      startedAt: new Date().toISOString(),
      submittedAt: new Date().toISOString(),
      version: 1,
      questions: [
        {
          questionId: 1,
          position: 1,
          type: "MCQ_SINGLE",
          prompt: "What is 2 + 2?",
          points: 10,
          options: [
            { id: "opt-1", text: "3", correct: false },
            { id: "opt-2", text: "4", correct: true },
          ],
          answer: {
            selectedOptionIds: ["opt-2"],
            autoPoints: 10,
          },
        },
        {
          questionId: 2,
          position: 2,
          type: "DESCRIPTIVE",
          prompt: "Explain gravity.",
          points: 40,
          answer: {
            answerText: "Gravity is a fundamental interaction...",
          },
        },
      ],
    };

    it("validates bounds and completeness on question grading", async () => {
      assignmentApi.getInstructorSubmission.mockResolvedValueOnce(
        mockSubmission,
      );

      const user = userEvent.setup();
      renderWithProviders(<ManualGradingPage />, {
        route: "/instructor/submissions/701",
        path: "/instructor/submissions/:submissionId",
      });

      expect(
        await screen.findByText("Grade: Midterm Exam"),
      ).toBeInTheDocument();
      expect(screen.getByText("Jane Doe")).toBeInTheDocument();

      // Submit without points for question 2
      const saveBtn = screen.getByRole("button", { name: /Save Grade/i });
      await user.click(saveBtn);

      expect(
        await screen.findByText("Points are required for this question."),
      ).toBeInTheDocument();

      // Type points above question max (max 40)
      const q2Input = screen.getByLabelText(/Assigned Points \* \(Max: 40\)/i);
      await user.type(q2Input, "55");
      await user.click(saveBtn);

      expect(
        await screen.findByText("Points must be between 0 and 40."),
      ).toBeInTheDocument();
    });

    it("displays version conflict dialog when saving returns 409 conflict", async () => {
      assignmentApi.getInstructorSubmission.mockResolvedValue(mockSubmission);
      const conflictError = new Error("Version conflict");
      conflictError.status = 409;
      assignmentApi.gradeSubmission.mockRejectedValueOnce(conflictError);

      const user = userEvent.setup();
      renderWithProviders(<ManualGradingPage />, {
        route: "/instructor/submissions/701",
        path: "/instructor/submissions/:submissionId",
      });

      expect(await screen.findByText("Jane Doe")).toBeInTheDocument();

      // Set valid score
      const q2Input = screen.getByLabelText(/Assigned Points \* \(Max: 40\)/i);
      await user.type(q2Input, "35");

      const saveBtn = screen.getByRole("button", { name: /Save Grade/i });
      await user.click(saveBtn);

      expect(
        await screen.findByText("Grading Conflict Detected"),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /Reload Latest Version/i }),
      ).toBeInTheDocument();
    });
  });

  describe("Slots Integration", () => {
    it("renders CourseEditorAssignmentsHeaderLink with correct link", () => {
      renderWithProviders(<CourseEditorAssignmentsHeaderLink courseId="42" />);
      const link = screen.getByRole("link", { name: "Assignments" });
      expect(link).toHaveAttribute(
        "href",
        "/instructor/courses/42/assignments",
      );
    });

    it("renders PlayerHeaderAssignmentsLink with correct link", () => {
      renderWithProviders(<PlayerHeaderAssignmentsLink courseId="88" />);
      const link = screen.getByRole("link", { name: "Assignments" });
      expect(link).toHaveAttribute("href", "/learning/courses/88/assignments");
    });

    it("renders LessonAssignmentsAction when lesson has assignments and hides when empty", async () => {
      assignmentApi.getCourseAssignments.mockResolvedValueOnce([
        { id: 11, title: "Quiz 1", status: "NOT_STARTED" },
      ]);

      const { rerender } = renderWithProviders(
        <LessonAssignmentsAction courseId="1" lessonId="2" />,
      );

      expect(
        await screen.findByText("Assignments for this lesson (1)"),
      ).toBeInTheDocument();
      expect(screen.getByText("Quiz 1")).toBeInTheDocument();

      // Empty case
      assignmentApi.getCourseAssignments.mockResolvedValueOnce([]);
      rerender(
        <MemoryRouter>
          <QueryClientProvider client={new QueryClient()}>
            <LessonAssignmentsAction courseId="1" lessonId="99" />
          </QueryClientProvider>
        </MemoryRouter>,
      );

      await waitFor(() => {
        expect(
          screen.queryByText(/Assignments for this lesson/i),
        ).not.toBeInTheDocument();
      });
    });
  });

  describe("Web Storage Security Guard", () => {
    it("asserts that no attempts, answers, or drafts are ever written to localStorage or sessionStorage", () => {
      expect(localStorage.length).toBe(0);
      expect(sessionStorage.length).toBe(0);

      // Verify no keys like 'draft', 'attempt', 'answer', 'assignment' exist
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        expect(key).not.toMatch(/attempt|answer|draft|submission/i);
      }
      for (let i = 0; i < sessionStorage.length; i++) {
        const key = sessionStorage.key(i);
        expect(key).not.toMatch(/attempt|answer|draft|submission/i);
      }
    });
  });
});
