"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import type { Booking, BookingDeclineRequest } from "@wedjan/shared";
import { apiErrorMessage } from "@wedjan/shared";
import { api } from "@/lib/api";
import {
  addDays,
  formatDateOnly,
  formatMoney,
  localDateValue,
  parseMajorUnits,
  titleCase,
} from "@/lib/booking-format";
import { bookingStyles as styles } from "./booking-shared";

type ActionPanel = "accept" | "decline" | "cancel" | "complete" | "dispute" | "reschedule" | null;

export function BookingActions({
  booking,
  perspective,
  onChanged,
}: {
  booking: Booking;
  perspective: "customer" | "vendor";
  onChanged: (booking: Booking) => void;
}) {
  const [panel, setPanel] = useState<ActionPanel>(null);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [childSubmitting, setChildSubmitting] = useState(false);
  const allowed = new Set(booking.allowedActions);
  const busy = submitting || childSubmitting;

  function changed(updated: Booking) {
    setPanel(null);
    setError("");
    setChildSubmitting(false);
    onChanged(updated);
  }

  async function versionAction(path: "/api/v1/bookings/{id}/confirm" | "/api/v1/bookings/{id}/complete") {
    setSubmitting(true);
    setError("");
    try {
      const { data, error: body } = await api.POST(path, {
        params: { path: { id: booking.id } },
        body: { version: booking.version },
      });
      if (!data) setError(apiErrorMessage(body, "The booking changed. Refresh and try again."));
      else { onChanged(data); setPanel(null); }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className={styles.panel} aria-labelledby="booking-actions-title">
      <p className={styles.eyebrow}>Available actions</p>
      <h2 id="booking-actions-title">What happens next</h2>
      <p className={styles.panelIntro}>Actions come directly from the booking state machine and re-check the current version.</p>
      <div className={styles.actions}>
        {allowed.has("CONFIRM_PAYMENT_STUB") && (
          <button className={styles.button} disabled={busy} type="button" onClick={() => void versionAction("/api/v1/bookings/{id}/confirm")}>
            Confirm with payment stub
          </button>
        )}
        {allowed.has("ACCEPT") && <ActionButton active={panel === "accept"} disabled={busy} onClick={() => setPanel("accept")}>Accept request</ActionButton>}
        {allowed.has("DECLINE") && <ActionButton active={panel === "decline"} disabled={busy} onClick={() => setPanel("decline")}>Decline</ActionButton>}
        {allowed.has("COMPLETE") && <ActionButton active={panel === "complete"} disabled={busy} onClick={() => setPanel("complete")}>Mark complete</ActionButton>}
        {allowed.has("PROPOSE_RESCHEDULE") && <ActionButton active={panel === "reschedule"} disabled={busy} onClick={() => setPanel("reschedule")}>Propose reschedule</ActionButton>}
        {allowed.has("RESPOND_RESCHEDULE") && <ActionButton active={panel === "reschedule"} disabled={busy} onClick={() => setPanel("reschedule")}>Review reschedule</ActionButton>}
        {allowed.has("DISPUTE") && <ActionButton active={panel === "dispute"} disabled={busy} onClick={() => setPanel("dispute")}>Report a problem</ActionButton>}
        {allowed.has("CANCEL") && (
          <button className={styles.dangerButton} disabled={busy} type="button" onClick={() => setPanel("cancel")}>Cancel booking</button>
        )}
      </div>

      {allowed.has("CONFIRM_PAYMENT_STUB") && (
        <div className={styles.notice}>Phase 4 uses a non-production confirmation stub. Stripe payment replaces this control in Phase 5.</div>
      )}
      {panel === "accept" && allowed.has("ACCEPT") && <AcceptPanel booking={booking} onBusyChange={setChildSubmitting} onChanged={changed} />}
      {panel === "decline" && allowed.has("DECLINE") && <DeclinePanel booking={booking} onBusyChange={setChildSubmitting} onChanged={changed} />}
      {panel === "complete" && allowed.has("COMPLETE") && (
        <div className={styles.actionPanel}>
          <h3>Confirm completion</h3>
          <p className={styles.panelIntro}>This starts the customer’s 48-hour dispute window.</p>
          <button className={styles.button} disabled={busy} type="button" onClick={() => void versionAction("/api/v1/bookings/{id}/complete")}>
            {submitting ? "Updating…" : "Mark booking complete"}
          </button>
        </div>
      )}
      {panel === "cancel" && allowed.has("CANCEL") && <CancelPanel booking={booking} onBusyChange={setChildSubmitting} onChanged={changed} />}
      {panel === "dispute" && allowed.has("DISPUTE") && <DisputePanel booking={booking} onBusyChange={setChildSubmitting} onChanged={changed} />}
      {panel === "reschedule" && (allowed.has("PROPOSE_RESCHEDULE") || allowed.has("RESPOND_RESCHEDULE")) && <ReschedulePanel booking={booking} perspective={perspective} onBusyChange={setChildSubmitting} onChanged={changed} />}
      {error && <div className={styles.notice} data-tone="error" role="alert">{error}</div>}
      {booking.allowedActions.length === 0 && <div className={styles.notice}>No action is required from you right now.</div>}
    </section>
  );
}

function AcceptPanel({ booking, onChanged, onBusyChange }: { booking: Booking; onChanged: (booking: Booking) => void; onBusyChange: (busy: boolean) => void }) {
  const [travelFee, setTravelFee] = useState(booking.price.travelFeeCents ? String(booking.price.travelFeeCents / 100) : "");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const cents = travelFee ? parseMajorUnits(travelFee) : undefined;
    if (cents === null) { setError("Enter a valid travel fee with at most two decimals."); return; }
    setSubmitting(true); onBusyChange(true); setError("");
    try {
      const { data, error: body } = await api.POST("/api/v1/bookings/{id}/accept", {
        params: { path: { id: booking.id } },
        body: { version: booking.version, travelFeeCents: cents },
      });
      if (!data) setError(apiErrorMessage(body, "The request could not be accepted"));
      else onChanged(data);
    } finally { setSubmitting(false); onBusyChange(false); }
  }
  return (
    <form className={`${styles.actionPanel} ${styles.stack}`} onSubmit={submit}>
      <h3>Accept request</h3>
      <p className={styles.panelIntro}>The base package price stays fixed. You may only adjust the travel fee.</p>
      <label className={styles.field}>Travel fee ({booking.price.currency})<input inputMode="decimal" value={travelFee} onChange={(event) => setTravelFee(event.target.value)} /></label>
      {error && <div className={styles.notice} data-tone="error" role="alert">{error}</div>}
      <button className={styles.button} disabled={submitting} type="submit">{submitting ? "Accepting…" : "Accept and hold date"}</button>
    </form>
  );
}

