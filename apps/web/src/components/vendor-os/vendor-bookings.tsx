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
import { BookingActions } from "@/components/marketplace/booking/booking-actions";
import {
  AsyncState,
  BookingCard,
  BookingOverview,
  BookingStatusStepper,
  BookingTimeline,
  PriceBreakdown,
  bookingStyles as styles,
} from "@/components/marketplace/booking/booking-shared";

export function VendorBookingsList() {
  const { me } = useAuth();
  const [status, setStatus] = useState<BookingStatus | "">("");
  const query = useInfiniteQuery({
    queryKey: ["vendor-bookings", me?.account.id, status],
    enabled: Boolean(me),
    initialPageParam: "",
    queryFn: async ({ pageParam }) => {
      const { data, error } = await api.GET("/api/v1/vendors/me/bookings", {
        params: { query: { status: status || undefined, cursor: pageParam || undefined, limit: 20 } },
      });
      if (!data) throw new Error(apiErrorMessage(error, "Booking requests could not be loaded"));
      return data;
    },
    getNextPageParam: (page) => page.nextCursor || undefined,
    refetchInterval: 60_000,
  });
  const bookings = uniqueBookings(query.data?.pages.flatMap((page) => page.items) ?? []);

  return (
    <>
      <h1 className="app-page-title">Booking inbox</h1>
      <p className="app-page-subtitle">Requests and upcoming work are ordered by the deadlines that need your attention first.</p>
      <div className={styles.filterBar}>
        <label className={styles.field}>
          Status
          <select value={status} onChange={(event) => setStatus(event.target.value as BookingStatus | "")}>
            <option value="">All bookings</option>
            {BOOKING_STATUSES.map((item) => <option key={item} value={item}>{titleCase(item)}</option>)}
          </select>
        </label>
        <button className={styles.secondaryButton} disabled={query.isFetching} type="button" onClick={() => void query.refetch()}>{query.isRefetching && !query.isFetchingNextPage ? "Refreshing…" : "Refresh"}</button>
        <Link className={styles.secondaryButton} href="/vendor/calendar">Manage calendar</Link>
      </div>
      <AsyncState
        loading={query.isLoading}
        error={!query.data ? query.error?.message : undefined}
        empty={bookings.length === 0 ? "No vendor bookings match this status yet." : undefined}
        onRetry={() => void query.refetch()}
      >
        <div className={styles.bookingList}>
          {bookings.map((booking) => (
            <BookingCard booking={booking} href={`/vendor/bookings/${booking.id}`} key={booking.id} />
          ))}
        </div>
        {(query.hasNextPage || query.isFetchNextPageError) && (
          <div className={styles.pagination}>
            {query.isFetchNextPageError && <p role="alert">{query.error?.message ?? "More booking requests could not be loaded."}</p>}
            <button
              className={styles.secondaryButton}
              disabled={query.isFetching}
              type="button"
              onClick={() => void query.fetchNextPage()}
            >
              {query.isFetchingNextPage ? "Loading more…" : query.isFetchNextPageError ? "Try loading more again" : "Load more bookings"}
            </button>
            <span aria-live="polite" className={styles.srStatus}>{query.isFetchingNextPage ? "Loading the next page of booking requests" : ""}</span>
          </div>
        )}
      </AsyncState>
    </>
  );
}

function uniqueBookings(items: Booking[]): Booking[] {
  return [...new Map(items.map((booking) => [booking.id, booking])).values()];
}

export function VendorBookingDetail({ bookingId }: { bookingId: string }) {
  const client = useQueryClient();
  const router = useRouter();
  const { me } = useAuth();
  const query = useQuery({
    queryKey: ["booking", "vendor", me?.account.id, bookingId],
    enabled: Boolean(me),
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/bookings/{id}", {
        params: { path: { id: bookingId } },
      });
      if (!data) throw new Error(apiErrorMessage(error, "Booking could not be loaded"));
      return data;
    },
    refetchInterval: 60_000,
  });

  function changed(booking: Booking) {
    client.setQueryData(["booking", "vendor", me?.account.id, bookingId], booking);
    void client.invalidateQueries({ queryKey: ["vendor-bookings"] });
    void client.invalidateQueries({ queryKey: ["vendor-calendar"] });
    void client.invalidateQueries({ queryKey: ["vendor-availability"] });
  }
  const customerPerspective = Boolean(query.data && me && query.data.vendorId !== me.account.id && query.data.customerId === me.account.id);
  useEffect(() => {
    if (customerPerspective) router.replace(`/bookings/${bookingId}`);
  }, [bookingId, customerPerspective, router]);

  return (
    <>
      <Link className={styles.textButton} href="/vendor/bookings">← Booking inbox</Link>
      <h1 className="app-page-title">{query.data?.packageTitle ?? "Booking detail"}</h1>
      <p className="app-page-subtitle">{query.data?.code ?? "Loading the latest booking state…"}</p>
      <AsyncState loading={query.isLoading} error={query.error?.message} onRetry={() => void query.refetch()}>
        {customerPerspective
          ? <div className={styles.notice}>Opening this booking in your customer workspace…</div>
          : query.data ? <VendorBookingWorkspace booking={query.data} onChanged={changed} /> : null}
      </AsyncState>
    </>
  );
}

function VendorBookingWorkspace({ booking, onChanged }: { booking: Booking; onChanged: (booking: Booking) => void }) {
  return (
    <div className={styles.detailGrid}>
      <div className={styles.stack}>
        <section className={`${styles.panel} ${styles.stack}`}>
          <div><p className={styles.eyebrow}>Booking snapshot</p><h2>Event and customer requirements</h2></div>
          <BookingStatusStepper booking={booking} />
          <BookingOverview booking={booking} />
        </section>
        <BookingActions booking={booking} perspective="vendor" onChanged={onChanged} />
      </div>
      <aside className={styles.stack}>
        <section className={styles.panel}>
          <p className={styles.eyebrow}>Quoted terms</p>
          <h2>Price</h2>
          <PriceBreakdown price={booking.price} />
        </section>
        <section className={styles.panel}>
          <p className={styles.eyebrow}>Append-only history</p>
          <h2>Timeline</h2>
          <BookingTimeline booking={booking} />
        </section>
      </aside>
    </div>
  );
}
