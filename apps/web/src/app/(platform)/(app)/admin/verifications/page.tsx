"use client";

import { useCallback, useEffect, useState } from "react";
import { apiErrorMessage, type components } from "@wedjan/shared";
import { api } from "@/lib/api";

type Queue = components["schemas"]["VerificationQueueItem"][];

export default function VerificationsPage() {
  const [items, setItems] = useState<Queue>([]); const [status, setStatus] = useState<"PENDING" | "APPROVED" | "REJECTED">("PENDING"); const [error, setError] = useState("");
  const load = useCallback(async () => { const r = await api.GET("/api/v1/admin/verifications", { params: { query: { status, limit: 50 } } }); if (!r.data) throw new Error(apiErrorMessage(r.error)); setItems(r.data.items); }, [status]);
  useEffect(() => {
    const timer = window.setTimeout(() => {
      void load().catch((e: unknown) => setError(e instanceof Error ? e.message : "Could not load queue"));
    }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);
  const review = async (id: string, approve: boolean) => { const note = approve ? "Approved after manual review" : window.prompt("Why is this unreadable or invalid?"); if (!approve && !note) return; const r = approve ? await api.POST("/api/v1/admin/verifications/{documentId}/approve", { params: { path: { documentId: id } }, body: { note: note ?? undefined } }) : await api.POST("/api/v1/admin/verifications/{documentId}/reject", { params: { path: { documentId: id } }, body: { note: note ?? undefined } }); if (!r.data) setError(apiErrorMessage(r.error)); else await load(); };
  return <><h1 className="app-page-title">Verification queue</h1><p className="app-page-subtitle">Approve only legible, matching documents. Required approvals publish submitted vendors.</p><div className="dashboard-actions"><select className="form-input" value={status} onChange={(e) => setStatus(e.target.value as typeof status)}><option>PENDING</option><option>APPROVED</option><option>REJECTED</option></select></div>{error && <div className="auth-error">{error}</div>}<div className="admin-queue">{items.map((item) => <article className="admin-doc" key={item.document.id}><div><h3>{item.businessName}</h3><p>{item.document.type.replaceAll("_", " ")} · Vendor {item.vendorStatus}</p>{item.mediaUrl && <a className="text-button" href={item.mediaUrl} target="_blank">Open document ↗</a>}{item.document.reviewerNote && <p>{item.document.reviewerNote}</p>}</div>{status === "PENDING" && <div className="admin-actions"><button className="ghost-button" onClick={() => void review(item.document.id, false)}>Reject</button><button className="primary-button" onClick={() => void review(item.document.id, true)}>Approve</button></div>}</article>)}{!items.length && <div className="empty-state"><h2>Queue clear</h2><p>No {status.toLowerCase()} documents.</p></div>}</div></>;
}