function DeclinePanel({ booking, onChanged, onBusyChange }: { booking: Booking; onChanged: (booking: Booking) => void; onBusyChange: (busy: boolean) => void }) {
  const [reason, setReason] = useState<BookingDeclineRequest["reason"]>("NOT_AVAILABLE");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSubmitting(true); onBusyChange(true); setError("");
    try {
      const { data, error: body } = await api.POST("/api/v1/bookings/{id}/decline", {
        params: { path: { id: booking.id } }, body: { reason, message: message.trim() || undefined, version: booking.version },
      });
      if (!data) setError(apiErrorMessage(body, "The request could not be declined"));
      else onChanged(data);
    } finally { setSubmitting(false); onBusyChange(false); }
  }
  const reasons: BookingDeclineRequest["reason"][] = ["NOT_AVAILABLE", "SCOPE_MISMATCH", "PRICE_MISMATCH", "SCHEDULING_CONFLICT", "OTHER"];
  return (
    <form className={`${styles.actionPanel} ${styles.stack}`} onSubmit={submit}>
      <h3>Decline request</h3>
      <label className={styles.field}>Reason<select value={reason} onChange={(event) => setReason(event.target.value as BookingDeclineRequest["reason"])}>{reasons.map((item) => <option key={item} value={item}>{titleCase(item)}</option>)}</select></label>
      <label className={styles.field}>Message<textarea maxLength={1000} value={message} onChange={(event) => setMessage(event.target.value)} /></label>
      {error && <div className={styles.notice} data-tone="error" role="alert">{error}</div>}
      <button className={styles.dangerButton} disabled={submitting} type="submit">{submitting ? "Declining…" : "Decline request"}</button>
    </form>
  );
}

