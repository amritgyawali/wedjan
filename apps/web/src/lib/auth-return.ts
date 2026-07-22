export const DEFAULT_AUTH_RETURN_PATH = "/dashboard";

const AUTH_RETURN_BASE = "https://return.wedjan.invalid";
const CONTROL_CHARACTER = /[\u0000-\u001f\u007f]/;

/**
 * Accept only same-site absolute paths for post-auth navigation. This keeps
 * `next` useful without turning the auth routes into open redirectors.
 */
export function safeAuthReturnPath(value: string | null | undefined): string {
  const candidate = value?.trim();
  if (
    !candidate ||
    !candidate.startsWith("/") ||
    candidate.startsWith("//") ||
    candidate.includes("\\") ||
    CONTROL_CHARACTER.test(candidate)
  ) {
    return DEFAULT_AUTH_RETURN_PATH;
  }

  try {
    const parsed = new URL(candidate, AUTH_RETURN_BASE);
    if (parsed.origin !== AUTH_RETURN_BASE) return DEFAULT_AUTH_RETURN_PATH;
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return DEFAULT_AUTH_RETURN_PATH;
  }
}

export function authRoute(
  pathname: string,
  returnPath: string,
  values: Record<string, string> = {},
): string {
  const query = new URLSearchParams(values);
  const safeReturnPath = safeAuthReturnPath(returnPath);
  if (safeReturnPath !== DEFAULT_AUTH_RETURN_PATH) {
    query.set("next", safeReturnPath);
  }
  const suffix = query.toString();
  return suffix ? `${pathname}?${suffix}` : pathname;
}
