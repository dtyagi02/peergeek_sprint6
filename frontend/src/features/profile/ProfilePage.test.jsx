import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ToastProvider } from "../../shared/components/Toast";
import { setAuthTokenProvider } from "../../shared/api/client";
import { useAuthStore } from "../auth/authStore";
import { ProfilePage } from "./ProfilePage";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  setAuthTokenProvider(async () => undefined);
  useAuthStore.getState().clearSession();
});

const mockStudentProfile = {
  id: 101,
  username: "alice_dev",
  displayName: "Alice Dev",
  headline: "Computer Science Student",
  bio: "Learning full stack development and cloud architectures.",
  avatarUrl: null,
  role: "USER",
  lastMode: "STUDENT",
  email: "alice@example.com",
  skills: ["React", "Java", "SQL"],
  interests: ["Distributed Systems", "Cloud"],
  teachingExpertise: null,
  githubUrl: "https://github.com/alice",
  linkedinUrl: "https://linkedin.com/in/alice",
  portfolioUrl: "https://alice.dev",
  websiteUrl: null,
  showEducation: true,
  showSocialLinks: true,
  showLearningHistory: true,
  isOwner: true,
  educations: [
    {
      id: 1,
      institution: "State University",
      degree: "B.S.",
      fieldOfStudy: "Computer Science",
      startYear: 2021,
      endYear: 2025,
    },
  ],
  projects: [
    {
      id: 11,
      title: "Distributed KV Store",
      description: "Raft consensus implementation in Java",
      technologies: "Java, Raft, gRPC",
      projectUrl: "https://kv.demo.com",
      repoUrl: "https://github.com/alice/kv-store",
    },
  ],
  achievements: [
    {
      courseId: 5,
      courseTitle: "Introduction to React and State Management",
      completedAt: "2026-09-15T10:00:00Z",
    },
  ],
  instructorStats: {
    totalPublishedCourses: 0,
    totalLearners: 0,
    averageRating: 0.0,
    totalReviews: 0,
    publishedCourses: [],
  },
  learningStats: {
    totalEnrolledCourses: 2,
    coursesCompleted: 1,
    coursesInProgress: 1,
    lessonsCompleted: 12,
  },
  enrolledCourses: [
    {
      courseId: 5,
      title: "Introduction to React and State Management",
      description: "Learn React Hooks and State",
      thumbnailUrl: null,
      instructorName: "Dan Abramov",
      instructorUsername: "dan_a",
      difficulty: "BEGINNER",
      enrollmentStatus: "ACTIVE",
      progressPercent: 100,
      completedLessons: 10,
      totalLessons: 10,
      courseRating: 4.8,
      reviewCount: 45,
      completed: true,
      completedAt: "2026-09-15T10:00:00Z",
    },
    {
      courseId: 12,
      title: "Advanced Java Concurrency",
      description: "Threads, locks, and atomic variables",
      thumbnailUrl: null,
      instructorName: "Brian Goetz",
      instructorUsername: "brian_g",
      difficulty: "ADVANCED",
      enrollmentStatus: "ACTIVE",
      progressPercent: 20,
      completedLessons: 2,
      totalLessons: 10,
      courseRating: 4.9,
      reviewCount: 30,
      completed: false,
      completedAt: null,
    },
  ],
  earnedSkills: [
    {
      skillName: "React",
      sourceCourses: ["Introduction to React and State Management"],
      earnedAt: "2026-09-15T10:00:00Z",
    },
  ],
  createdAt: "2026-01-10T12:00:00Z",
};

const mockPublicInstructorProfile = {
  id: 202,
  username: "prof_turing",
  displayName: "Alan Turing",
  headline: "Professor of Computing",
  bio: "Pioneering computer science and theoretical logic.",
  avatarUrl: null,
  role: "USER",
  lastMode: "INSTRUCTOR",
  email: null,
  skills: ["Theory of Computation", "Algorithms"],
  interests: ["Cryptography", "AI"],
  teachingExpertise: "Automata Theory, Cryptanalysis, Discrete Mathematics",
  githubUrl: "https://github.com/aturing",
  linkedinUrl: null,
  portfolioUrl: null,
  websiteUrl: null,
  showEducation: true,
  showSocialLinks: true,
  showLearningHistory: false,
  isOwner: false,
  educations: [
    {
      id: 2,
      institution: "Cambridge University",
      degree: "Ph.D.",
      fieldOfStudy: "Mathematical Logic",
      startYear: 1935,
      endYear: 1938,
    },
  ],
  projects: [],
  achievements: [],
  instructorStats: {
    totalPublishedCourses: 2,
    totalLearners: 1540,
    averageRating: 4.9,
    totalReviews: 320,
    publishedCourses: [
      {
        id: 77,
        title: "Foundations of Computability",
        description: "Turing machines and undecidability",
        difficulty: "ADVANCED",
        enrolledCount: 890,
        averageRating: 4.95,
        reviewCount: 150,
      },
    ],
  },
  learningStats: null,
  enrolledCourses: [],
  earnedSkills: [],
  createdAt: "2025-05-01T08:00:00Z",
};

function renderProfile(path = "/profile") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ToastProvider>
        <Routes>
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/profile/:username" element={<ProfilePage />} />
        </Routes>
      </ToastProvider>
    </MemoryRouter>,
  );
}

