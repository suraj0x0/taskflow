import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider, useAuth } from "./AuthContext";
import { TOKEN_KEY } from "../api/client";

function AuthProbe() {
  const { user, loading, restoreError, login } = useAuth();
  return (
    <div>
      <span>{loading ? "loading" : user?.name ?? "signed out"}</span>
      {restoreError && <span role="alert">{restoreError}</span>}
      <button onClick={() => void login("worker@example.com", "password123")}>Sign in</button>
    </div>
  );
}

describe("AuthProvider", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("stores a login token for the current browser session", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({
      id: "user-1",
      name: "Taylor",
      email: "worker@example.com",
      role: "WORKER",
      token: "signed-jwt",
    }), { status: 200, headers: { "Content-Type": "application/json" } }));

    render(<AuthProvider><AuthProbe /></AuthProvider>);
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await screen.findByText("Taylor");
    expect(window.sessionStorage.getItem(TOKEN_KEY)).toBe("signed-jwt");
  });

  it("keeps the token when session restoration fails due to a network problem", async () => {
    window.sessionStorage.setItem(TOKEN_KEY, "valid-token");
    vi.mocked(fetch).mockRejectedValue(new TypeError("offline"));

    render(<AuthProvider><AuthProbe /></AuthProvider>);

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Could not restore your session"));
    expect(window.sessionStorage.getItem(TOKEN_KEY)).toBe("valid-token");
    expect(screen.getByText("signed out")).toBeInTheDocument();
  });
});
