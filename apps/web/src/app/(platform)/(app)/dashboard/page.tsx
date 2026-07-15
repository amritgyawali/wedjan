"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { VendorMe } from "@wedjan/shared";
import { useAuth } from "@/components/platform/auth-context";
import { Icon } from "@/components/platform/icon";
import { api } from "@/lib/api";

const EMPTY_STATES = {
  CUSTOMER: { icon: "celebration", title: "No events yet", subtitle: "Your celebrations, all in one place", body: "Create events, build a guest list, and book every vendor with protected payments when the event workspace opens." },
  FREELANCER: { icon: "photo_camera", title: "No shifts yet", subtitle: "Paid event work, escrow-protected", body: "Browse shifts with exact take-home pay when the gig layer opens." },
} as const;

export default function DashboardPage() {
  const { me, activeRole } = useAuth();
  const [vendor, setVendor] = useState<VendorMe | null>(null);
  useEffect(() => { if (activeRole === "VENDOR") void api.GET("/api/v1/vendors/me").then((r) => r.data && setVendor(r.data)); }, [activeRole]);
  if (!me || !activeRole) return null;
  const greetingName = me.profile.displayName || me.account.email.split("@")[0];
  if (activeRole === "VENDOR") return <VendorDashboard name={greetingName} vendor={vendor} />;
  if (activeRole === "ADMIN") return <><h1 className="app-page-title">Operations</h1><p className="app-page-subtitle">Trust starts with a careful review.</p><div className="dashboard-grid"><div className="dashboard-card"><h2>Vendor verification</h2><p>Review identity and business documents. Both approvals publish a submitted listing automatically.</p><div className="dashboard-actions"><Link className="primary-button" href="/admin/verifications">Open verification queue</Link></div></div></div></>;
  const content = EMPTY_STATES[activeRole];
  return <><h1 className="app-page-title">Welcome, {greetingName}</h1><p className="app-page-subtitle">{content.subtitle}</p><div className="empty-state"><Icon name={content.icon} /><h2>{content.title}</h2><p>{content.body}</p></div></>;
}

function VendorDashboard({ name, vendor }: { name: string; vendor: VendorMe | null }) {
  if (!vendor) return <p className="app-page-subtitle">Loading your storefront…</p>;
  const approved = vendor.documents.filter((d) => d.status === "APPROVED").length;
  return <><h1 className="app-page-title">Welcome, {name}</h1><p className="app-page-subtitle">Your storefront, verification, and listing quality at a glance.</p><div className="dashboard-grid"><section className="dashboard-card"><div className="status-line"><div><p className="eyebrow">Profile status</p><h2>{statusTitle(vendor.profile.status)}</h2></div><span className={`status-chip ${vendor.profile.status.toLowerCase()}`}>{vendor.profile.status}</span></div><p>{statusBody(vendor.profile.status)}</p><div className="dashboard-actions"><Link className="primary-button" href="/vendor/onboarding">{vendor.listingStrength === 100 ? "Edit listing" : "Continue onboarding"}</Link>{vendor.profile.slug && <Link className="ghost-button" href={`/v/${vendor.profile.slug}`}>Preview as customer</Link>}</div></section><section className="dashboard-card"><p className="eyebrow">Listing strength</p><h2>{vendor.listingStrength}% complete</h2><div className="strength-bar"><span style={{ width: `${vendor.listingStrength}%` }} /></div><p>{vendor.gates.filter((g) => g.passed).length} of {vendor.gates.length} requirements complete</p></section><section className="dashboard-card"><h2>Verification checklist</h2>{vendor.documents.length ? vendor.documents.map((d) => <div className="record-row" key={d.id}><strong>{d.type.replaceAll("_", " ")}</strong><span className={`status-chip ${d.status.toLowerCase()}`}>{d.status}</span></div>) : <p>Upload government ID and business registration to start review.</p>}<p>{approved} document{approved === 1 ? "" : "s"} approved</p></section><section className="dashboard-card"><h2>Supply snapshot</h2><div className="record-row"><strong>Published packages</strong><span>{vendor.packages.filter((p) => p.status === "PUBLISHED").length}</span></div><div className="record-row"><strong>Portfolio photos</strong><span>{vendor.media.filter((m) => m.kind === "GALLERY").length}</span></div><div className="record-row"><strong>Service areas</strong><span>{vendor.serviceAreas.length}</span></div></section></div></>;
}
function statusTitle(status: string) { return ({ DRAFT: "Build your listing", SUBMITTED: "Under review", UNDER_REVIEW: "Updates under review", VERIFIED: "Your listing is live", REJECTED: "Action required", SUSPENDED: "Listing suspended" } as Record<string,string>)[status] ?? status; }
function statusBody(status: string) { return status === "VERIFIED" ? "Customers can see your profile and every published price." : status === "SUBMITTED" ? "Your documents are in the admin queue. We’ll publish after both required approvals." : status === "UNDER_REVIEW" ? "Your current listing remains live while sensitive changes are reviewed." : "Complete all seven steps to submit for verification."; }
