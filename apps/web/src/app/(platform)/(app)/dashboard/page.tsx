"use client";

import { useAuth } from "@/components/platform/auth-context";
import { Icon } from "@/components/platform/icon";

const EMPTY_STATES = {
  CUSTOMER: {
    icon: "celebration",
    title: "No events yet",
    subtitle: "Your celebrations, all in one place",
    body: "Event planning arrives with the workspace phase. Soon you'll create your event here, build a guest list, and book every vendor with protected payments.",
  },
  VENDOR: {
    icon: "storefront",
    title: "Your storefront starts here",
    subtitle: "Run your event business on wedjan",
    body: "Vendor onboarding opens in the next phase: build your profile, publish transparently priced packages, and get verified to appear in search.",
  },
  FREELANCER: {
    icon: "photo_camera",
    title: "No shifts yet",
    subtitle: "Paid event work, escrow-protected",
    body: "The gig layer is on the roadmap: browse shifts posted by vendors, see exact take-home pay before applying, and get paid within 72 hours of the event.",
  },
  ADMIN: {
    icon: "shield_person",
    title: "Admin console pending",
    subtitle: "Operations tooling",
    body: "The full admin console ships in a later phase. Verification queues arrive with vendor onboarding.",
  },
} as const;

export default function DashboardPage() {
  const { me, activeRole } = useAuth();
  if (!me || !activeRole) return null;

  const content = EMPTY_STATES[activeRole];
  const greetingName = me.profile.displayName || me.account.email.split("@")[0];

  return (
    <>
      <h1 className="app-page-title">Welcome, {greetingName}</h1>
      <p className="app-page-subtitle">{content.subtitle}</p>
      <div className="empty-state">
        <Icon name={content.icon} />
        <h2>{content.title}</h2>
        <p>{content.body}</p>
      </div>
    </>
  );
}
