"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { apiErrorMessage } from "@wedjan/shared";
import { api } from "@/lib/api";
import { authRoute, safeAuthReturnPath } from "@/lib/auth-return";

function VerifyForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnPath = safeAuthReturnPath(searchParams.get("next"));
  const [email, setEmail] = useState(searchParams.get("email") ?? "");
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const { response, error: body } = await api.POST("/api/v1/auth/verify-email", {
        body: { email, code },
      });
      if (!response.ok) {
        setError(apiErrorMessage(body, "Verification failed — check the code"));
        return;
      }
      router.push(authRoute("/login", returnPath));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <h1>Check your email</h1>
      <p>We sent a 6-digit code to {email || "your inbox"}. Enter it below to activate your account.</p>
      <form className="auth-form" onSubmit={onSubmit}>
        {!searchParams.get("email") && (
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
        )}
        <label className="form-label">
          Verification code
          <input
            className="form-input otp-input"
            inputMode="numeric"
            pattern="[0-9]{6}"
            maxLength={6}
            placeholder="••••••"
            required
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
          />
        </label>
        {error && <div className="auth-error">{error}</div>}
        <button className="primary-button auth-submit" type="submit" disabled={submitting}>
          {submitting ? "Verifying…" : "Verify email"}
        </button>
      </form>
      <p className="auth-alt">
        Wrong account?{" "}
        <Link className="text-link" href={authRoute("/signup", returnPath)}>
          Start over
        </Link>
      </p>
    </>
  );
}

export default function VerifyPage() {
  return (
    <Suspense>
      <VerifyForm />
    </Suspense>
  );
}
