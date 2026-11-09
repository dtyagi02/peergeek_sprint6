import { ApiError } from "./ApiError";

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? "/api/v1";
let authTokenProvider = async () => undefined;
let authRecoveryHandler = async () => false;
let authExpiredHandler = () => {};
let recoveryInFlight;
const recoveryExcluded = new Set([
  "auth/refresh",
  "auth/login",
  "auth/register",
  "auth/verify-email",
  "auth/resend-verification-otp",
  "auth/forgot-password",
  "auth/verify-reset-otp",
  "auth/reset-password",
]);

export function setAuthTokenProvider(provider) {
  authTokenProvider = provider;
}

export function setAuthRecoveryHandler(handler) {
  authRecoveryHandler = handler;
}

export function setAuthExpiredHandler(handler) {
  authExpiredHandler = handler;
}

export async function getAuthToken() {
  return authTokenProvider();
}

export async function apiRequest(path, options = {}) {
  const authPath = path.replace(/^\//, "");
  const canRecover =
    !options.skipAuthRecovery && !recoveryExcluded.has(authPath);
  const tokenAtStart = await getAuthToken();
  let response = await send(path, options, tokenAtStart);
  if (response.status === 401 && canRecover) {
    try {
      let currentToken = await getAuthToken();
      if (!currentToken || currentToken === tokenAtStart) {
        if (!recoveryInFlight) {
          recoveryInFlight = Promise.resolve(authRecoveryHandler()).finally(
            () => {
              recoveryInFlight = undefined;
            },
          );
        }
        if (!(await recoveryInFlight))
          throw new Error("Session refresh failed");
        currentToken = await getAuthToken();
      }
      response = await send(path, options, currentToken);
      if (response.status === 401) {
        authExpiredHandler();
        redirectToLogin();
      }
    } catch {
      authExpiredHandler();
      redirectToLogin();
    }
  }
  return readResponse(response);
}

async function send(path, options, token) {
  const headers = new Headers(options.headers);
  const requestOptions = {
    ...options,
    headers,
    credentials: options.credentials ?? "include",
  };
  if (options.body instanceof FormData) {
    requestOptions.body = options.body;
  } else if (options.body !== undefined && typeof options.body !== "string") {
    requestOptions.body = JSON.stringify(options.body);
    if (!headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
  } else if (options.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (token) headers.set("Authorization", `Bearer ${token}`);

  return fetch(
    `${baseUrl.replace(/\/$/, "")}/${path.replace(/^\//, "")}`,
    requestOptions,
  );
}

async function readResponse(response) {
  const responseType = response.headers.get("content-type") ?? "";
  let payload;

  if (responseType.includes("application/json")) {
    try {
      payload = await response.json();
    } catch {
      if (response.ok) throw new Error("The server returned invalid JSON");
    }
  } else if (response.status !== 204) {
    if (responseType.includes("text/html")) {
      throw new ApiError({
        status: response.status,
        code: "UNEXPECTED_HTML_RESPONSE",
        message: "Expected JSON response from API but received HTML",
      });
    }
    payload = await response.text();
  }

  if (!response.ok) {
    throw new ApiError({
      status: Number.isInteger(payload?.status)
        ? payload.status
        : response.status,
      code: typeof payload?.code === "string" ? payload.code : "INTERNAL_ERROR",
      message:
        typeof payload?.message === "string"
          ? payload.message
          : "The request could not be completed",
      fieldErrors: Array.isArray(payload?.fieldErrors)
        ? payload.fieldErrors.filter(
            (error) =>
              typeof error?.field === "string" &&
              typeof error?.message === "string",
          )
        : [],
    });
  }

  return payload;
}

function redirectToLogin() {
  if (typeof window === "undefined" || window.location.pathname === "/login")
    return;
  const returnTo =
    window.location.pathname + window.location.search + window.location.hash;
  const loginUrl = `/login?returnTo=${encodeURIComponent(returnTo)}`;
  window.history.pushState(window.history.state, "", loginUrl);
  window.dispatchEvent(new window.PopStateEvent("popstate"));
}
