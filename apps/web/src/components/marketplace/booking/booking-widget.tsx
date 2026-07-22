"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type {
  BookingConfigurationRequest,
  BookingQuote,
  VendorPublic,
} from "@wedjan/shared";
import { apiErrorMessage } from "@wedjan/shared";
import { useAuth } from "@/components/platform/auth-context";
import { api } from "@/lib/api";
import { authRoute } from "@/lib/auth-return";
import { localDateValue, monthRange } from "@/lib/booking-format";
import {
  AvailabilityCalendar,
  PriceBreakdown,
  bookingStyles as styles,
} from "./booking-shared";

type VendorPackage = VendorPublic["packages"][number];
type VendorAddOn = NonNullable<VendorPublic["addOns"]>[number];
type BookingDraft = {
  packageId: string;
  eventDate: string;
  startTime: string;
  endTime: string;
  guests: string;
  locationMode: "TRAVEL" | "VENDOR";
  venueAddress: string;
  venueCity: string;
  venueCountry: string;
  venueLat: string;
  venueLng: string;
  notes: string;
  quantities: Record<string, number>;
};

export function BookingWidget({
  vendorSlug,
  packages,
  addOns,
}: {
  vendorSlug: string;
  packages: VendorPackage[];
  addOns: VendorAddOn[];
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { status: authStatus, me } = useAuth();
  const today = localDateValue();
  const requestedPackage = searchParams.get("package");
  const requestedDate = searchParams.get("date") ?? "";
  const initialPackage = packages.find((item) => item.id === requestedPackage) ?? packages[0];
  const [packageId, setPackageId] = useState(initialPackage?.id ?? "");
  const [eventDate, setEventDate] = useState(requestedDate);
  const [month, setMonth] = useState(`${(requestedDate || today).slice(0, 7)}-01`);
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [guests, setGuests] = useState("100");
  const [locationMode, setLocationMode] = useState<"TRAVEL" | "VENDOR">("TRAVEL");
  const [venueAddress, setVenueAddress] = useState("");
  const [venueCity, setVenueCity] = useState("");
  const [venueCountry, setVenueCountry] = useState("");
  const [venueLat, setVenueLat] = useState("");
  const [venueLng, setVenueLng] = useState("");
  const [locating, setLocating] = useState(false);
  const [locationError, setLocationError] = useState("");
  const [notes, setNotes] = useState("");
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [submitError, setSubmitError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const idempotency = useRef({ signature: "", key: "" });
  const selectedPackage = packages.find((item) => item.id === packageId) ?? initialPackage;
  const range = monthRange(month);
  const minimumDate = selectedPackage?.allowSameDay ? today : nextDay(today);

  const availabilityQuery = useQuery({
    queryKey: ["vendor-availability", vendorSlug, range.from, range.to],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/vendors/{slug}/availability", {
        params: { path: { slug: vendorSlug }, query: range },
      });
      if (!data) throw new Error(apiErrorMessage(error, "Availability could not be loaded"));
      return data;
    },
    staleTime: 60_000,
  });

  useEffect(() => {
    const timer = window.setTimeout(() => {
      try {
        const stored = sessionStorage.getItem(bookingDraftKey(vendorSlug));
        if (!stored) return;
        sessionStorage.removeItem(bookingDraftKey(vendorSlug));
        const draft = JSON.parse(stored) as Partial<BookingDraft>;
        if (draft.packageId && packages.some((item) => item.id === draft.packageId)) setPackageId(draft.packageId);
        if (typeof draft.eventDate === "string") {
          setEventDate(draft.eventDate);
          if (draft.eventDate) setMonth(`${draft.eventDate.slice(0, 7)}-01`);
        }
        if (typeof draft.startTime === "string") setStartTime(draft.startTime);
        if (typeof draft.endTime === "string") setEndTime(draft.endTime);
        if (typeof draft.guests === "string") setGuests(draft.guests);
        if (draft.locationMode === "TRAVEL" || draft.locationMode === "VENDOR") setLocationMode(draft.locationMode);
        if (typeof draft.venueAddress === "string") setVenueAddress(draft.venueAddress);
        if (typeof draft.venueCity === "string") setVenueCity(draft.venueCity);
        if (typeof draft.venueCountry === "string") setVenueCountry(draft.venueCountry);
        if (typeof draft.venueLat === "string") setVenueLat(draft.venueLat);
        if (typeof draft.venueLng === "string") setVenueLng(draft.venueLng);
        if (typeof draft.notes === "string") setNotes(draft.notes);
        if (draft.quantities && typeof draft.quantities === "object") {
          setQuantities(Object.fromEntries(Object.entries(draft.quantities).filter((entry): entry is [string, number] => Number.isInteger(entry[1]) && entry[1] >= 0)));
        }
      } catch {
        sessionStorage.removeItem(bookingDraftKey(vendorSlug));
      }
    }, 0);
    return () => window.clearTimeout(timer);
  }, [packages, vendorSlug]);

  const availableAddOns = useMemo(
    () => addOns.filter((item) => !item.packageId || item.packageId === packageId),
    [addOns, packageId],
  );
  const timezone = availabilityQuery.data?.timezone
    ?? Intl.DateTimeFormat().resolvedOptions().timeZone
    ?? "UTC";
  const selectedDay = availabilityQuery.data?.days.find((day) => day.date === eventDate);
  const selectedSlot = selectedDay?.slots.find((slot) => slot.start === startTime && slot.end === endTime);
  const dateIsBookable = Boolean(
    selectedDay
      && ["AVAILABLE", "LIMITED"].includes(selectedDay.status)
      && eventDate >= minimumDate
      && (availabilityQuery.data?.mode !== "SLOT" || selectedSlot?.status === "AVAILABLE"),
  );
  const configuration = useMemo<BookingConfigurationRequest | null>(() => {
    if (!selectedPackage || !eventDate || !dateIsBookable) return null;
    if (availabilityQuery.data?.mode === "SLOT" && (!startTime || !endTime)) return null;
    const guestCount = Number(guests);
    if (!Number.isInteger(guestCount) || guestCount < 1) return null;
    const latitude = Number(venueLat);
    const longitude = Number(venueLng);
    if (
      locationMode === "TRAVEL"
      && (!venueAddress.trim() || !venueCity.trim() || !/^[A-Za-z]{2}$/.test(venueCountry.trim())
        || !venueLat.trim() || !venueLng.trim() || !validCoordinate(latitude, -90, 90) || !validCoordinate(longitude, -180, 180))
    ) return null;
    return {
      packageId: selectedPackage.id,
      eventDate,
      eventTimezone: timezone,
      venueLocationMode: locationMode,
      startTime: startTime || undefined,
      endTime: endTime || undefined,
      guests: guestCount,
      venueAddress: locationMode === "TRAVEL" ? venueAddress.trim() : undefined,
      venueCity: locationMode === "TRAVEL" ? venueCity.trim() : undefined,
      venueCountry: locationMode === "TRAVEL" ? venueCountry.trim().toUpperCase() : undefined,
      venueLat: locationMode === "TRAVEL" ? latitude : undefined,
      venueLng: locationMode === "TRAVEL" ? longitude : undefined,
      notes: notes.trim() || undefined,
      addOns: Object.entries(quantities)
        .filter(([, quantity]) => Number.isInteger(quantity) && quantity > 0)
        .map(([addOnId, qty]) => ({ addOnId, qty })),
      expectedPackageVersion: selectedPackage.version,
    };
  }, [availabilityQuery.data?.mode, dateIsBookable, endTime, eventDate, guests, locationMode, notes, quantities, selectedPackage, startTime, timezone, venueAddress, venueCity, venueCountry, venueLat, venueLng]);
  const pricingSignature = JSON.stringify([
    packageId,
    eventDate,
    startTime,
    endTime,
    guests,
    Object.entries(quantities).filter(([, quantity]) => Number.isInteger(quantity) && quantity > 0).sort(([left], [right]) => left.localeCompare(right)),
    timezone,
    locationMode,
    venueCity,
    venueCountry,
    venueLat,
    venueLng,
  ]);
  const debouncedPricingSignature = useDebouncedValue(pricingSignature, 400);
  const pricingSettled = pricingSignature === debouncedPricingSignature;
  const canQuote = authStatus === "authenticated" && Boolean(me?.roles.includes("CUSTOMER")) && Boolean(configuration);
  const quoteQuery = useQuery<BookingQuote>({
    queryKey: ["booking-quote", me?.account.id, debouncedPricingSignature],
    enabled: canQuote && pricingSettled,
    retry: false,
    queryFn: async () => {
      if (!configuration) throw new Error("Choose an available date first");
      const { data, error } = await api.POST("/api/v1/bookings/quote", { body: configuration });
      if (!data) throw new Error(apiErrorMessage(error, "The exact quote could not be calculated"));
      return data;
    },
  });
  const quoteIsCurrent = pricingSettled && Boolean(quoteQuery.data) && !quoteQuery.isFetching;

  if (!selectedPackage) return null;

  function useCurrentLocation() {
    if (!navigator.geolocation) {
      setLocationError("Location access is unavailable in this browser. Enter the venue coordinates manually.");
      return;
    }
    setLocating(true);
    setLocationError("");
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setVenueLat(position.coords.latitude.toFixed(6));
        setVenueLng(position.coords.longitude.toFixed(6));
        setLocating(false);
      },
      () => {
        setLocationError("We could not read your location. Enter the venue coordinates manually.");
        setLocating(false);
      },
      { enableHighAccuracy: false, maximumAge: 300_000, timeout: 10_000 },
    );
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError("");
    if (!configuration) {
      setSubmitError("Choose an available date and time before continuing.");
      return;
    }
    if (authStatus !== "authenticated") {
      const draft: BookingDraft = { packageId, eventDate, startTime, endTime, guests, locationMode, venueAddress, venueCity, venueCountry, venueLat, venueLng, notes, quantities };
      try { sessionStorage.setItem(bookingDraftKey(vendorSlug), JSON.stringify(draft)); } catch { /* Continue with the URL-safe package/date handoff. */ }
      const next = new URLSearchParams(searchParams.toString());
      next.set("package", packageId);
      next.set("date", eventDate);
      router.push(authRoute("/login", `${pathname}?${next}`));
      return;
    }
    if (!me?.roles.includes("CUSTOMER")) {
      setSubmitError("Add the “Plan events” role in Settings before making a booking.");
      return;
    }
    if (!quoteIsCurrent) {
      setSubmitError("Wait for the exact quote before reserving this date.");
      return;
    }

    const signature = JSON.stringify(configuration);
    if (idempotency.current.signature !== signature) {
      idempotency.current = { signature, key: crypto.randomUUID() };
    }
    setSubmitting(true);
    try {
      const { data, error } = await api.POST("/api/v1/bookings", {
        params: { header: { "Idempotency-Key": idempotency.current.key } },
        body: configuration,
      });
      if (!data) {
        setSubmitError(apiErrorMessage(error, "The date could not be reserved. Recheck availability and try again."));
        return;
      }
      idempotency.current = { signature: "", key: "" };
      try { sessionStorage.removeItem(bookingDraftKey(vendorSlug)); } catch { /* Storage may be unavailable. */ }
      router.push(`/bookings/${data.id}`);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className={styles.panel} id="booking" aria-labelledby="booking-widget-title">
      <p className={styles.eyebrow}>Real-time booking</p>
      <h2 id="booking-widget-title">Check your date</h2>
      <p className={styles.panelIntro}>Choose a priced package, live availability, and event details. Your exact total comes from wedjan.</p>
      <form className={styles.stack} onSubmit={submit}>
        <label className={styles.field}>
          Package
          <select value={packageId} onChange={(event) => { setPackageId(event.target.value); setQuantities({}); }}>
            {packages.map((item) => (
              <option key={item.id} value={item.id}>{item.title}</option>
            ))}
          </select>
        </label>

        {availabilityQuery.isLoading ? (
          <div className={styles.loading} aria-label="Loading availability" />
        ) : availabilityQuery.error ? (
          <div className={styles.notice} data-tone="error" role="alert">
            {availabilityQuery.error.message}
            <button className={styles.textButton} type="button" onClick={() => void availabilityQuery.refetch()}>Retry</button>
          </div>
        ) : (
          <AvailabilityCalendar
            month={month}
            days={availabilityQuery.data?.days ?? []}
            selectedDate={eventDate}
            minDate={minimumDate}
            disableUnavailable
            onMonthChange={setMonth}
            onSelect={(day, date) => {
              setEventDate(date);
              const slot = day?.slots.find((item) => item.status === "AVAILABLE");
              setStartTime(slot?.start ?? "");
              setEndTime(slot?.end ?? "");
            }}
          />
        )}

        <label className={styles.field}>
          Event date
          <input
            min={minimumDate}
            type="date"
            value={eventDate}
            onChange={(event) => {
              setEventDate(event.target.value);
              setStartTime("");
              setEndTime("");
              if (event.target.value) setMonth(`${event.target.value.slice(0, 7)}-01`);
            }}
          />
        </label>

        {availabilityQuery.data?.mode === "SLOT" && eventDate && (
          <div className={styles.stack}>
            <p className={styles.eyebrow}>Available times</p>
            <div className={styles.slots}>
              {(availabilityQuery.data.days.find((day) => day.date === eventDate)?.slots ?? []).map((slot) => (
                <button
                  className={styles.slotButton}
                  data-selected={startTime === slot.start && endTime === slot.end}
                  disabled={slot.status !== "AVAILABLE"}
                  key={`${slot.start}-${slot.end}`}
                  type="button"
                  onClick={() => { setStartTime(slot.start); setEndTime(slot.end); }}
                >
                  {slot.label || `${slot.start.slice(0, 5)}–${slot.end.slice(0, 5)}`}
                </button>
              ))}
            </div>
          </div>
        )}

        {eventDate && availabilityQuery.isSuccess && !dateIsBookable && (
          <div className={styles.notice} data-tone="error" role="alert">
            Choose an available {availabilityQuery.data.mode === "SLOT" ? "date and time" : "date"} from the live calendar.
          </div>
        )}

        <div className={styles.formGrid}>
          <label className={styles.field}>
            Guests
            <input min="1" inputMode="numeric" type="number" value={guests} onChange={(event) => setGuests(event.target.value)} />
          </label>
          <label className={styles.field}>
            Event timezone
            <input readOnly value={timezone} />
          </label>
          <label className={`${styles.field} ${styles.fieldWide}`}>Event location<select value={locationMode} onChange={(event) => { setLocationMode(event.target.value as "TRAVEL" | "VENDOR"); setLocationError(""); }}><option value="TRAVEL">Vendor travels to the event venue</option><option value="VENDOR">Event is at the vendor’s location</option></select></label>
          {locationMode === "TRAVEL" && <>
            <label className={`${styles.field} ${styles.fieldWide}`}>
              Venue address
              <input maxLength={500} required placeholder="Venue or event address" value={venueAddress} onChange={(event) => setVenueAddress(event.target.value)} />
            </label>
            <label className={styles.field}>Venue city<input maxLength={120} required value={venueCity} onChange={(event) => setVenueCity(event.target.value)} /></label>
            <label className={styles.field}>Country code<input autoCapitalize="characters" maxLength={2} minLength={2} pattern="[A-Za-z]{2}" required value={venueCountry} onChange={(event) => setVenueCountry(event.target.value.toUpperCase())} placeholder="NP" /></label>
            <label className={styles.field}>Venue latitude<input max="90" min="-90" required step="any" type="number" value={venueLat} onChange={(event) => setVenueLat(event.target.value)} /></label>
            <label className={styles.field}>Venue longitude<input max="180" min="-180" required step="any" type="number" value={venueLng} onChange={(event) => setVenueLng(event.target.value)} /></label>
            <div className={`${styles.actions} ${styles.fieldWide}`}><button className={styles.secondaryButton} disabled={locating} type="button" onClick={useCurrentLocation}>{locating ? "Reading location…" : "Use current location"}</button><span className={styles.coordinateHint}>Coordinates let the server verify the service radius and calculate travel exactly.</span></div>
            {locationError && <div className={`${styles.notice} ${styles.fieldWide}`} data-tone="error" role="alert">{locationError}</div>}
          </>}
          <label className={`${styles.field} ${styles.fieldWide}`}>
            Event notes
            <textarea maxLength={3000} placeholder="Share logistics the vendor should know" value={notes} onChange={(event) => setNotes(event.target.value)} />
          </label>
        </div>

        {availableAddOns.length > 0 && (
          <div className={styles.stack}>
            <p className={styles.eyebrow}>Optional add-ons</p>
            {availableAddOns.map((addOn) => (
              <label className={styles.field} key={addOn.id}>
                {addOn.title} · quantity
                <input
                  min="0"
                  max={addOn.maxQty ?? 999}
                  step="1"
                  type="number"
                  value={quantities[addOn.id] ?? 0}
                  onChange={(event) => setQuantities((current) => ({ ...current, [addOn.id]: Number(event.target.value) }))}
                />
              </label>
            ))}
          </div>
        )}

        {quoteIsCurrent && quoteQuery.data && <PriceBreakdown price={quoteQuery.data.price} />}
        {(quoteQuery.isFetching || (canQuote && !pricingSettled)) && <div className={styles.notice}>Recalculating your exact total…</div>}
        {quoteQuery.error && <div className={styles.notice} data-tone="error" role="alert">{quoteQuery.error.message}</div>}
        {authStatus === "anonymous" && <div className={styles.notice}>Availability is public. You’ll sign in only when you’re ready to reserve.</div>}
        {authStatus === "authenticated" && !me?.roles.includes("CUSTOMER") && (
          <div className={styles.notice}>This account does not yet have the Plan events role. <Link href="/settings">Add it in Settings</Link>.</div>
        )}
        {submitError && <div className={styles.notice} data-tone="error" role="alert">{submitError}</div>}
        <button
          className={styles.button}
          disabled={submitting || authStatus === "loading" || !configuration || (authStatus === "authenticated" && !quoteIsCurrent)}
          type="submit"
        >
          {submitting
            ? "Reserving…"
            : selectedPackage.bookingMode === "INSTANT"
              ? "Start instant booking"
              : "Send booking request"}
        </button>
      </form>
    </section>
  );
}

function nextDay(value: string): string {
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day + 1, 12);
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, "0"), String(date.getDate()).padStart(2, "0")].join("-");
}

function validCoordinate(value: number, minimum: number, maximum: number): boolean {
  return Number.isFinite(value) && value >= minimum && value <= maximum;
}

function bookingDraftKey(vendorSlug: string): string {
  return `wedjan.bookingDraft.${vendorSlug}`;
}

function useDebouncedValue<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay);
    return () => window.clearTimeout(timer);
  }, [delay, value]);
  return debounced;
}