describe("ProfilePage Component", () => {
  it("renders authenticated user's own profile with TEACHING and LEARNING sections, stats, and course cards", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(mockStudentProfile), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );

    renderProfile("/profile");

    // Profile header
    expect(await screen.findByText("Alice Dev")).toBeInTheDocument();
    expect(screen.getByText("@alice_dev")).toBeInTheDocument();
    expect(screen.getByText("Computer Science Student")).toBeInTheDocument();
    expect(screen.getByText("Account: alice@example.com (Private)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit Profile" })).toBeInTheDocument();

    // Section 1: TEACHING
    expect(screen.getByRole("heading", { name: "TEACHING" })).toBeInTheDocument();
    expect(screen.getByText("You haven't published any courses yet.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "+ Create Course" })).toBeInTheDocument();

    // Section 2: LEARNING
    expect(screen.getByRole("heading", { name: "LEARNING" })).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument(); // lessons completed
    expect(screen.getAllByText("Introduction to React and State Management").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Advanced Java Concurrency").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("10 of 10 lessons completed")).toBeInTheDocument();
    expect(screen.getByText("2 of 10 lessons completed")).toBeInTheDocument();

    // Skills: Declared vs Earned
    expect(screen.getByText("Skills I Added")).toBeInTheDocument();
    // "React" exists in both "Skills I Added" and "Skills Earned Through Learning"
    expect(screen.getAllByText("React").length).toBe(2);
    expect(screen.getAllByText("Java").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("SQL")).toBeInTheDocument();

    expect(screen.getByText("Skills Earned Through Learning")).toBeInTheDocument();
    expect(screen.getByText(/Verified by: Introduction to React and State Management/)).toBeInTheDocument();
  });

  it("renders another user's public profile without edit buttons and respects learning privacy settings", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(mockPublicInstructorProfile), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );

    renderProfile("/profile/prof_turing");

    expect(await screen.findByText("Alan Turing")).toBeInTheDocument();
    expect(screen.getByText("@prof_turing")).toBeInTheDocument();
    expect(screen.getByText("Instructor")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit Profile" })).not.toBeInTheDocument();
    expect(screen.queryByText(/Account:/)).not.toBeInTheDocument();

    // Teaching section with real stats
    expect(screen.getByRole("heading", { name: "TEACHING" })).toBeInTheDocument();
    expect(screen.getAllByText("Foundations of Computability").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("1540")).toBeInTheDocument(); // total learners
    expect(screen.getByText("4.9")).toBeInTheDocument(); // average rating

    // Learning section shows private lock banner
    expect(screen.getByRole("heading", { name: "LEARNING" })).toBeInTheDocument();
    expect(screen.getByText("Learning Activity is Private")).toBeInTheDocument();
    expect(screen.queryByText("My Enrolled Courses")).not.toBeInTheDocument();
  });

  it("opens edit profile modal, adds/removes declared skills via chip editor, and submits updated information", async () => {
    const fetchMock = vi.fn().mockImplementation((url, opts) => {
      if (opts?.method === "PUT") {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              ...mockStudentProfile,
              displayName: "Alice Updated",
              headline: "Software Architect",
              skills: ["React", "Java", "TypeScript"],
            }),
            {
              status: 200,
              headers: { "Content-Type": "application/json" },
            },
          ),
        );
      }
      return Promise.resolve(
        new Response(JSON.stringify(mockStudentProfile), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      );
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    renderProfile("/profile");

    expect(await screen.findByText("Alice Dev")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Edit Profile" }));

    expect(screen.getByRole("heading", { name: "Edit Profile" })).toBeInTheDocument();

    const nameInput = screen.getByLabelText("Display Name *");
    await user.clear(nameInput);
    await user.type(nameInput, "Alice Updated");

    const headlineInput = screen.getByLabelText("Headline");
    await user.clear(headlineInput);
    await user.type(headlineInput, "Software Architect");

    // Add a new skill via the chip editor
    const skillInput = screen.getByPlaceholderText("Type skill & press Enter...");
    await user.type(skillInput, "TypeScript");
    await user.click(screen.getByRole("button", { name: "+ Add" }));

    // Remove SQL skill
    const removeSqlBtn = screen.getByRole("button", { name: "Remove skill SQL" });
    await user.click(removeSqlBtn);

    await user.click(screen.getByRole("button", { name: "Save Changes" }));

    await waitFor(() => {
      expect(screen.getByText("Alice Updated")).toBeInTheDocument();
    });
  }, 15000);

  it("adds a new showcased project via modal", async () => {
    const newProject = {
      id: 99,
      title: "Realtime Chat",
      description: "WebSocket chat application",
      technologies: "React, WebSockets",
      projectUrl: "https://chat.example.com",
      repoUrl: null,
    };

    const fetchMock = vi.fn().mockImplementation((url, opts) => {
      if (opts?.method === "POST" && String(url).includes("/users/me/projects")) {
        return Promise.resolve(
          new Response(JSON.stringify(newProject), {
            status: 201,
            headers: { "Content-Type": "application/json" },
          }),
        );
      }
      return Promise.resolve(
        new Response(JSON.stringify(mockStudentProfile), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      );
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    renderProfile("/profile");

    expect(await screen.findByText("Alice Dev")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "+ Add Project" }));

    expect(screen.getByRole("heading", { name: "Add Project" })).toBeInTheDocument();

    await user.type(screen.getByLabelText("Project Title *"), "Realtime Chat");
    await user.type(screen.getByLabelText("Description"), "WebSocket chat application");
    await user.type(screen.getByLabelText("Technologies (comma separated)"), "React, WebSockets");

    await user.click(screen.getByRole("button", { name: "Save Project" }));

    await waitFor(() => {
      expect(screen.getByText("Realtime Chat")).toBeInTheDocument();
    });
  }, 15000);
});
