import * as SecureStore from "expo-secure-store";
import { createApiClient } from "@wedjan/shared";

const REFRESH_TOKEN_KEY = "wedjan.refreshToken";

/** Access token in memory; refresh token in SecureStore (mobile pattern). */
let accessToken: string | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export async function saveRefreshToken(token: string): Promise<void> {
  await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, token);
}

export async function readRefreshToken(): Promise<string | null> {
  return SecureStore.getItemAsync(REFRESH_TOKEN_KEY);
}

export async function clearRefreshToken(): Promise<void> {
  await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
}

export const api = createApiClient({
  baseUrl: process.env.EXPO_PUBLIC_API_URL ?? "http://localhost:8080",
  clientType: "mobile",
  getAccessToken: () => accessToken,
});
