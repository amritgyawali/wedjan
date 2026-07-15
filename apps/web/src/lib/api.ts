import { createApiClient } from "@wedjan/shared";

/**
 * Web API client. The access token lives in memory only (XSS-hardened);
 * the rotating refresh token is an httpOnly cookie owned by the API.
 */
let accessToken: string | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export const api = createApiClient({
  baseUrl: process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080",
  clientType: "web",
  getAccessToken: () => accessToken,
});
