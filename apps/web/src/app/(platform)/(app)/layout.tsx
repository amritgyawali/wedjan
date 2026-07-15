"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import type { Role } from "@wedjan/shared";
import { useAuth } from "@/components/platform/auth-context";
import { Icon } from "@/components/platform/icon";

const NAV_BY_ROLE: Record<Role, { href: string; icon: string; label: string }[]> = {
  CUSTOMER: [
    { href: "/dashboard", icon: "celebration", label: "My Events" },
    { href: "/settings", icon: "settings", label: "Settings" },
  ],
  VENDOR: [
    { href: "/dashboard", icon: "storefront", label: "Dashboard" },
    { href: "/settings", icon: "settings", label: "Settings" },
  ],
  FREELANCER: [
    { href: "/dashboard", icon: "photo_camera", label: "Shifts" },
    { href: "/settings", icon: "settings", label: "Settings" },
  ],
  ADMIN: [
    { href: "/dashboard", icon: "shield_person", label: "Admin" },
    { href: "/settings", icon: "settings", label: "Settings" },
  ],
};

const ROLE_LABEL: Record<Role, string> = {
  CUSTOMER: "Planning events",
  VENDOR: "Vendor",
  FREELANCER: "Freelancer",
  ADMIN: "Admin",
};

export default function AppLayout({ children }: { children: ReactNode }) {
  const { status, me, activeRole, setActiveRole, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (status === "anonymous") {
      router.replace("/login");
    }
  }, [status, router]);

  if (status === "loading" || !me || !activeRole) {
    return (
      <div className="auth-shell">
        <p style={{ color: "var(--wj-color-text-muted)" }}>Loading your workspace…</p>
      </div>
    );
  }

  const nav = NAV_BY_ROLE[activeRole];

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <Link href="/" className="wordmark">
          wedjan
        </Link>
        <nav className="app-nav" aria-label="Main">
          {nav.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="app-nav-link"
              data-active={pathname === item.href}
            >
              <Icon name={item.icon} />
              {item.label}
            </Link>
          ))}
        </nav>
        <div className="role-switcher">
          {me.roles.length > 1 && (
            <>
              <label htmlFor="role-switcher-select">Viewing as</label>
              <select
                id="role-switcher-select"
                value={activeRole}
                onChange={(e) => setActiveRole(e.target.value as Role)}
              >
                {me.roles.map((role) => (
                  <option key={role} value={role}>
                    {ROLE_LABEL[role]}
                  </option>
                ))}
              </select>
            </>
          )}
          <button
            type="button"
            className="ghost-button"
            onClick={async () => {
              await logout();
              router.replace("/login");
            }}
          >
            Log out
          </button>
        </div>
      </aside>
      <main className="app-main">{children}</main>
    </div>
  );
}
