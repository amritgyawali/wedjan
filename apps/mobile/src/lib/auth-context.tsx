import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { apiErrorMessage, type Me, type Role } from "@wedjan/shared";
import {
  api,
  clearRefreshToken,
  readRefreshToken,
  saveRefreshToken,
  setAccessToken,
} from "./api";

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

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [me, setMe] = useState<Me | null>(null);
  const [activeRole, setActiveRole] = useState<Role | null>(null);

  const applySession = useCallback(
    async (accessToken: string, refreshToken: string | undefined, account: Me) => {
      setAccessToken(accessToken);
      if (refreshToken) {
        await saveRefreshToken(refreshToken);
      }
      setMe(account);
      setActiveRole((current) =>
        current && account.roles.includes(current) ? current : (account.roles[0] ?? null),
      );
      setStatus("authenticated");
    },
    [],
  );

  const clearSession = useCallback(async () => {
    setAccessToken(null);
    await clearRefreshToken();
    setMe(null);
    setActiveRole(null);
    setStatus("anonymous");
  }, []);

  // Bootstrap from the stored refresh token.
  useEffect(() => {
    (async () => {
      const stored = await readRefreshToken();
      if (!stored) {
        setStatus("anonymous");
        return;
      }
      const { data, response } = await api.POST("/api/v1/auth/refresh", {
        body: { refreshToken: stored },
      });
      if (response.ok && data) {
        await applySession(data.accessToken, data.refreshToken, data.account);
      } else {
        await clearSession();
      }
    })();
  }, [applySession, clearSession]);

  const login = useCallback(
    async (email: string, password: string) => {
      const { data, error, response } = await api.POST("/api/v1/auth/login", {
        body: { email, password, deviceName: "wedjan mobile" },
      });
      if (!response.ok || !data) {
        throw new Error(apiErrorMessage(error, "Login failed"));
      }
      await applySession(data.accessToken, data.refreshToken, data.account);
    },
    [applySession],
  );

  const logout = useCallback(async () => {
    const stored = await readRefreshToken();
    await api.POST("/api/v1/auth/logout", {
      body: stored ? { refreshToken: stored } : {},
    });
    await clearSession();
  }, [clearSession]);

  const refreshMe = useCallback(async () => {
    const { data, response } = await api.GET("/api/v1/me");
    if (response.ok && data) {
      setMe(data);
    }
  }, []);

  const value = useMemo(
    () => ({ status, me, activeRole, setActiveRole, login, logout, refreshMe }),
    [status, me, activeRole, login, logout, refreshMe],
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
