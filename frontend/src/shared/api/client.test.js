import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./ApiError";
import { apiRequest, getAuthToken, setAuthTokenProvider } from "./client";

afterEach(() => {
  vi.unstubAllGlobals();
  setAuthTokenProvider(async () => undefined);
});

describe("apiRequest", () => {
  it("normalizes the backend error contract to ApiError", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            timestamp: "2026-01-01T00:00:00Z",
            status: 400,
            error: "Bad Request",
            code: "VALIDATION_FAILED",
            message: "Request validation failed",
            path: "/api/v1/example",
            fieldErrors: [{ field: "name", message: "must not be blank" }],
          }),
          { status: 400, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );

    const error = await apiRequest("/example").catch((caught) => caught);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      name: "ApiError",
      status: 400,
      code: "VALIDATION_FAILED",
      message: "Request validation failed",
      fieldErrors: [{ field: "name", message: "must not be blank" }],
    });
  });

  it("provides an auth-header hook that returns no token by default", async () => {
    expect(await getAuthToken()).toBeUndefined();
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);
    await apiRequest("/status");
    expect(fetchMock.mock.calls[0][1].headers.get("Authorization")).toBeNull();
  });

  it("serializes object request bodies as JSON", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: 1 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await apiRequest("/items", {
      method: "POST",
      body: { title: "Foundations" },
    });

    expect(fetchMock.mock.calls[0][1].headers.get("Content-Type")).toBe(
      "application/json",
    );
    expect(fetchMock.mock.calls[0][1].body).toBe(
      JSON.stringify({ title: "Foundations" }),
    );
  });

  it("throws ApiError when response contains HTML instead of JSON", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response("<!doctype html><html><body>Error</body></html>", {
          status: 200,
          headers: { "Content-Type": "text/html; charset=utf-8" },
        }),
      ),
    );

    const error = await apiRequest("/example").catch((caught) => caught);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      code: "UNEXPECTED_HTML_RESPONSE",
      message: "Expected JSON response from API but received HTML",
    });
  });
});
