"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import type { Booking, BookingStatus } from "@wedjan/shared";
import { apiErrorMessage } from "@wedjan/shared";
import { api } from "@/lib/api";
import { useAuth } from "@/components/platform/auth-context";
import { BOOKING_STATUSES, titleCase } from "@/lib/booking-format";
import { BookingActions } from "./booking-actions";
import {
  AsyncState,
  BookingCard,
  BookingOverview,
  BookingStatusStepper,
  BookingTimeline,
  PriceBreakdown,
  bookingStyles as styles,
} from "./booking-shared";

export function CustomerBookingsList() {
  const { me } = useAuth();
  const [status, setStatus] = useState<BookingStatus | "">("");
  const query = useInfiniteQuery({
    queryKey: ["customer-bookings", me?.account.id, status],
    enabled: Boolean(me),
    initialPageParam: "",
    queryFn: async ({ pageParam }) => {
      const { data, error } = await api.GET("/api/v1/bookings", {
        params: { query: { status: status || undefined, cursor: pageParam || undefined, limit: 20 } },
      });
      if (!data) throw new Error(apiErrorMessage(error, "Bookings could not be loaded"));
      return data;
    },
    getNextPageParam: (page) => page.nextCursor || undefined,
    refetchInterval: 60_000,
  });
  const bookings = uniqueBookings(query.data?.pages.flatMap((page) => page.items) ?? []);
  return (
    <>
      <h1 className="app-page-title">Bookings</h1>
      <p className="app-page-subtitle">Every request, hold, confirmation, and completed event in one place.</p>
      <div className={styles.filterBar}>
        <label className={styles.field}>Status<select value={status} onChange={(event) => setStatus(event.target.value as BookingStatus | "")}><option value="">All bookings</option>{BOOKING_STATUSES.map((item) => <option key={item} value={item}>{titleCase(item)}</option>)}</select></label>
        <button className={styles.secondaryButton} disabled={query.isFetching} type="button" onClick={() => void query.refetch()}>{query.isRefetching && !query.isFetchingNextPage ? "Refreshing…" : "Refresh"}</button>
        <Link className={styles.secondaryButton} href="/search">Find another vendor</Link>
      </div>
      <AsyncState loading={query.isLoading} error={!query.data ? query.error?.message : undefined} empty={bookings.length === 0 ? "No bookings match this status yet." : undefined} onRetry={() => void query.refetch()}>
        <div className={styles.bookingList}>{bookings.map((booking) => <BookingCard booking={booking} href={`/bookings/${booking.id}`} key={booking.id} />)}</div>
        {(query.hasNextPage || query.isFetchNextPageError) && (
          <div className={styles.pagination}>
            {query.isFetchNextPageError && <p role="alert">{query.error?.message ?? "More bookings could not be loaded."}</p>}
            <button
              className={styles.secondaryButton}
              disabled={query.isFetching}
              type="button"
              onClick={() => void query.fetchNextPage()}
            >
              {query.isFetchingNextPage ? "Loading more…" : query.isFetchNextPageError ? "Try loading more again" : "Load more bookings"}
            </button>
            <span aria-live="polite" className={styles.srStatus}>{query.isFetchingNextPage ? "Loading the next page of bookings" : ""}</span>
          </div>
        )}
      </AsyncState>
    </>
  );
}

function uniqueBookings(items: Booking[]): Booking[] {
  return [...new Map(items.map((booking) => [booking.id, booking])).values()];
}

export function CustomerBookingDetail({ bookingId }: { bookingId: string }) {
  const client = useQueryClient();
  const router = useRouter();
  const { me } = useAuth();
  const query = useQuery({
    queryKey: ["booking", "customer", me?.account.id, bookingId],
    enabled: Boolean(me),
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/bookings/{id}", { params: { path: { id: bookingId } } });
      if (!data) throw new Error(apiErrorMessage(error, "Booking could not be loaded"));
      return data;
    },
    refetchInterval: 60_000,
  });
  function changed(booking: Booking) {
    client.setQueryData(["booking", "customer", me?.account.id, bookingId], booking);
    void client.invalidateQueries({ queryKey: ["customer-bookings"] });
    void client.invalidateQueries({ queryKey: ["vendor-availability", booking.vendorSlug] });
  }
  const vendorPerspective = Boolean(query.data && me && query.data.customerId !== me.account.id && query.data.vendorId === me.account.id);
  useEffect(() => {
    if (vendorPerspective) router.replace(`/vendor/bookings/${bookingId}`);
  }, [bookingId, router, vendorPerspective]);
  return (
    <>
      <Link className={styles.textButton} href="/bookings">← All bookings</Link>
      <h1 className="app-page-title">{query.data?.packageTitle ?? "Booking detail"}</h1>
      <p className="app-page-subtitle">{query.data?.code ?? "Loading the latest booking state…"}</p>
      <AsyncState loading={query.isLoading} error={query.error?.message} onRetry={() => void query.refetch()}>
        {vendorPerspective
          ? <div className={styles.notice}>Opening this booking in your vendor workspace…</div>
          : query.data ? <BookingDetail booking={query.data} onChanged={changed} /> : null}
      </AsyncState>
    </>
  );
}

function BookingDetail({ booking, onChanged }: { booking: Booking; onChanged: (booking: Booking) => void }) {
  return (
    <div className={styles.detailGrid}>
      <div className={styles.stack}>
        <section className={`${styles.panel} ${styles.stack}`}><div><p className={styles.eyebrow}>Booking snapshot</p><h2>Your event</h2></div><BookingStatusStepper booking={booking} /><BookingOverview booking={booking} /></section>
        <BookingActions booking={booking} perspective="customer" onChanged={onChanged} />
      </div>
      <aside className={styles.stack}>
        <section className={styles.panel}><p className={styles.eyebrow}>Price locked at booking</p><h2>Price</h2><PriceBreakdown price={booking.price} /></section>
        <section className={styles.panel}><p className={styles.eyebrow}>Append-only history</p><h2>Timeline</h2><BookingTimeline booking={booking} /></section>
      </aside>
    </div>
  );
}
