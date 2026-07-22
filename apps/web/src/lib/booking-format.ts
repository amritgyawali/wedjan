export const BOOKING_STATUSES = [
  "DRAFT",
  "REQUESTED",
  "VENDOR_ACCEPTED",
  "PENDING_PAYMENT",
  "CONFIRMED",
  "IN_PROGRESS",
  "COMPLETED",
  "CANCELLED_BY_CUSTOMER",
  "CANCELLED_BY_VENDOR",
  "DECLINED",
  "EXPIRED",
  "DISPUTED",
] as const;

export const TERMINAL_BOOKING_STATUSES = new Set<string>([
  "COMPLETED",
  "CANCELLED_BY_CUSTOMER",
  "CANCELLED_BY_VENDOR",
  "DECLINED",
  "EXPIRED",
  "DISPUTED",
]);

export function formatMoney(cents: number, currency: string): string {
  return new Intl.NumberFormat("en", {
    style: "currency",
    currency,
    minimumFractionDigits: cents % 100 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(cents / 100);
}

export function parseMajorUnits(value: string): number | null {
  const normalized = value.trim();
  if (!/^\d+(?:\.\d{0,2})?$/.test(normalized)) return null;
  const [whole, fraction = ""] = normalized.split(".");
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  return Number.isSafeInteger(cents) ? cents : null;
}

export function formatDateOnly(value: string, options?: Intl.DateTimeFormatOptions): string {
  const parsed = parseDateOnly(value);
  if (!parsed) return value;
  return new Intl.DateTimeFormat("en", options ?? {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(parsed);
}

export function formatDateTime(value?: string, timeZone?: string): string {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.valueOf())) return value;
  return new Intl.DateTimeFormat("en", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZone,
    timeZoneName: "short",
  }).format(parsed);
}

export function formatTime(value?: string): string {
  if (!value) return "";
  const [hour, minute] = value.split(":").map(Number);
  if (!Number.isInteger(hour) || !Number.isInteger(minute)) return value;
  const suffix = hour >= 12 ? "pm" : "am";
  const displayHour = hour % 12 || 12;
  return `${displayHour}:${String(minute).padStart(2, "0")}${suffix}`;
}

export function bookingStatusLabel(value: string): string {
  const labels: Record<string, string> = {
    DRAFT: "Checkout held",
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
  return labels[value] ?? titleCase(value);
}

export function bookingStatusTone(value: string): "success" | "warning" | "danger" | "neutral" {
  if (["CONFIRMED", "COMPLETED"].includes(value)) return "success";
  if (["DRAFT", "REQUESTED", "VENDOR_ACCEPTED", "PENDING_PAYMENT", "IN_PROGRESS"].includes(value)) {
    return "warning";
  }
  if (["CANCELLED_BY_CUSTOMER", "CANCELLED_BY_VENDOR", "DECLINED", "EXPIRED", "DISPUTED"].includes(value)) {
    return "danger";
  }
  return "neutral";
}

export function titleCase(value: string): string {
  return value
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/(^|\s)\S/g, (character) => character.toUpperCase());
}

export function localDateValue(date = new Date()): string {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");
}

export function addDays(value: string, days: number): string {
  const date = parseDateOnly(value);
  if (!date) return value;
  date.setDate(date.getDate() + days);
  return localDateValue(date);
}

export function addMonths(value: string, months: number): string {
  const date = parseDateOnly(value);
  if (!date) return value;
  date.setDate(1);
  date.setMonth(date.getMonth() + months);
  return localDateValue(date).slice(0, 7) + "-01";
}

export function monthRange(monthValue: string): { from: string; to: string } {
  const monthStart = `${monthValue.slice(0, 7)}-01`;
  const first = parseDateOnly(monthStart) ?? new Date();
  const from = addDays(localDateValue(first), -first.getDay());
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0, 12);
  const to = addDays(localDateValue(last), 6 - last.getDay());
  return { from, to };
}

export function dateRange(from: string, to: string): string[] {
  const values: string[] = [];
  let cursor = from;
  while (cursor <= to && values.length <= 366) {
    values.push(cursor);
    cursor = addDays(cursor, 1);
  }
  return values;
}

export function countdownLabel(deadline: string | undefined, serverNow?: string): string {
  if (!deadline) return "";
  const end = new Date(deadline).valueOf();
  const start = serverNow ? new Date(serverNow).valueOf() : Date.now();
  const remaining = Math.max(0, end - start);
  if (remaining === 0) return "Expired";
  const minutes = Math.ceil(remaining / 60_000);
  if (minutes < 60) return `${minutes}m remaining`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return `${hours}h ${rest}m remaining`;
}

function parseDateOnly(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return null;
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]), 12);
  return Number.isNaN(date.valueOf()) ? null : date;
}
