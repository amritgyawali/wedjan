import { useCallback, useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  SafeAreaView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { useFocusEffect, useRouter } from "expo-router";
import { apiErrorMessage, type Booking, type BookingStatus } from "@wedjan/shared";
import { tokens } from "@wedjan/ui-tokens";
import { api } from "@/lib/api";
import {
  BOOKING_STATUS_LABELS,
  bookingDeadline,
  money,
  readableDate,
  timeRemaining,
} from "@/lib/booking-ui";
import { useAuth } from "@/lib/auth-context";

const CUSTOMER_FILTERS: { label: string; value?: BookingStatus }[] = [
  { label: "All" },
  { label: "Awaiting", value: "REQUESTED" },
  { label: "Payment", value: "PENDING_PAYMENT" },
  { label: "Confirmed", value: "CONFIRMED" },
  { label: "Past", value: "COMPLETED" },
];

const VENDOR_FILTERS: { label: string; value?: BookingStatus }[] = [
  { label: "All" },
  { label: "Needs reply", value: "REQUESTED" },
  { label: "Payment", value: "PENDING_PAYMENT" },
  { label: "Confirmed", value: "CONFIRMED" },
  { label: "In progress", value: "IN_PROGRESS" },
];

type FooterError = { message: string; retry: "refresh" | "more" };

export default function BookingsScreen() {
  const router = useRouter();
  const { activeRole } = useAuth();
  const vendorView = activeRole === "VENDOR";
  const [items, setItems] = useState<Booking[]>([]);
  const [filter, setFilter] = useState<BookingStatus>();
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [nextCursor, setNextCursor] = useState<string>();
  const [error, setError] = useState<string>();
  const [footerError, setFooterError] = useState<FooterError>();
  const [now, setNow] = useState(Date.now());
  const itemsRef = useRef<Booking[]>([]);
  const requestGeneration = useRef(0);
  const firstPageInFlight = useRef(false);
  const pageRequestSequence = useRef(0);
  const activePageRequest = useRef<number | undefined>(undefined);

  useEffect(() => {
    requestGeneration.current += 1;
    firstPageInFlight.current = false;
    activePageRequest.current = undefined;
    itemsRef.current = [];
    setItems([]);
    setFilter(undefined);
    setNextCursor(undefined);
    setError(undefined);
    setFooterError(undefined);
    setLoadingMore(false);
  }, [activeRole]);

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(timer);
  }, []);

  const requestPage = useCallback(
    (cursor?: string) => {
      const query = { status: filter, cursor, limit: 20 };
      return vendorView
        ? api.GET("/api/v1/vendors/me/bookings", { params: { query } })
        : api.GET("/api/v1/bookings", { params: { query } });
    },
    [filter, vendorView],
  );

  const load = useCallback(async (pull = false) => {
    if (activeRole !== "CUSTOMER" && activeRole !== "VENDOR") {
      setLoading(false);
      setRefreshing(false);
      return;
    }
    const generation = ++requestGeneration.current;
    const retainItems = itemsRef.current.length > 0;
    firstPageInFlight.current = true;
    activePageRequest.current = undefined;
    setLoading(!pull && !retainItems);
    setRefreshing(pull || retainItems);
    setLoadingMore(false);
    setError(undefined);
    setFooterError(undefined);
    try {
      const result = await requestPage();
      if (requestGeneration.current !== generation) return;
      if (result.data) {
        const next = uniqueBookings(result.data.items);
        itemsRef.current = next;
        setItems(next);
        setNextCursor(result.data.nextCursor);
      } else {
        const message = apiErrorMessage(result.error, "Could not load bookings");
        if (retainItems) setFooterError({ message, retry: "refresh" });
        else setError(message);
      }
    } catch (cause) {
      if (requestGeneration.current !== generation) return;
      const message = cause instanceof Error ? cause.message : "Could not load bookings";
      if (retainItems) setFooterError({ message, retry: "refresh" });
      else setError(message);
    } finally {
      if (requestGeneration.current === generation) {
        firstPageInFlight.current = false;
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, [activeRole, requestPage]);

  const loadMore = useCallback(async (retry = false) => {
    const cursor = nextCursor;
    if ((activeRole !== "CUSTOMER" && activeRole !== "VENDOR") || !cursor
        || firstPageInFlight.current || activePageRequest.current !== undefined
        || (footerError && !retry)) return;
    const generation = requestGeneration.current;
    const requestId = ++pageRequestSequence.current;
    activePageRequest.current = requestId;
    setLoadingMore(true);
    setFooterError(undefined);
    try {
      const result = await requestPage(cursor);
      if (requestGeneration.current !== generation || activePageRequest.current !== requestId) return;
      if (result.data) {
        setItems((current) => {
          const merged = uniqueBookings([...current, ...result.data.items]);
          itemsRef.current = merged;
          return merged;
        });
        setNextCursor(result.data.nextCursor && result.data.nextCursor !== cursor
          ? result.data.nextCursor
          : undefined);
      } else {
        setFooterError({
          message: apiErrorMessage(result.error, "Could not load more bookings"),
          retry: "more",
        });
      }
    } catch (cause) {
      if (requestGeneration.current === generation && activePageRequest.current === requestId) {
        setFooterError({
          message: cause instanceof Error ? cause.message : "Could not load more bookings",
          retry: "more",
        });
      }
    } finally {
      if (activePageRequest.current === requestId) {
        activePageRequest.current = undefined;
        if (requestGeneration.current === generation) setLoadingMore(false);
      }
    }
  }, [activeRole, footerError, nextCursor, requestPage]);

  const selectFilter = useCallback((value?: BookingStatus) => {
    if (filter === value) return;
    requestGeneration.current += 1;
    firstPageInFlight.current = false;
    activePageRequest.current = undefined;
    itemsRef.current = [];
    setItems([]);
    setNextCursor(undefined);
    setError(undefined);
    setFooterError(undefined);
    setLoadingMore(false);
    setFilter(value);
  }, [filter]);

  useFocusEffect(
    useCallback(() => {
      void load();
      return () => {
        requestGeneration.current += 1;
        firstPageInFlight.current = false;
        activePageRequest.current = undefined;
      };
    }, [load]),
  );

  const filters = vendorView ? VENDOR_FILTERS : CUSTOMER_FILTERS;

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <View style={styles.headerCopy}>
          <Text style={styles.eyebrow}>{vendorView ? "VENDOR INBOX" : "MY BOOKINGS"}</Text>
          <Text style={styles.title}>{vendorView ? "Requests & bookings" : "Your celebrations"}</Text>
          <Text style={styles.subtitle}>
            {vendorView
              ? "Reply before the SLA expires and keep every date accurate."
              : "Requests, protected checkout holds, and confirmed plans in one place."}
          </Text>
        </View>
        {vendorView && (
          <Pressable
            accessibilityRole="button"
            style={styles.calendarButton}
            onPress={() => router.push("/(app)/availability" as never)}
          >
            <Text style={styles.calendarGlyph}>▦</Text>
            <Text style={styles.calendarText}>Calendar</Text>
          </Pressable>
        )}
      </View>

      <View style={styles.filters}>
        {filters.map((item) => (
          <Pressable
            key={item.label}
            accessibilityRole="button"
            accessibilityState={{ selected: filter === item.value }}
            style={[styles.filter, filter === item.value && styles.filterActive]}
            onPress={() => selectFilter(item.value)}
          >
            <Text style={[styles.filterText, filter === item.value && styles.filterTextActive]}>
              {item.label}
            </Text>
          </Pressable>
        ))}
      </View>

      {loading && !items.length ? (
        <ActivityIndicator style={styles.loader} color={tokens.colors.brandPink} />
      ) : error && !items.length ? (
        <View style={styles.stateCard}>
          <Text style={styles.stateTitle}>Bookings are unavailable</Text>
          <Text style={styles.stateBody}>{error}</Text>
          <Pressable style={styles.retry} onPress={() => void load()}>
            <Text style={styles.retryText}>Try again</Text>
          </Pressable>
        </View>
      ) : (
        <FlatList
          data={items}
          keyExtractor={(item) => item.id}
          contentContainerStyle={[styles.list, !items.length && styles.emptyList]}
          onEndReached={() => void loadMore()}
          onEndReachedThreshold={0.25}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={() => void load(true)}
              tintColor={tokens.colors.brandPink}
            />
          }
          renderItem={({ item }) => (
            <BookingCard
              booking={item}
              vendorView={vendorView}
              now={now}
              onPress={() => router.push(`/bookings/${item.id}` as never)}
            />
          )}
          ListEmptyComponent={
            <View style={styles.stateCard}>
              <Text style={styles.stateTitle}>
                {vendorView ? "No requests in this view" : "No bookings in this view"}
              </Text>
              <Text style={styles.stateBody}>
                {vendorView
                  ? "New customer requests will appear here with their reply deadline."
                  : "Explore verified vendors and choose a package with a real price."}
              </Text>
              {!vendorView && (
                <Pressable style={styles.retry} onPress={() => router.push("/(app)/explore" as never)}>
                  <Text style={styles.retryText}>Explore vendors</Text>
                </Pressable>
              )}
            </View>
          }
          ListFooterComponent={
            nextCursor || loadingMore || footerError ? (
              <View style={styles.listFooter}>
                {loadingMore && (
                  <>
                    <ActivityIndicator color={tokens.colors.brandPink} />
                    <Text accessibilityLiveRegion="polite" style={styles.footerMessage}>Loading more bookings…</Text>
                  </>
                )}
                {!loadingMore && footerError && (
                  <>
                    <Text accessibilityLiveRegion="polite" style={styles.footerError}>{footerError.message}</Text>
                    <Pressable
                      accessibilityRole="button"
                      style={styles.retry}
                      onPress={() => footerError.retry === "more" ? void loadMore(true) : void load(true)}
                    >
                      <Text style={styles.retryText}>{footerError.retry === "more" ? "Try loading more again" : "Try refreshing again"}</Text>
                    </Pressable>
                  </>
                )}
                {!loadingMore && !footerError && nextCursor && (
                  <Pressable accessibilityRole="button" style={styles.retry} onPress={() => void loadMore()}>
                    <Text style={styles.retryText}>Load more bookings</Text>
                  </Pressable>
                )}
              </View>
            ) : null
          }
        />
      )}
    </SafeAreaView>
  );
}

