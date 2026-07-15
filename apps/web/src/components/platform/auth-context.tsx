"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { apiErrorMessage, type Me, type Role } from "@wedjan/shared";
import { api, setAccessToken } from "@/lib/api";

const ACTIVE_ROLE_KEY = "wedjan.activeRole";

export type AuthStatus = "loading" | "authenticated" | "anonymous";

interface AuthContextValue {
  status: AuthStatus;
  me: Me | null;
  activeRole: Role | null;
  setActiveRole: (role: Role) => void;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshMe: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function pickActiveRole(me: Me, stored: string | null): Role | null {
  const roles = me.roles;
  if (roles.length === 0) return null;
  if (stored && roles.includes(stored as Role)) return stored as Role;
  return roles[0];
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [me, setMe] = useState<Me | null>(null);
  const [activeRole, setActiveRoleState] = useState<Role | null>(null);
  const refreshTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Breaks the applySession → scheduleRefresh → refreshSession → applySession
  // cycle without stale closures.
  const refreshSessionRef = useRef<(() => Promise<boolean>) | null>(null);

  const scheduleRefresh = useCallback((expiresInSeconds: number) => {
    if (refreshTimer.current) clearTimeout(refreshTimer.current);
    const delayMs = Math.max(30, expiresInSeconds - 60) * 1000;
    refreshTimer.current = setTimeout(() => {
      void refreshSessionRef.current?.();
    }, delayMs);
  }, []);

  const applySession = useCallback(
    (accessToken: string, expiresInSeconds: number, account: Me) => {
      setAccessToken(accessToken);
      setMe(account);
      setActiveRoleState(pickActiveRole(account, localStorage.getItem(ACTIVE_ROLE_KEY)));
      setStatus("authenticated");
      scheduleRefresh(expiresInSeconds);
    },
    [scheduleRefresh],
  );

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setMe(null);
    setActiveRoleState(null);
    setStatus("anonymous");
    if (refreshTimer.current) clearTimeout(refreshTimer.current);
  }, []);

  const refreshSession = useCallback(async () => {
    const { data, response } = await api.POST("/api/v1/auth/refresh", {});
    if (data && response.ok) {
      applySession(data.accessToken, data.expiresInSeconds, data.account);
      return true;
    }
    clearSession();
    return false;
  }, [applySession, clearSession]);

  useEffect(() => {
    refreshSessionRef.current = refreshSession;
  }, [refreshSession]);

  // Bootstrap: try the refresh cookie once on mount.
  useEffect(() => {
    void refreshSessionRef.current?.();
    return () => {
      if (refreshTimer.current) clearTimeout(refreshTimer.current);
    };
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const { data, error, response } = await api.POST("/api/v1/auth/login", {
        body: { email, password, deviceName: navigator.userAgent.slice(0, 100) },
      });
      if (!response.ok || !data) {
        throw new Error(apiErrorMessage(error, "Login failed"));
      }
      applySession(data.accessToken, data.expiresInSeconds, data.account);
    },
    [applySession],
  );

  const logout = useCallback(async () => {
    await api.POST("/api/v1/auth/logout", {});
    clearSession();
  }, [clearSession]);

  const refreshMe = useCallback(async () => {
    const { data, response } = await api.GET("/api/v1/me");
    if (response.ok && data) {
      setMe(data);
      setActiveRoleState(pickActiveRole(data, localStorage.getItem(ACTIVE_ROLE_KEY)));
    }
  }, []);

  const setActiveRole = useCallback((role: Role) => {
    localStorage.setItem(ACTIVE_ROLE_KEY, role);
    setActiveRoleState(role);
  }, []);

  const value = useMemo(
    () => ({ status, me, activeRole, setActiveRole, login, logout, refreshMe }),
    [status, me, activeRole, setActiveRole, login, logout, refreshMe],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside <AuthProvider>");
  }
  return context;
}
