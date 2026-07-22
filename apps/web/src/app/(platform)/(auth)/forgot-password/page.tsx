"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { api } from "@/lib/api";
import { authRoute, safeAuthReturnPath } from "@/lib/auth-return";

function ForgotPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnPath = safeAuthReturnPath(searchParams.get("next"));
  const [email, setEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      await api.POST("/api/v1/auth/password-reset/request", { body: { email } });
      // Always continue — the API never reveals whether the email exists.
      router.push(authRoute("/reset-password", returnPath, { email }));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <h1>Reset your password</h1>
      <p>Enter your email and we&apos;ll send a 6-digit reset code if an account exists.</p>
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
        <button className="primary-button auth-submit" type="submit" disabled={submitting}>
          {submitting ? "Sending…" : "Send reset code"}
        </button>
      </form>
      <p className="auth-alt">
        Remembered it?{" "}
        <Link className="text-link" href={authRoute("/login", returnPath)}>
          Log in
        </Link>
      </p>
    </>
  );
}

export default function ForgotPasswordPage() {
  return (
    <Suspense fallback={<p>Preparing password reset…</p>}>
      <ForgotPasswordForm />
    </Suspense>
  );
}
