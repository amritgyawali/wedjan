"use client";

import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import type { AvailabilityDay, Booking, BookingQuote } from "@wedjan/shared";
import {
  addMonths,
  bookingStatusLabel,
  bookingStatusTone,
  dateRange,
  formatDateOnly,
  formatDateTime,
  formatMoney,
  formatTime,
  monthRange,
  titleCase,
} from "@/lib/booking-format";
import styles from "./booking.module.css";

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

export function AvailabilityCalendar({
  month,
  days,
  selectedDate,
  minDate,
  onMonthChange,
  onSelect,
  disableUnavailable = false,
}: {
  month: string;
  days: AvailabilityDay[];
  selectedDate?: string;
  minDate?: string;
  onMonthChange: (month: string) => void;
  onSelect?: (day: AvailabilityDay | undefined, date: string) => void;
  disableUnavailable?: boolean;
}) {
  const facts = new Map(days.map((day) => [day.date, day]));
  const range = monthRange(month);
  const dates = dateRange(range.from, range.to);
  const monthPrefix = month.slice(0, 7);
  const monthLabel = formatDateOnly(`${monthPrefix}-01`, { month: "long", year: "numeric" });

  return (
    <div className={styles.stack}>
      <div className={styles.calendar}>
        <div className={styles.calendarHeader}>
          <button
            aria-label="Previous month"
            type="button"
            onClick={() => onMonthChange(addMonths(month, -1))}
          >
            ←
          </button>
          <strong>{monthLabel}</strong>
          <button
            aria-label="Next month"
            type="button"
            onClick={() => onMonthChange(addMonths(month, 1))}
          >
            →
          </button>
        </div>
        <div className={styles.weekdays} aria-hidden="true">
          {WEEKDAYS.map((weekday) => <span key={weekday}>{weekday}</span>)}
        </div>
        <div className={styles.calendarGrid} aria-label={monthLabel}>
          {dates.map((date) => {
            const day = facts.get(date);
            const status = day?.status;
            const outside = !date.startsWith(monthPrefix);
            const beforeMinimum = Boolean(minDate && date < minDate);
            const disabled = outside || beforeMinimum || (disableUnavailable && (!day || status === "BOOKED" || status === "BLACKED_OUT"));
            const description = beforeMinimum ? "Past date" : day ? availabilityDescription(day) : "Availability not loaded";
            return (
              <button
                aria-label={`${formatDateOnly(date)}. ${description}`}
                aria-pressed={selectedDate === date}
                className={styles.calendarDay}
                data-outside={outside}
                data-selected={selectedDate === date}
                data-status={status}
                disabled={disabled}
                key={date}
                type="button"
                onClick={() => onSelect?.(day, date)}
              >
                <span className={styles.calendarDayNumber}>{Number(date.slice(-2))}</span>
                <span className={styles.calendarDayStatus}>{description}</span>
              </button>
            );
          })}
        </div>
      </div>
      <div className={styles.legend} aria-label="Availability legend">
        <span style={{ color: "var(--wj-color-success)" }}>Available</span>
        <span style={{ color: "var(--wj-color-warning)" }}>Limited</span>
        <span style={{ color: "var(--wj-color-danger)" }}>Booked or blocked</span>
      </div>
    </div>
  );
}

export function StatusChip({ status }: { status: string }) {
  return (
    <span className={styles.statusChip} data-tone={bookingStatusTone(status)}>
      {bookingStatusLabel(status)}
    </span>
  );
}

export function PriceBreakdown({
  price,
}: {
  price: Booking["price"] | BookingQuote["price"];
}) {
  return (
    <div className={styles.priceBreakdown} aria-label="Price breakdown">
      <PriceRow label="Package" value={formatMoney(price.subtotalCents, price.currency)} />
      {price.addOnsCents > 0 && <PriceRow label="Add-ons" value={formatMoney(price.addOnsCents, price.currency)} />}
      {price.travelFeeCents > 0 && <PriceRow label="Travel" value={formatMoney(price.travelFeeCents, price.currency)} />}
      {price.discountCents > 0 && <PriceRow label="Discount" value={`−${formatMoney(price.discountCents, price.currency)}`} />}
      <div className={`${styles.priceRow} ${styles.priceTotal}`}>
        <span>Total</span>
        <strong>{formatMoney(price.totalCents, price.currency)}</strong>
      </div>
      <PriceRow
        label={`${price.depositPct}% deposit`}
        value={formatMoney(price.depositCents, price.currency)}
      />
      <PriceRow label="Cancellation" value={titleCase(price.cancellationPolicy)} />
    </div>
  );
}

