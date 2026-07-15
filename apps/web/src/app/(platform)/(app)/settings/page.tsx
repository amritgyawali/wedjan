"use client";

import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { CURRENCIES, SIGNUP_ROLES, apiErrorMessage, type Role } from "@wedjan/shared";
import { useAuth } from "@/components/platform/auth-context";
import { api } from "@/lib/api";

export default function SettingsPage() {
  const { me, refreshMe, logout } = useAuth();
  const queryClient = useQueryClient();

  const [displayName, setDisplayName] = useState(me?.profile.displayName ?? "");
  const [city, setCity] = useState(me?.profile.city ?? "");
  const [country, setCountry] = useState(me?.profile.country ?? "");
  const [timezone, setTimezone] = useState(me?.profile.timezone ?? "");
  const [currency, setCurrency] = useState(me?.account.defaultCurrency ?? "AUD");
  const [marketingOptIn, setMarketingOptIn] = useState(me?.account.marketingOptIn ?? false);
  const [profileMessage, setProfileMessage] = useState<string | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);

  const sessions = useQuery({
    queryKey: ["sessions"],
    queryFn: async () => {
      const { data, response } = await api.GET("/api/v1/me/sessions");
      if (!response.ok || !data) throw new Error("Failed to load sessions");
      return data.items;
    },
  });

  const revokeSession = useMutation({
    mutationFn: async (sessionId: string) => {
      await api.DELETE("/api/v1/me/sessions/{sessionId}", {
        params: { path: { sessionId } },
      });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["sessions"] }),
  });

  const addRole = useMutation({
    mutationFn: async (role: Role) => {
      const { response, error } = await api.POST("/api/v1/auth/roles/add", {
        body: { role: role as "CUSTOMER" | "VENDOR" | "FREELANCER" },
      });
      if (!response.ok) throw new Error(apiErrorMessage(error, "Could not add role"));
    },
    onSuccess: () => refreshMe(),
  });

  if (!me) return null;

  async function saveProfile(event: FormEvent) {
    event.preventDefault();
    setProfileMessage(null);
    setProfileError(null);
    const { response, error } = await api.PATCH("/api/v1/me", {
      body: {
        displayName: displayName || undefined,
        city: city || undefined,
        country: country || undefined,
        timezone: timezone || undefined,
        defaultCurrency: currency as (typeof CURRENCIES)[number],
        marketingOptIn,
      },
    });
    if (!response.ok) {
      setProfileError(apiErrorMessage(error, "Could not save profile"));
      return;
    }
    await refreshMe();
    setProfileMessage("Profile saved");
  }

  const missingRoles = SIGNUP_ROLES.filter((role) => !me.roles.includes(role));

  return (
    <>
      <h1 className="app-page-title">Settings</h1>
      <p className="app-page-subtitle">Your account, profile, roles and active sessions.</p>

      <div className="settings-grid">
        <section className="settings-card">
          <h2>Profile</h2>
          <p>How you appear across wedjan.</p>
          <form className="settings-form" onSubmit={saveProfile}>
            <div className="form-row">
              <label className="form-label">
                Display name
                <input
                  className="form-input"
                  value={displayName}
                  maxLength={120}
                  onChange={(e) => setDisplayName(e.target.value)}
                />
              </label>
              <label className="form-label">
                City
                <input
                  className="form-input"
                  value={city}
                  maxLength={120}
                  onChange={(e) => setCity(e.target.value)}
                />
              </label>
            </div>
            <div className="form-row">
              <label className="form-label">
                Country (2-letter code)
                <input
                  className="form-input"
                  value={country}
                  maxLength={2}
                  placeholder="AU / NP"
                  onChange={(e) => setCountry(e.target.value.toUpperCase())}
                />
              </label>
              <label className="form-label">
                Timezone
                <input
                  className="form-input"
                  value={timezone}
                  placeholder="Australia/Melbourne"
                  onChange={(e) => setTimezone(e.target.value)}
                />
              </label>
            </div>
            <div className="form-row">
              <label className="form-label">
                Currency
                <select
                  className="form-input"
                  value={currency}
                  onChange={(e) => setCurrency(e.target.value as (typeof CURRENCIES)[number])}
                >
                  {CURRENCIES.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </label>
              <label className="form-label" style={{ justifyContent: "flex-end" }}>
                <span style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <input
                    type="checkbox"
                    checked={marketingOptIn}
                    onChange={(e) => setMarketingOptIn(e.target.checked)}
                  />
                  Send me planning tips &amp; updates
                </span>
              </label>
            </div>
            {profileError && <div className="auth-error">{profileError}</div>}
            {profileMessage && <div className="auth-success">{profileMessage}</div>}
            <button className="primary-button" type="submit" style={{ justifySelf: "start" }}>
              Save profile
            </button>
          </form>
        </section>

        <section className="settings-card">
          <h2>Account</h2>
          <p>Signed in as {me.account.email}</p>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 12 }}>
            {missingRoles.map((role) => (
              <button
                key={role}
                type="button"
                className="ghost-button"
                disabled={addRole.isPending}
                onClick={() => addRole.mutate(role)}
              >
                {role === "CUSTOMER" && "+ Plan events"}
                {role === "VENDOR" && "+ Become a vendor"}
                {role === "FREELANCER" && "+ Work events"}
              </button>
            ))}
            <Link className="ghost-button" href="/forgot-password">
              Change password
            </Link>
          </div>
        </section>

        <section className="settings-card">
          <h2>Active sessions</h2>
          <p>Devices with access to your account. Revoke anything you don&apos;t recognise.</p>
          {sessions.isLoading && <p>Loading sessions…</p>}
          {sessions.data?.map((session) => (
            <div key={session.id} className="session-row">
              <div className="session-meta">
                <strong>{session.deviceName || "Unknown device"}</strong>
                <span>
                  {session.ip ?? "unknown ip"} · started{" "}
                  {new Date(session.createdAt).toLocaleString()}
                </span>
              </div>
              {session.current ? (
                <span className="session-current-chip">This device</span>
              ) : (
                <button
                  type="button"
                  className="ghost-button"
                  disabled={revokeSession.isPending}
                  onClick={() => revokeSession.mutate(session.id)}
                >
                  Revoke
                </button>
              )}
            </div>
          ))}
          <div style={{ marginTop: 20 }}>
            <button
              type="button"
              className="danger-button"
              onClick={async () => {
                await api.POST("/api/v1/auth/logout-all", {});
                await logout();
                window.location.href = "/login";
              }}
            >
              Log out of all devices
            </button>
          </div>
        </section>
      </div>
    </>
  );
}
