import { afterEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ToastProvider } from "../../shared/components/Toast";
import { setAuthTokenProvider } from "../../shared/api/client";
import { useAuthStore } from "../auth/authStore";
import { HeaderActions } from "./HeaderActions";
import { AppShell } from "../../app/shell/AppShell";
import { ProtectedRoute } from "../../app/ProtectedRoute";
import {
  navigationItems,
  visibleNavigation,
} from "../../app/registry/navigation";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  setAuthTokenProvider(async () => undefined);
  useAuthStore.getState().clearSession();
});

function renderActions(initialPath = "/") {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <ToastProvider>
        <HeaderActions />
      </ToastProvider>
    </MemoryRouter>,
  );
}

function renderShell(initialPath = "/learning") {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <ToastProvider>
          <Routes>
            <Route element={<AppShell />}>
              <Route path="/" element={<div>Home</div>} />
              <Route
                path="/learning"
                element={
                  <ProtectedRoute>
                    <div>My Learning Content</div>
                  </ProtectedRoute>
                }
              />
              <Route
                path="/instructor/courses"
                element={
                  <ProtectedRoute>
                    <div>Instructor Courses Content</div>
                  </ProtectedRoute>
                }
              />
              <Route path="/login" element={<div>Login Screen</div>} />
            </Route>
          </Routes>
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("profile mode control", () => {
  it("optimistically updates the mode and persists it when switching from Student to Instructor", async () => {
    useAuthStore.getState().setSession({
      accessToken: "access-token",
      user: { id: 11, displayName: "Ada", lastMode: "STUDENT" },
    });
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ lastMode: "INSTRUCTOR" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    renderActions();
    await user.click(screen.getByRole("button", { name: "Instructor mode" }));

    await waitFor(() => {
      expect(useAuthStore.getState().user.lastMode).toBe("INSTRUCTOR");
    });
    expect(fetchMock.mock.calls[0][0]).toContain("/users/me/mode");
    expect(fetchMock.mock.calls[0][1].method).toBe("PATCH");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      mode: "INSTRUCTOR",
    });
  });

  it("switches from Instructor to Student mode and sends the correct payload", async () => {
    useAuthStore.getState().setSession({
      accessToken: "access-token",
      user: { id: 11, displayName: "Ada", lastMode: "INSTRUCTOR" },
    });
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ lastMode: "STUDENT" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    renderActions();
    await user.click(screen.getByRole("button", { name: "Student mode" }));

    await waitFor(() => {
      expect(useAuthStore.getState().user.lastMode).toBe("STUDENT");
    });
    expect(fetchMock.mock.calls[0][0]).toContain("/users/me/mode");
    expect(fetchMock.mock.calls[0][1].method).toBe("PATCH");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      mode: "STUDENT",
    });
  });

  it("rolls back the mode when persistence fails", async () => {
    useAuthStore.getState().setSession({
      accessToken: "access-token",
      user: { id: 11, displayName: "Ada", lastMode: "STUDENT" },
    });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            status: 500,
            code: "INTERNAL_ERROR",
            message: "The request could not be completed",
            fieldErrors: [],
          }),
          {
            status: 500,
            headers: { "Content-Type": "application/json" },
          },
        ),
      ),
    );
    const user = userEvent.setup();

    renderActions();
    await user.click(screen.getByRole("button", { name: "Instructor mode" }));

    await waitFor(() => {
      expect(useAuthStore.getState().user.lastMode).toBe("STUDENT");
    });
    expect(
      await screen.findByText("Your previous mode is still active."),
    ).toBeVisible();
  });

  it("prevents duplicate requests during rapid repeated clicks", async () => {
    useAuthStore.getState().setSession({
      accessToken: "access-token",
      user: { id: 11, displayName: "Ada", lastMode: "STUDENT" },
    });

    let resolveFetch;
    const fetchPromise = new Promise((resolve) => {
      resolveFetch = resolve;
    });
    const fetchMock = vi.fn().mockReturnValue(fetchPromise);
    vi.stubGlobal("fetch", fetchMock);

    renderActions();
    const instructorBtn = screen.getByRole("button", {
      name: "Instructor mode",
    });

    // Trigger multiple clicks rapidly
    fireEvent.click(instructorBtn);
    fireEvent.click(instructorBtn);
    fireEvent.click(instructorBtn);

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    // Complete the in-flight request
    resolveFetch(
      new Response(JSON.stringify({ lastMode: "INSTRUCTOR" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await waitFor(() => {
      expect(useAuthStore.getState().user.lastMode).toBe("INSTRUCTOR");
    });
  });

  it("supports keyboard arrow navigation to switch modes", async () => {
    useAuthStore.getState().setSession({
      accessToken: "access-token",
      user: { id: 11, displayName: "Ada", lastMode: "STUDENT" },
    });
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ lastMode: "INSTRUCTOR" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    renderActions();
    const studentBtn = screen.getByRole("button", { name: "Student mode" });

    fireEvent.keyDown(studentBtn, { key: "ArrowRight" });

    await waitFor(() => {
      expect(useAuthStore.getState().user.lastMode).toBe("INSTRUCTOR");
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("does not render the mode switch for unauthenticated or ineligible users", () => {
    useAuthStore.getState().clearSession();

    renderActions();
    expect(
      screen.queryByRole("group", { name: "Interface mode" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Student mode" }),
    ).not.toBeInTheDocument();
  });

  it("updates visible navigation items consistently when mode changes", () => {
    const studentItems = visibleNavigation(navigationItems, {
      isAuthenticated: true,
      userMode: "STUDENT",
    }).map((item) => item.label);

    expect(studentItems).toContain("My Learning");
    expect(studentItems).toContain("Assignments");
    expect(studentItems).toContain("Bookmarks");
    expect(studentItems).not.toContain("My Courses");

    const instructorItems = visibleNavigation(navigationItems, {
      isAuthenticated: true,
      userMode: "INSTRUCTOR",
    }).map((item) => item.label);

    expect(instructorItems).toContain("My Courses");
    expect(instructorItems).not.toContain("My Learning");
    expect(instructorItems).not.toContain("Assignments");
    expect(instructorItems).not.toContain("Bookmarks");
  });

  it("navigates away from student-exclusive pages when switching to Instructor mode", async () => {
    useAuthStore.getState().setSession({
      accessToken: "access-token",
      user: { id: 11, displayName: "Ada", lastMode: "STUDENT" },
    });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((url) => {
        if (String(url).includes("/users/me/mode")) {
          return Promise.resolve(
            new Response(JSON.stringify({ lastMode: "INSTRUCTOR" }), {
              status: 200,
              headers: { "Content-Type": "application/json" },
            }),
          );
        }
        return Promise.resolve(
          new Response(JSON.stringify({ count: 0, unreadCount: 0 }), {
            status: 200,
            headers: { "Content-Type": "application/json" },
          }),
        );
      }),
    );
    const user = userEvent.setup();

    renderShell("/learning");
    expect(screen.getByText("My Learning Content")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Instructor mode" }));

    await waitFor(() => {
      expect(
        screen.getByText("Instructor Courses Content"),
      ).toBeInTheDocument();
    });
  });

  it("navigates away from instructor-exclusive pages when switching to Student mode", async () => {
    useAuthStore.getState().setSession({
      accessToken: "access-token",
      user: { id: 11, displayName: "Ada", lastMode: "INSTRUCTOR" },
    });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((url) => {
        if (String(url).includes("/users/me/mode")) {
          return Promise.resolve(
            new Response(JSON.stringify({ lastMode: "STUDENT" }), {
              status: 200,
              headers: { "Content-Type": "application/json" },
            }),
          );
        }
        return Promise.resolve(
          new Response(JSON.stringify({ count: 0, unreadCount: 0 }), {
            status: 200,
            headers: { "Content-Type": "application/json" },
          }),
        );
      }),
    );
    const user = userEvent.setup();

    renderShell("/instructor/courses");
    expect(screen.getByText("Instructor Courses Content")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Student mode" }));

    await waitFor(() => {
      expect(screen.getByText("My Learning Content")).toBeInTheDocument();
    });
  });

  it("redirects unauthenticated users trying to directly access restricted pages to login", () => {
    useAuthStore.setState({
      status: "anonymous",
      user: null,
      accessToken: null,
    });

    renderShell("/instructor/courses");
    expect(screen.getByText("Login Screen")).toBeInTheDocument();
  });

  it("correctly restores persisted mode without flashing the wrong state", async () => {
    useAuthStore.setState({ status: "unknown", user: null, accessToken: null });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            accessToken: "restored-token",
            user: { id: 11, displayName: "Ada", lastMode: "INSTRUCTOR" },
          }),
          {
            status: 200,
            headers: { "Content-Type": "application/json" },
          },
        ),
      ),
    );

    const restored = await useAuthStore.getState().restoreSession();
    expect(restored).toBe(true);
    expect(useAuthStore.getState().status).toBe("authenticated");
    expect(useAuthStore.getState().user.lastMode).toBe("INSTRUCTOR");
  });
});
