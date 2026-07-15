"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { apiErrorMessage } from "@wedjan/shared";
import { api } from "@/lib/api";
import { Icon } from "@/components/platform/icon";

const ROLE_OPTIONS = [
  {
    role: "CUSTOMER",
    icon: "celebration",
    title: "Plan an event",
    description: "Find and book trusted vendors for your celebration",
  },
  {
    role: "VENDOR",
    icon: "storefront",
    title: "I'm a vendor",
    description: "List priced packages and grow your event business",
  },
  {
    role: "FREELANCER",
    icon: "photo_camera",
    title: "I work events",
    description: "Pick up paid shifts from vendors and event hosts",
  },
] as const;

type SignupRole = (typeof ROLE_OPTIONS)[number]["role"];

export default function SignupPage() {
  const router = useRouter();
  const [role, setRole] = useState<SignupRole>("CUSTOMER");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const { response, error: body } = await api.POST("/api/v1/auth/signup", {
        body: { email, password, role },
      });
      if (!response.ok) {
        setError(apiErrorMessage(body, "Signup failed — try again"));
        return;
      }
      router.push(`/verify?email=${encodeURIComponent(email)}`);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <h1>Create your account</h1>
      <p>Join the marketplace where every price is real and every payment protected.</p>
      <form className="auth-form" onSubmit={onSubmit}>
        <div className="role-picker" role="radiogroup" aria-label="How will you use wedjan?">
          {ROLE_OPTIONS.map((option) => (
            <button
              key={option.role}
              type="button"
              role="radio"
              aria-checked={role === option.role}
              className="role-option"
              data-selected={role === option.role}
              onClick={() => setRole(option.role)}
            >
              <Icon name={option.icon} />
              <div>
                <strong>{option.title}</strong>
                <span>{option.description}</span>
              </div>
            </button>
          ))}
        </div>
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
            autoComplete="new-password"
            minLength={10}
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        {error && <div className="auth-error">{error}</div>}
        <button className="primary-button auth-submit" type="submit" disabled={submitting}>
          {submitting ? "Creating account…" : "Create account"}
        </button>
      </form>
      <p className="auth-alt">
        Already have an account? <Link className="text-link" href="/login">Log in</Link>
      </p>
    </>
  );
}
