import createClient, { type ClientOptions, type Middleware } from "openapi-fetch";
import type { components, paths } from "./generated/api";

export type { components, paths };

/** Domain aliases over the generated OpenAPI types. */
export type Role = components["schemas"]["Role"];
export type Currency = components["schemas"]["Currency"];
export type Account = components["schemas"]["Account"];
export type Profile = components["schemas"]["Profile"];
export type Me = components["schemas"]["MeResponse"];
export type AuthTokens = components["schemas"]["AuthTokensResponse"];
export type Session = components["schemas"]["Session"];
export type MediaAsset = components["schemas"]["MediaAsset"];
export type MediaKind = components["schemas"]["MediaKind"];
export type ErrorEnvelope = components["schemas"]["ErrorEnvelope"];

export const ROLES = ["CUSTOMER", "VENDOR", "FREELANCER", "ADMIN"] as const;
export const SIGNUP_ROLES = ["CUSTOMER", "VENDOR", "FREELANCER"] as const;
export const CURRENCIES = ["AUD", "USD", "GBP", "NPR"] as const;

export type ApiClient = ReturnType<typeof createApiClient>;

export interface CreateApiClientOptions extends ClientOptions {
  baseUrl: string;
  /** "mobile" makes token endpoints return refresh tokens in the body. */
  clientType?: "web" | "mobile";
  /** Called before each request; return the current access token if any. */
  getAccessToken?: () => string | null | Promise<string | null>;
}

/**
 * Typed fetch client generated from openapi.yaml — the single API client
 * used by web and mobile. Attaches Authorization and X-Wedjan-Client
 * headers automatically.
 */
export function createApiClient(options: CreateApiClientOptions) {
  const { clientType = "web", getAccessToken, ...clientOptions } = options;

  const client = createClient<paths>({
    credentials: clientType === "web" ? "include" : "omit",
    ...clientOptions,
  });

  const authMiddleware: Middleware = {
    async onRequest({ request }) {
      request.headers.set("X-Wedjan-Client", clientType);
      const token = getAccessToken ? await getAccessToken() : null;
      if (token) {
        request.headers.set("Authorization", `Bearer ${token}`);
      }
      return request;
    },
  };

  client.use(authMiddleware);
  return client;
}

/** Type guard for the standard error envelope. */
export function isErrorEnvelope(value: unknown): value is ErrorEnvelope {
  return (
    typeof value === "object" &&
    value !== null &&
    "error" in value &&
    typeof (value as { error?: unknown }).error === "object"
  );
}

/** Extracts a human-readable message from an API error body. */
export function apiErrorMessage(value: unknown, fallback = "Something went wrong"): string {
  if (isErrorEnvelope(value)) {
    const fieldError = value.error.fieldErrors?.[0];
    if (fieldError) return `${fieldError.field}: ${fieldError.message}`;
    return value.error.message || fallback;
  }
  return fallback;
}