export function BookingCard({ booking, href }: { booking: Booking; href: string }) {
  const clock = bookingClock(booking);
  return (
    <Link className={styles.bookingCard} href={href}>
      <div>
        <div className={styles.actions}>
          <StatusChip status={booking.status} />
          {clock && <Countdown deadline={clock.deadline} label={clock.label} serverNow={booking.serverNow} />}
        </div>
        <h2>{booking.packageTitle}</h2>
        <div className={styles.bookingMeta}>
          <span>{booking.vendorName}</span>
          <span>{formatDateOnly(booking.eventDate)}</span>
          <span>{booking.code}</span>
        </div>
      </div>
      <div className={styles.bookingAmount}>
        {formatMoney(booking.price.totalCents, booking.price.currency)}
      </div>
    </Link>
  );
}

export function BookingOverview({ booking }: { booking: Booking }) {
  const clock = bookingClock(booking);
  return (
    <div className={styles.stack}>
      <div className={styles.actions}>
        <StatusChip status={booking.status} />
        {clock && <Countdown deadline={clock.deadline} label={clock.label} serverNow={booking.serverNow} />}
      </div>
      <div className={styles.summaryRows}>
        <SummaryRow label="Booking code">{booking.code}</SummaryRow>
        <SummaryRow label="Vendor">
          <Link href={`/v/${booking.vendorSlug}`}>{booking.vendorName}</Link>
        </SummaryRow>
        <SummaryRow label="Package">{booking.packageTitle}</SummaryRow>
        <SummaryRow label="Event date">{formatDateOnly(booking.eventDate)}</SummaryRow>
        {(booking.startTime || booking.endTime) && (
          <SummaryRow label="Event time">
            {formatTime(booking.startTime)}{booking.endTime ? ` – ${formatTime(booking.endTime)}` : ""}
          </SummaryRow>
        )}
        <SummaryRow label="Timezone">{booking.eventTimezone}</SummaryRow>
        {booking.guests && <SummaryRow label="Guests">{booking.guests.toLocaleString()}</SummaryRow>}
        <SummaryRow label="Location type">{booking.venueLocationMode === "VENDOR" ? "At vendor location" : "Vendor travels to venue"}</SummaryRow>
        {booking.venueAddress && <SummaryRow label="Venue">{booking.venueAddress}</SummaryRow>}
        {(booking.venueCity || booking.venueCountry) && <SummaryRow label="Venue region">{[booking.venueCity, booking.venueCountry].filter(Boolean).join(", ")}</SummaryRow>}
        {booking.notes && <SummaryRow label="Notes">{booking.notes}</SummaryRow>}
        {booking.cancelReason && <SummaryRow label="Cancellation reason">{booking.cancelReason}</SummaryRow>}
      </div>
      {booking.addOns.length > 0 && (
        <div>
          <p className={styles.eyebrow}>Add-ons</p>
          <div className={styles.summaryRows}>
            {booking.addOns.map((addOn) => (
              <SummaryRow label={`${addOn.qty} × ${addOn.title}`} key={addOn.addOnId}>
                {formatMoney(addOn.totalCents, booking.price.currency)}
              </SummaryRow>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export function BookingStatusStepper({ booking }: { booking: Booking }) {
  const normal = booking.bookingMode === "REQUEST"
    ? [
        ["REQUESTED", "Request sent"],
        ["PENDING_PAYMENT", "Payment due"],
        ["CONFIRMED", "Confirmed"],
        ["IN_PROGRESS", "In progress"],
        ["COMPLETED", "Completed"],
      ] as const
    : [
        ["DRAFT", "Date held"],
        ["CONFIRMED", "Confirmed"],
        ["IN_PROGRESS", "In progress"],
        ["COMPLETED", "Completed"],
      ] as const;
  const stageStatus = booking.status === "VENDOR_ACCEPTED" ? "PENDING_PAYMENT" : booking.status;
  const terminal = ["CANCELLED_BY_CUSTOMER", "CANCELLED_BY_VENDOR", "DECLINED", "EXPIRED", "DISPUTED"].includes(booking.status);
  const reached = new Set(booking.events.map((event) => event.toStatus));
  reached.add(booking.status);
  if (reached.has("VENDOR_ACCEPTED")) reached.add("PENDING_PAYMENT");
  const currentIndex = normal.findIndex(([status]) => status === stageStatus);
  let furthestIndex = normal.reduce((furthest, [status], index) => reached.has(status) ? index : furthest, -1);
  if (currentIndex >= 0) furthestIndex = Math.max(furthestIndex, currentIndex);
  const steps = terminal
    ? [...normal, [booking.status, bookingStatusLabel(booking.status)] as const]
    : normal;

  return (
    <ol className={styles.statusStepper} aria-label="Booking progress">
      {steps.map(([status, label], index) => {
        const isTerminalStep = terminal && index === steps.length - 1;
        const state = isTerminalStep
          ? "current"
          : terminal && index <= furthestIndex
            ? "complete"
            : index < furthestIndex
              ? "complete"
              : index === currentIndex
                ? "current"
                : "future";
        return (
          <li aria-current={state === "current" ? "step" : undefined} data-state={state} key={`${status}-${index}`}>
            <span aria-hidden="true" />
            <strong>{label}</strong>
          </li>
        );
      })}
    </ol>
  );
}

export function BookingTimeline({ booking }: { booking: Booking }) {
  if (booking.events.length === 0) return <p className={styles.panelIntro}>No history yet.</p>;
  return (
    <ol className={styles.timeline}>
      {booking.events.map((event) => (
        <li key={event.id}>
          <strong>{bookingEventLabel(event)}</strong>
          <span>{titleCase(event.actor)} · {formatDateTime(event.createdAt, booking.eventTimezone)}</span>
        </li>
      ))}
    </ol>
  );
}

export function AsyncState({
  loading,
  error,
  empty,
  onRetry,
  children,
}: {
  loading: boolean;
  error?: string;
  empty?: string;
  onRetry?: () => void;
  children: ReactNode;
}) {
  if (loading) return <div className={styles.loading} aria-label="Loading" />;
  if (error) {
    return (
      <div className={styles.empty} role="alert">
        <p>{error}</p>
        {onRetry && <button className={styles.secondaryButton} type="button" onClick={onRetry}>Retry</button>}
      </div>
    );
  }
  if (empty) return <div className={styles.empty}>{empty}</div>;
  return children;
}

export function Countdown({ deadline, label, serverNow }: { deadline: string; label?: string; serverNow: string }) {
  return <CountdownClock deadline={deadline} initialNow={serverNow} key={`${deadline}:${serverNow}`} label={label} />;
}

function CountdownClock({ deadline, initialNow, label: prefix }: { deadline: string; initialNow: string; label?: string }) {
  const [effectiveNow, setEffectiveNow] = useState(() => new Date(initialNow).valueOf());

  useEffect(() => {
    const serverAnchor = new Date(initialNow).valueOf();
    const clientAnchor = performance.now();
    const update = () => setEffectiveNow(serverAnchor + (performance.now() - clientAnchor));
    const timer = window.setInterval(update, 30_000);
    return () => window.clearInterval(timer);
  }, [initialNow]);

  const remaining = Math.max(0, new Date(deadline).valueOf() - effectiveNow);
  const minutes = Math.ceil(remaining / 60_000);
  const remainingLabel = remaining === 0
    ? "Expired"
    : minutes < 60
      ? `${minutes}m remaining`
      : `${Math.floor(minutes / 60)}h ${minutes % 60}m remaining`;
  return <span className={styles.countdown}>{prefix ? `${prefix} · ${remainingLabel}` : remainingLabel}</span>;
}

export { styles as bookingStyles };

function PriceRow({ label, value }: { label: string; value: string }) {
  return <div className={styles.priceRow}><span>{label}</span><strong>{value}</strong></div>;
}

function SummaryRow({ label, children }: { label: string; children: ReactNode }) {
  return <div className={styles.summaryRow}><span>{label}</span><strong>{children}</strong></div>;
}

function availabilityDescription(day: AvailabilityDay): string {
  if (day.status === "AVAILABLE") return day.capacity > 1 ? `${day.capacity} spots open` : "Available";
  if (day.status === "LIMITED") return `${Math.max(0, day.capacity - day.occupied)} spots left`;
  if (day.status === "BOOKED") return "Booked";
  return "Blocked";
}

function bookingClock(booking: Booking): { deadline: string; label: string } | null {
  if (booking.status === "DRAFT" && booking.holdExpiresAt) return { deadline: booking.holdExpiresAt, label: "Hold" };
  if (booking.status === "REQUESTED" && booking.slaDueAt) return { deadline: booking.slaDueAt, label: "Vendor reply" };
  if (["VENDOR_ACCEPTED", "PENDING_PAYMENT"].includes(booking.status) && booking.paymentDueAt) {
    return { deadline: booking.paymentDueAt, label: "Payment window" };
  }
  if (booking.status === "COMPLETED" && booking.disputeWindowEndsAt) {
    return { deadline: booking.disputeWindowEndsAt, label: "Dispute window" };
  }
  return null;
}

function bookingEventLabel(event: Booking["events"][number]): string {
  const eventType = event.metadata.eventType;
  if (typeof eventType === "string" && eventType.trim()) return titleCase(eventType);
  return event.fromStatus
    ? `${bookingStatusLabel(event.fromStatus)} → ${bookingStatusLabel(event.toStatus)}`
    : bookingStatusLabel(event.toStatus);
}
