import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { ApiError } from "../../shared/api/ApiError";
import {
  apiRequest,
  setAuthExpiredHandler,
  setAuthRecoveryHandler,
  setAuthTokenProvider,
} from "../../shared/api/client";
import { ProtectedRoute } from "../../app/ProtectedRoute";
import {
  ForgotPasswordPage,
  GuestRoute,
  LoginPage,
  RegisterPage,
  ResetPasswordPage,
  VerifyEmailPage,
} from "./AuthPages";
import * as authApi from "./authApi";
import { useAuthStore } from "./authStore";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.history.replaceState({}, "", "/");
  setAuthTokenProvider(async () => undefined);
  setAuthRecoveryHandler(async () => false);
  setAuthExpiredHandler(() => {});
  useAuthStore.getState().clearSession();
});

describe("auth state and routing", () => {
  it("keeps access tokens in memory without writing browser storage", () => {
    const localWrite = vi.spyOn(window.localStorage, "setItem");
    const sessionWrite = vi.spyOn(window.sessionStorage, "setItem");
    useAuthStore.getState().setSession({
      accessToken: "memory-only-token",
      user: { id: 7, email: "learner@example.com", lastMode: "STUDENT" },
    });

    expect(useAuthStore.getState()).toMatchObject({
      accessToken: "memory-only-token",
      status: "authenticated",
    });
    expect(localWrite).not.toHaveBeenCalled();
    expect(sessionWrite).not.toHaveBeenCalled();
  });

  it("redirects protected routes to login with the original destination", () => {
    useAuthStore.setState({ status: "anonymous" });
    function LoginLocation() {
      const location = useLocation();
      return <output>{location.state?.from.pathname}</output>;
    }

    render(
      <MemoryRouter initialEntries={["/profile"]}>
        <Routes>
          <Route
            element={
              <ProtectedRoute>
                <p>private content</p>
              </ProtectedRoute>
            }
            path="/profile"
          />
          <Route element={<LoginLocation />} path="/login" />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText("/profile")).toBeInTheDocument();
    expect(screen.queryByText("private content")).not.toBeInTheDocument();
  });

  it("redirects authenticated visitors away from the login page", () => {
    useAuthStore.setState({ status: "authenticated" });
    render(
      <MemoryRouter initialEntries={["/login"]}>
        <Routes>
          <Route
            element={
              <GuestRoute>
                <LoginPage />
              </GuestRoute>
            }
            path="/login"
          />
          <Route element={<p>home page</p>} path="/" />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText("home page")).toBeInTheDocument();
  });
});

