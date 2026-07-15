"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { apiErrorMessage } from "@wedjan/shared";
import { api } from "@/lib/api";

function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [email, setEmail] = useState(searchParams.get("email") ?? "");
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const { response, error: body } = await api.POST("/api/v1/auth/password-reset/confirm", {
        body: { email, code, newPassword },
      });
      if (!response.ok) {
        setError(apiErrorMessage(body, "Reset failed — check the code"));
        return;
      }
      router.push("/login");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <h1>Choose a new password</h1>
      <p>Enter the 6-digit code from your email and a new password.</p>
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
          Reset code
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
        <label className="form-label">
          New password
          <input
            className="form-input"
            type="password"
            autoComplete="new-password"
            minLength={10}
            required
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />
        </label>
        {error && <div className="auth-error">{error}</div>}
        <button className="primary-button auth-submit" type="submit" disabled={submitting}>
          {submitting ? "Updating…" : "Update password"}
        </button>
      </form>
    </>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense>
      <ResetPasswordForm />
    </Suspense>
  );
}