function uniqueBookings(items: Booking[]): Booking[] {
  return [...new Map(items.map((booking) => [booking.id, booking])).values()];
}

function BookingCard({
  booking,
  vendorView,
  now,
  onPress,
}: {
  booking: Booking;
  vendorView: boolean;
  now: number;
  onPress: () => void;
}) {
  const deadline = timeRemaining(bookingDeadline(booking), now);
  const urgent = deadline != null && deadline !== "Expired" && !deadline.includes("d left");
  return (
    <Pressable style={styles.card} onPress={onPress} accessibilityRole="button">
      <View style={styles.cardTop}>
        <Text style={styles.code}>{booking.code}</Text>
        <View style={[styles.status, urgent && styles.statusUrgent]}>
          <Text style={[styles.statusText, urgent && styles.statusTextUrgent]}>
            {BOOKING_STATUS_LABELS[booking.status] ?? booking.status}
          </Text>
        </View>
      </View>
      <Text style={styles.cardTitle}>{booking.packageTitle}</Text>
      <Text style={styles.cardMeta}>
        {vendorView ? `Customer ${booking.customerId.slice(0, 8)}` : booking.vendorName} · {readableDate(booking.eventDate)}
      </Text>
      <View style={styles.cardBottom}>
        <Text style={styles.price}>{money(booking.price.totalCents, booking.price.currency)}</Text>
        <View style={styles.deadlineRow}>
          {deadline && <Text style={[styles.deadline, urgent && styles.deadlineUrgent]}>{deadline}</Text>}
          <Text style={styles.chevron}>›</Text>
        </View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: tokens.colors.background },
  header: { flexDirection: "row", alignItems: "flex-start", padding: 20, paddingBottom: 12, gap: 12 },
  headerCopy: { flex: 1 },
  eyebrow: { color: tokens.colors.brandPink, fontSize: 10, fontWeight: "800", letterSpacing: 1.5 },
  title: { color: tokens.colors.onSurface, fontSize: 27, fontWeight: "800", marginTop: 4 },
  subtitle: { color: tokens.colors.textMuted, fontSize: 12, lineHeight: 18, marginTop: 5 },
  calendarButton: { minWidth: 68, alignItems: "center", gap: 2, borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.lg, padding: 9, backgroundColor: tokens.colors.surface },
  calendarGlyph: { color: tokens.colors.brandPink, fontSize: 20 },
  calendarText: { color: tokens.colors.onSurface, fontSize: 10, fontWeight: "700" },
  filters: { flexDirection: "row", flexWrap: "wrap", gap: 7, paddingHorizontal: 20, paddingBottom: 12 },
  filter: { borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.pill, paddingHorizontal: 12, paddingVertical: 7, backgroundColor: tokens.colors.surface },
  filterActive: { borderColor: tokens.colors.brandPink, backgroundColor: tokens.colors.pinkSoft },
  filterText: { color: tokens.colors.secondary, fontSize: 11, fontWeight: "700" },
  filterTextActive: { color: tokens.colors.brandPink },
  loader: { flex: 1 },
  list: { padding: 16, paddingBottom: 90, gap: 12 },
  emptyList: { flexGrow: 1, justifyContent: "center" },
  card: { borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.xl, backgroundColor: tokens.colors.surface, padding: 16 },
  cardTop: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 8 },
  code: { color: tokens.colors.textMuted, fontSize: 10, fontWeight: "800", letterSpacing: 1 },
  status: { borderRadius: tokens.radii.pill, paddingHorizontal: 9, paddingVertical: 5, backgroundColor: tokens.colors.surfaceGray },
  statusUrgent: { backgroundColor: "#fff4e5" },
  statusText: { color: tokens.colors.secondary, fontSize: 9, fontWeight: "800" },
  statusTextUrgent: { color: tokens.colors.warning },
  cardTitle: { color: tokens.colors.onSurface, fontSize: 18, fontWeight: "800", marginTop: 12 },
  cardMeta: { color: tokens.colors.textMuted, fontSize: 12, marginTop: 4 },
  cardBottom: { flexDirection: "row", alignItems: "flex-end", justifyContent: "space-between", marginTop: 16 },
  price: { color: tokens.colors.brandPink, fontSize: 18, fontWeight: "800" },
  deadlineRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  deadline: { color: tokens.colors.textMuted, fontSize: 11, fontWeight: "700" },
  deadlineUrgent: { color: tokens.colors.warning },
  chevron: { color: tokens.colors.brandPink, fontSize: 24, lineHeight: 24 },
  stateCard: { margin: 20, borderWidth: 1.5, borderStyle: "dashed", borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.xl, padding: 28, alignItems: "center", backgroundColor: tokens.colors.surface },
  stateTitle: { color: tokens.colors.onSurface, fontSize: 18, fontWeight: "800", textAlign: "center" },
  stateBody: { color: tokens.colors.textMuted, fontSize: 13, lineHeight: 20, textAlign: "center", marginTop: 7 },
  retry: { minHeight: 44, justifyContent: "center", borderRadius: tokens.radii.pill, backgroundColor: tokens.colors.brandPink, paddingHorizontal: 20, marginTop: 16 },
  retryText: { color: tokens.colors.pureWhite, fontSize: 13, fontWeight: "800" },
  listFooter: { alignItems: "center", gap: 8, paddingVertical: 16 },
  footerMessage: { color: tokens.colors.textMuted, fontSize: 12, textAlign: "center" },
  footerError: { color: tokens.colors.danger, fontSize: 12, lineHeight: 18, textAlign: "center" },
});
