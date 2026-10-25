import type { ApiErrorBody } from "../types";

const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");
export const TOKEN_KEY = "taskflow.token";

export class ApiError extends Error {
  status: number;
  fieldErrors: Record<string, string>;

  constructor(message: string, status: number, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

export function clearStoredToken(): void {
  window.sessionStorage.removeItem(TOKEN_KEY);
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  const token = window.sessionStorage.getItem(TOKEN_KEY);

  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, {
      ...options,
      headers,
      credentials: "same-origin",
    });
  } catch {
    throw new ApiError("Could not reach TaskFlow. Check your connection and try again.", 0);
  }

  if (response.status === 204) return undefined as T;

  const contentType = response.headers.get("content-type") ?? "";
  let body: unknown;
  if (contentType.includes("application/json")) {
    try {
      body = await response.json();
    } catch {
      throw new ApiError("The server returned an unreadable response.", response.status);
    }
  } else {
    const text = await response.text();
    body = text ? { message: text } : undefined;
  }

  if (!response.ok) {
    const error = (body && typeof body === "object" ? body : {}) as ApiErrorBody;
    const message = error.message || error.error || `Request failed (${response.status}).`;
    if (response.status === 401) {
      clearStoredToken();
      window.dispatchEvent(new Event("taskflow:unauthorized"));
    }
    throw new ApiError(message, response.status, error.errors ?? {});
  }

  return body as T;
}

export function apiUrl(path: string): string {
  return `${baseUrl}${path}`;
}
