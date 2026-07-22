export const BOOKING_STATUS_LABELS: Record<string, string> = {
  DRAFT: "Checkout hold",
  REQUESTED: "Awaiting vendor",
  VENDOR_ACCEPTED: "Vendor accepted",
  PENDING_PAYMENT: "Payment due",
  CONFIRMED: "Confirmed",
  IN_PROGRESS: "In progress",
  COMPLETED: "Completed",
  CANCELLED_BY_CUSTOMER: "Cancelled by customer",
  CANCELLED_BY_VENDOR: "Cancelled by vendor",
  DECLINED: "Declined",
  EXPIRED: "Expired",
  DISPUTED: "Disputed",
};

export const AVAILABILITY_STATUS_LABELS: Record<string, string> = {
  AVAILABLE: "Available",
  LIMITED: "Limited",
  BOOKED: "Booked",
  BLACKED_OUT: "Closed",
};

export function money(cents: number, currency: string): string {
  return new Intl.NumberFormat("en", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(cents / 100);
}

export function localIsoDate(date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function addCalendarDays(value: string, days: number): string {
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  date.setDate(date.getDate() + days);
  return localIsoDate(date);
}

export function readableDate(value: string): string {
  const [year, month, day] = value.split("-").map(Number);
  return new Intl.DateTimeFormat("en", {
    weekday: "short",
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(year, month - 1, day));
}

export function shortDate(value: string): { weekday: string; day: string; month: string } {
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return {
    weekday: new Intl.DateTimeFormat("en", { weekday: "short" }).format(date),
    day: String(day),
    month: new Intl.DateTimeFormat("en", { month: "short" }).format(date),
  };
}

export function readableTimestamp(value?: string | null): string | null {
  if (!value) return null;
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}

export function timeRemaining(deadline?: string | null, now = Date.now()): string | null {
  if (!deadline) return null;
  const remaining = new Date(deadline).getTime() - now;
  if (remaining <= 0) return "Expired";
  const minutes = Math.ceil(remaining / 60_000);
  if (minutes < 60) return `${minutes}m left`;
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  if (hours < 48) return remainder ? `${hours}h ${remainder}m left` : `${hours}h left`;
  return `${Math.ceil(hours / 24)}d left`;
}

export function bookingDeadline(booking: {
  status: string;
  holdExpiresAt?: string | null;
  slaDueAt?: string | null;
  paymentDueAt?: string | null;
  disputeWindowEndsAt?: string | null;
}): string | null {
  if (booking.status === "REQUESTED") return booking.slaDueAt ?? null;
  if (booking.status === "DRAFT") return booking.holdExpiresAt ?? null;
  if (booking.status === "PENDING_PAYMENT") return booking.paymentDueAt ?? null;
  if (booking.status === "COMPLETED") return booking.disputeWindowEndsAt ?? null;
  return null;
}

export function createIdempotencyKey(): string {
  return `mobile-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}
