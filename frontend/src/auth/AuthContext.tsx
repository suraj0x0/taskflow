import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from "react";
import { ApiError } from "../api/client";
import { authApi } from "../api/services";
import { clearStoredToken, TOKEN_KEY } from "../api/client";
import type { LoginResponse, User } from "../types";

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  restoreError: string;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<User>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function tokenExpiry(token: string): number | null {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const claims = JSON.parse(window.atob(normalized)) as { exp?: unknown };
    return typeof claims.exp === "number" ? claims.exp * 1000 : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [restoreError, setRestoreError] = useState("");

  const logout = useCallback(() => {
    clearStoredToken();
    setUser(null);
    setLoading(false);
    setRestoreError("");
  }, []);

  const refreshUser = useCallback(async () => {
    const current = await authApi.me();
    setUser(current);
    return current;
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const response: LoginResponse = await authApi.login(email, password);
    window.sessionStorage.setItem(TOKEN_KEY, response.token);
    setUser({
      id: response.id,
      name: response.name,
      email: response.email,
      role: response.role,
    });
    setRestoreError("");
    setLoading(false);
  }, []);

  const register = useCallback(async (name: string, email: string, password: string) => {
    await authApi.register(name, email, password);
  }, []);

  useEffect(() => {
    let active = true;
    const token = window.sessionStorage.getItem(TOKEN_KEY);
    if (!token) {
      setLoading(false);
      return () => {
        active = false;
      };
    }

    void authApi.me()
      .then((current) => {
        if (active) setUser(current);
      })
      .catch((cause: unknown) => {
        if (active) {
          setUser(null);
          if (cause instanceof ApiError && cause.status === 401) {
            clearStoredToken();
            setRestoreError("Your session has expired. Please sign in again.");
          } else {
            setRestoreError("Could not restore your session. Check your connection and try again.");
          }
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const handleUnauthorized = () => {
      setUser(null);
      setLoading(false);
      setRestoreError("Your session has expired. Please sign in again.");
    };
    window.addEventListener("taskflow:unauthorized", handleUnauthorized);
    return () => window.removeEventListener("taskflow:unauthorized", handleUnauthorized);
  }, []);

  useEffect(() => {
    if (!user) return;
    const token = window.sessionStorage.getItem(TOKEN_KEY);
    const expiry = token ? tokenExpiry(token) : null;
    if (expiry === null) return;
    const delay = expiry - Date.now();
    if (delay <= 0) {
      logout();
      return;
    }
    const timeoutId = window.setTimeout(logout, Math.min(delay, 2_147_000_000));
    return () => window.clearTimeout(timeoutId);
  }, [logout, user]);

  const value = useMemo(
    () => ({ user, loading, restoreError, login, register, logout, refreshUser }),
    [user, loading, restoreError, login, register, logout, refreshUser],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
}
