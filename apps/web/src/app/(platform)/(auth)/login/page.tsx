"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { useAuth } from "@/components/platform/auth-context";
import { authRoute, safeAuthReturnPath } from "@/lib/auth-return";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { login } = useAuth();
  const returnPath = safeAuthReturnPath(searchParams.get("next"));
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      router.replace(returnPath);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Login failed");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <h1>Welcome back</h1>
      <p>Log in to keep planning, selling, or working events.</p>
      <form className="auth-form" onSubmit={onSubmit}>
        <label className="form-label">
          Email
          <input
            className="form-input"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>
        <label className="form-label">
          Password
          <input
            className="form-input"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        {error && <div className="auth-error">{error}</div>}
        <button className="primary-button auth-submit" type="submit" disabled={submitting}>
          {submitting ? "Logging in…" : "Log in"}
        </button>
      </form>
      <p className="auth-alt">
        <Link className="text-link" href={authRoute("/forgot-password", returnPath)}>
          Forgot password?
        </Link>
      </p>
      <p className="auth-alt">
        New to wedjan?{" "}
        <Link className="text-link" href={authRoute("/signup", returnPath)}>
          Create an account
        </Link>
      </p>
    </>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<p>Preparing login…</p>}>
      <LoginForm />
    </Suspense>
  );
}
