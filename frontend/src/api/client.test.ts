import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest, TOKEN_KEY, ApiError } from "./client";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("apiRequest", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("sends the session token and JSON request body", async () => {
    window.sessionStorage.setItem(TOKEN_KEY, "jwt-token");
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ id: "p-1" }));

    await expect(apiRequest<{ id: string }>("/api/projects", {
      method: "POST",
      body: JSON.stringify({ name: "Planning" }),
    })).resolves.toEqual({ id: "p-1" });

    const [, options] = vi.mocked(fetch).mock.calls[0];
    expect(new Headers(options?.headers).get("Authorization")).toBe("Bearer jwt-token");
    expect(new Headers(options?.headers).get("Content-Type")).toBe("application/json");
    expect(options?.credentials).toBe("same-origin");
  });

  it("handles no-content responses", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));
    await expect(apiRequest<void>("/api/notifications/read-all", { method: "PATCH" })).resolves.toBeUndefined();
  });

  it("preserves backend validation errors", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({
      message: "Request validation failed",
      errors: { email: "Email must be valid" },
    }, 400));

    await expect(apiRequest("/api/users")).rejects.toMatchObject({
      name: "ApiError",
      status: 400,
      message: "Request validation failed",
      fieldErrors: { email: "Email must be valid" },
    });
  });

  it("clears an unauthorized token and notifies auth state", async () => {
    window.sessionStorage.setItem(TOKEN_KEY, "expired");
    const dispatch = vi.spyOn(window, "dispatchEvent");
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ message: "Unauthorized" }, 401));

    await expect(apiRequest("/api/auth/me")).rejects.toBeInstanceOf(ApiError);
    expect(window.sessionStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({ type: "taskflow:unauthorized" }));
  });

  it("surfaces network failures as API errors", async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError("offline"));
    await expect(apiRequest("/api/projects")).rejects.toMatchObject({
      name: "ApiError",
      status: 0,
      message: "Could not reach TaskFlow. Check your connection and try again.",
    });
  });
});