function CancelPanel({ booking, onChanged, onBusyChange }: { booking: Booking; onChanged: (booking: Booking) => void; onBusyChange: (busy: boolean) => void }) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const refund = useQuery({
    queryKey: ["booking-refund-preview", booking.id, booking.version],
    queryFn: async () => {
      const { data, error: body } = await api.GET("/api/v1/bookings/{id}/refund-preview", { params: { path: { id: booking.id } } });
      if (!data) throw new Error(apiErrorMessage(body, "Refund preview could not be loaded"));
      return data;
    },
    retry: false,
  });
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!refund.data) return;
    setSubmitting(true); onBusyChange(true); setError("");
    try {
      const { data, error: body } = await api.POST("/api/v1/bookings/{id}/cancel", {
        params: { path: { id: booking.id } },
        body: {
          reason: reason.trim(),
          version: booking.version,
          refundCalculationId: refund.data.calculationId,
        },
      });
      if (!data) setError(apiErrorMessage(body, "The booking could not be cancelled"));
      else onChanged(data);
    } finally { setSubmitting(false); onBusyChange(false); }
  }
  return (
    <form className={`${styles.actionPanel} ${styles.stack}`} onSubmit={submit}>
      <h3>Review cancellation</h3>
      {refund.isLoading && <div className={styles.notice}>Calculating the exact refund…</div>}
      {refund.error && <div className={styles.notice} data-tone="error" role="alert">{refund.error.message}</div>}
      {refund.data && (
        <div className={styles.refundBox}>
          <span className={styles.eyebrow}>Refund preview</span>
          <strong className={styles.refundAmount}>{formatMoney(refund.data.refundableCents, booking.price.currency)}</strong>
          <span>{refund.data.refundPercent}% refundable · {refund.data.daysToEvent} days to event</span>
          <small>{formatMoney(refund.data.nonRefundableCents, booking.price.currency)} non-refundable under the {titleCase(refund.data.policy)} policy.</small>
          {refund.data.vendorPenalty && <div className={styles.notice} data-tone="error">Vendor cancellation gives the customer a full refund and records a reliability penalty.</div>}
        </div>
      )}
      <label className={styles.field}>Cancellation reason<textarea maxLength={1000} required value={reason} onChange={(event) => setReason(event.target.value)} /></label>
      {error && <div className={styles.notice} data-tone="error" role="alert">{error}</div>}
      <button className={styles.dangerButton} disabled={submitting || !refund.data || !reason.trim()} type="submit">{submitting ? "Cancelling…" : "Confirm cancellation"}</button>
    </form>
  );
}

function DisputePanel({ booking, onChanged, onBusyChange }: { booking: Booking; onChanged: (booking: Booking) => void; onBusyChange: (busy: boolean) => void }) {
  const [reason, setReason] = useState(""); const [error, setError] = useState(""); const [submitting, setSubmitting] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSubmitting(true); onBusyChange(true); setError("");
    try {
      const { data, error: body } = await api.POST("/api/v1/bookings/{id}/dispute", { params: { path: { id: booking.id } }, body: { reason: reason.trim(), version: booking.version } });
      if (!data) setError(apiErrorMessage(body, "The dispute could not be opened")); else onChanged(data);
    } finally { setSubmitting(false); onBusyChange(false); }
  }
  return (
    <form className={`${styles.actionPanel} ${styles.stack}`} onSubmit={submit}>
      <h3>Report a problem</h3><p className={styles.panelIntro}>This freezes release while the issue is reviewed.</p>
      <label className={styles.field}>What happened?<textarea maxLength={2000} required value={reason} onChange={(event) => setReason(event.target.value)} /></label>
      {error && <div className={styles.notice} data-tone="error">{error}</div>}
      <button className={styles.dangerButton} disabled={submitting || !reason.trim()} type="submit">{submitting ? "Submitting…" : "Open dispute"}</button>
    </form>
  );
}