describe("auth forms", () => {
  it("validates login fields with Zod", async () => {
    useAuthStore.setState({ status: "anonymous" });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );
    await user.click(screen.getByRole("button", { name: "Log in" }));
    expect(
      await screen.findByText("Enter a valid email address"),
    ).toBeVisible();
    expect(
      screen.getByText("Password must be at least 8 characters"),
    ).toBeVisible();
  });

  it("maps backend fieldErrors to the matching registration input", async () => {
    const original = useAuthStore.getState().register;
    useAuthStore.setState({
      status: "anonymous",
      register: vi.fn().mockRejectedValue(
        new ApiError({
          status: 400,
          code: "VALIDATION_FAILED",
          message: "Request validation failed",
          fieldErrors: [
            { field: "password", message: "Password policy failed" },
            { field: "username", message: "Username is already taken. Please choose another username." },
          ],
        }),
      ),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText("Display name"), "Person");
    await user.type(screen.getByLabelText("Username"), "person_user");
    await user.type(screen.getByLabelText("Email"), "person@example.com");
    await user.type(screen.getByLabelText("Password"), "valid-password");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("Password policy failed")).toBeVisible();
    expect(await screen.findByText("Username is already taken. Please choose another username.")).toBeVisible();
    useAuthStore.setState({ register: original });
  });

  it("validates username rules on client before submitting registration", async () => {
    const registerMock = vi.fn();
    useAuthStore.setState({
      status: "anonymous",
      register: registerMock,
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>,
    );

    // Empty username validation
    await user.type(screen.getByLabelText("Display name"), "Ada Lovelace");
    await user.type(screen.getByLabelText("Email"), "ada@example.com");
    await user.type(screen.getByLabelText("Password"), "valid-password");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("Username is required")).toBeVisible();
    expect(registerMock).not.toHaveBeenCalled();

    // Username starting with a number
    await user.type(screen.getByLabelText("Username"), "1ada");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("Username must start with a letter")).toBeVisible();

    // Username too short (< 3)
    await user.clear(screen.getByLabelText("Username"));
    await user.type(screen.getByLabelText("Username"), "ad");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("Username must be at least 3 characters")).toBeVisible();

    // Reserved username
    await user.clear(screen.getByLabelText("Username"));
    await user.type(screen.getByLabelText("Username"), "admin");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("This username is reserved. Please choose another username.")).toBeVisible();

    // Invalid characters (spaces/special characters)
    await user.clear(screen.getByLabelText("Username"));
    await user.type(screen.getByLabelText("Username"), "ada-lovelace!");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("Username can only contain letters, numbers, and underscores")).toBeVisible();
  });

  it("validates forgot-password email and the reset code/password", async () => {
    const user = userEvent.setup();
    const { unmount } = render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>,
    );
    await user.click(screen.getByRole("button", { name: "Send reset code" }));
    expect(
      await screen.findByText("Enter a valid email address"),
    ).toBeVisible();
    unmount();

    render(
      <MemoryRouter>
        <ResetPasswordPage />
      </MemoryRouter>,
    );
    await user.click(screen.getByRole("button", { name: "Update password" }));
    expect(await screen.findByText("Enter the six-digit code")).toBeVisible();
    expect(
      screen.getByText("Password must be at least 8 characters"),
    ).toBeVisible();
  });

  it("validates email verification inputs with Zod and verifies successfully", async () => {
    const user = userEvent.setup();
    const verifySpy = vi
      .spyOn(authApi, "verifyEmailRequest")
      .mockResolvedValue({
        message: "Email verified successfully. You can now log in.",
      });

    render(
      <MemoryRouter initialEntries={["/verify-email?email=test%40example.com"]}>
        <VerifyEmailPage />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole("button", { name: "Verify email" }));
    expect(await screen.findByText("Enter the six-digit code")).toBeVisible();

    await user.type(screen.getByLabelText("Six-digit code"), "123456");
    await user.click(screen.getByRole("button", { name: "Verify email" }));

    expect(verifySpy).toHaveBeenCalledWith({
      email: "test@example.com",
      otp: "123456",
    });
    expect(await screen.findByText("Email verified!")).toBeVisible();
    expect(
      screen.getByRole("link", { name: "Continue to log in" }),
    ).toBeVisible();
  });

  it("handles resend verification OTP button state", async () => {
    render(
      <MemoryRouter initialEntries={["/verify-email"]}>
        <VerifyEmailPage />
      </MemoryRouter>,
    );

    const resendBtn = screen.getByRole("button", { name: /Resend code/i });
    expect(resendBtn).toBeDisabled();
  });

  it("displays verification required banner on login when server returns EMAIL_NOT_VERIFIED", async () => {
    const user = userEvent.setup();
    const originalLogin = useAuthStore.getState().login;
    useAuthStore.setState({
      status: "anonymous",
      login: vi.fn().mockRejectedValue(
        new ApiError({
          status: 403,
          code: "EMAIL_NOT_VERIFIED",
          message: "Your email address has not been verified.",
          fieldErrors: [],
        }),
      ),
    });

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText("Email"), "unverified@example.com");
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    expect(await screen.findByText("Email verification required")).toBeVisible();
    expect(
      screen.getByRole("link", { name: /Verify your email/i }),
    ).toHaveAttribute("href", "/verify-email?email=unverified%40example.com");

    useAuthStore.setState({ login: originalLogin });
  });

  it("executes 2-step password reset workflow in ResetPasswordPage", async () => {
    const user = userEvent.setup();
    const verifyOtpSpy = vi
      .spyOn(authApi, "verifyResetOtpRequest")
      .mockResolvedValue({
        resetToken: "sha256-test-reset-token",
        message: "Reset code verified successfully",
      });
    const resetPwSpy = vi
      .spyOn(authApi, "resetPasswordRequest")
      .mockResolvedValue({
        message: "Password reset completed successfully",
      });

    render(
      <MemoryRouter
        initialEntries={["/reset-password?email=reset%40example.com"]}
      >
        <ResetPasswordPage />
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText("Six-digit code"), "654321");
    await user.type(
      screen.getByLabelText("New password"),
      "newSecretPassword123",
    );
    await user.click(screen.getByRole("button", { name: "Update password" }));

    expect(verifyOtpSpy).toHaveBeenCalledWith({
      email: "reset@example.com",
      otp: "654321",
    });
    expect(resetPwSpy).toHaveBeenCalledWith({
      email: "reset@example.com",
      resetToken: "sha256-test-reset-token",
      newPassword: "newSecretPassword123",
    });

    expect(await screen.findByText("Password updated!")).toBeVisible();
  });
});

describe("single-flight token refresh", () => {
  it("refreshes once for concurrent 401s and retries each request", async () => {
    let accessToken = "expired-token";
    let refreshCalls = 0;
    setAuthTokenProvider(async () => accessToken);
    setAuthRecoveryHandler(async () => {
      refreshCalls += 1;
      await new Promise((resolve) => setTimeout(resolve, 10));
      accessToken = "fresh-token";
      return true;
    });
    const fetchMock = vi.fn(async (_url, options) => {
      const authorized =
        options.headers.get("Authorization") === "Bearer fresh-token";
      return new Response(JSON.stringify({ ok: authorized }), {
        status: authorized ? 200 : 401,
        headers: { "Content-Type": "application/json" },
      });
    });
    vi.stubGlobal("fetch", fetchMock);

    const results = await Promise.all([
      apiRequest("users/me"),
      apiRequest("users/me/mode"),
    ]);

    expect(results).toEqual([{ ok: true }, { ok: true }]);
    expect(refreshCalls).toBe(1);
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(fetchMock.mock.calls[2][1].headers.get("Authorization")).toBe(
      "Bearer fresh-token",
    );
    expect(fetchMock.mock.calls[3][1].headers.get("Authorization")).toBe(
      "Bearer fresh-token",
    );
  });

  it("clears state and preserves the current route when refresh fails", async () => {
    window.history.replaceState({}, "", "/profile?tab=about#bio");
    setAuthRecoveryHandler(async () => false);
    const errorBody = {
      status: 401,
      code: "UNAUTHORIZED",
      message: "Authentication is required",
      fieldErrors: [],
    };
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(errorBody), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );

    await expect(apiRequest("users/me")).rejects.toMatchObject({
      status: 401,
      code: "UNAUTHORIZED",
    });
    await waitFor(() => {
      expect(window.location.pathname).toBe("/login");
      expect(new URLSearchParams(window.location.search).get("returnTo")).toBe(
        "/profile?tab=about#bio",
      );
    });
  });
});