function ReschedulePanel({ booking, perspective, onChanged, onBusyChange }: { booking: Booking; perspective: "customer" | "vendor"; onChanged: (booking: Booking) => void; onBusyChange: (busy: boolean) => void }) {
  const [date, setDate] = useState(booking.pendingReschedule?.eventDate ?? "");
  const [startTime, setStartTime] = useState(booking.pendingReschedule?.startTime ?? booking.startTime ?? "");
  const [endTime, setEndTime] = useState(booking.pendingReschedule?.endTime ?? booking.endTime ?? "");
  const [error, setError] = useState(""); const [submitting, setSubmitting] = useState(false);
  const pending = booking.pendingReschedule;
  const alreadyApproved = perspective === "customer" ? pending?.customerApproved : pending?.vendorApproved;
  async function propose(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSubmitting(true); onBusyChange(true); setError("");
    try {
      const { data, error: body } = await api.POST("/api/v1/bookings/{id}/reschedule-propose", { params: { path: { id: booking.id } }, body: { eventDate: date, startTime: startTime || undefined, endTime: endTime || undefined, eventTimezone: booking.eventTimezone, version: booking.version } });
      if (!data) setError(apiErrorMessage(body, "The reschedule could not be proposed")); else onChanged(data);
    } finally { setSubmitting(false); onBusyChange(false); }
  }
  async function decide(accept: boolean) {
    setSubmitting(true); onBusyChange(true); setError("");
    try {
      const { data, error: body } = await api.POST("/api/v1/bookings/{id}/reschedule-confirm", { params: { path: { id: booking.id } }, body: { accept, version: booking.version } });
      if (!data) setError(apiErrorMessage(body, "The reschedule response could not be saved")); else onChanged(data);
    } finally { setSubmitting(false); onBusyChange(false); }
  }
  if (pending) return (
    <div className={`${styles.actionPanel} ${styles.stack}`}>
      <h3>Proposed new date</h3>
      <div className={styles.notice}>{formatDateOnly(pending.eventDate)}{pending.startTime ? ` · ${pending.startTime.slice(0, 5)}–${pending.endTime?.slice(0, 5)}` : ""}</div>
      <div className={styles.chips}><span className={styles.chip}>Customer {pending.customerApproved ? "approved" : "pending"}</span><span className={styles.chip}>Vendor {pending.vendorApproved ? "approved" : "pending"}</span></div>
      {alreadyApproved ? <div className={styles.notice}>Waiting for the other party.</div> : <div className={styles.actions}><button className={styles.button} disabled={submitting} type="button" onClick={() => void decide(true)}>Accept new date</button><button className={styles.dangerButton} disabled={submitting} type="button" onClick={() => void decide(false)}>Reject</button></div>}
      {error && <div className={styles.notice} data-tone="error">{error}</div>}
    </div>
  );
  return (
    <form className={`${styles.actionPanel} ${styles.stack}`} onSubmit={propose}>
      <h3>Propose one new date</h3><p className={styles.panelIntro}>The date is rechecked only after both parties approve. Rescheduling requires at least 14 days’ notice.</p>
      <div className={styles.formGrid}><label className={styles.field}>New date<input min={addDays(localDateValue(), 1)} required type="date" value={date} onChange={(event) => setDate(event.target.value)} /></label>{booking.startTime && <><label className={styles.field}>Start<input required type="time" value={startTime} onChange={(event) => setStartTime(event.target.value)} /></label><label className={styles.field}>End<input required type="time" value={endTime} onChange={(event) => setEndTime(event.target.value)} /></label></>}</div>
      {error && <div className={styles.notice} data-tone="error">{error}</div>}
      <button className={styles.button} disabled={submitting || !date} type="submit">{submitting ? "Proposing…" : "Send reschedule proposal"}</button>
    </form>
  );
}

function ActionButton({ active, disabled, onClick, children }: { active: boolean; disabled: boolean; onClick: () => void; children: ReactNode }) {
  return <button aria-pressed={active} className={styles.secondaryButton} disabled={disabled} type="button" onClick={onClick}>{children}</button>;
}
