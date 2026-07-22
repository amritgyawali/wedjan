import { useCallback, useEffect, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Modal,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { Redirect, Stack, useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";
import {
  apiErrorMessage,
  type Booking,
  type BookingAllowedAction,
  type BookingDeclineRequest,
  type RefundCalculation,
} from "@wedjan/shared";
import { tokens } from "@wedjan/ui-tokens";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import {
  BOOKING_STATUS_LABELS,
  addCalendarDays,
  bookingDeadline,
  money,
  readableDate,
  readableTimestamp,
  timeRemaining,
} from "@/lib/booking-ui";

type DeclineReason = BookingDeclineRequest["reason"];
type FormAction = Extract<BookingAllowedAction, "ACCEPT" | "DECLINE" | "CANCEL" | "DISPUTE" | "PROPOSE_RESCHEDULE">;

const DECLINE_REASONS: { value: DeclineReason; label: string }[] = [
  { value: "NOT_AVAILABLE", label: "Not available" },
  { value: "SCOPE_MISMATCH", label: "Scope mismatch" },
  { value: "PRICE_MISMATCH", label: "Price mismatch" },
  { value: "SCHEDULING_CONFLICT", label: "Schedule conflict" },
  { value: "OTHER", label: "Other" },
];

export default function BookingDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { status: authStatus, activeRole } = useAuth();
  const [booking, setBooking] = useState<Booking>();
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [action, setAction] = useState<FormAction>();
  const [refund, setRefund] = useState<RefundCalculation>();
  const [reason, setReason] = useState("");
  const [declineReason, setDeclineReason] = useState<DeclineReason>("NOT_AVAILABLE");
  const [travelFee, setTravelFee] = useState("");
  const [rescheduleDate, setRescheduleDate] = useState("");
  const [rescheduleStart, setRescheduleStart] = useState("");
  const [rescheduleEnd, setRescheduleEnd] = useState("");
  const [rescheduleTimezone, setRescheduleTimezone] = useState("");
  const [loadedAt, setLoadedAt] = useState(Date.now());
  const [tick, setTick] = useState(Date.now());

  useEffect(() => {
    const timer = setInterval(() => setTick(Date.now()), 30_000);
    return () => clearInterval(timer);
  }, []);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(undefined);
    const result = await api.GET("/api/v1/bookings/{id}", { params: { path: { id } } });
    if (result.data) {
      setBooking(result.data);
      setLoadedAt(Date.now());
    } else {
      setError(apiErrorMessage(result.error, "Could not load this booking"));
    }
    setLoading(false);
  }, [id]);

  useFocusEffect(
    useCallback(() => {
      if (authStatus === "authenticated") void load();
    }, [authStatus, load]),
  );

  if (authStatus === "anonymous") return <Redirect href="/(auth)/login" />;

  if (loading && !booking) {
    return <ActivityIndicator style={styles.loader} color={tokens.colors.brandPink} />;
  }

  if (!booking || error) {
    return (
      <SafeAreaView style={styles.safe}>
        <Stack.Screen options={{ headerShown: false }} />
        <View style={styles.errorState}>
          <Text style={styles.sectionTitle}>Booking unavailable</Text>
          <Text style={styles.muted}>{error ?? "This booking could not be found."}</Text>
          <Pressable style={styles.primary} onPress={() => void load()}>
            <Text style={styles.primaryText}>Try again</Text>
          </Pressable>
          <Pressable style={styles.linkButton} onPress={() => router.back()}>
            <Text style={styles.linkText}>Go back</Text>
          </Pressable>
        </View>
      </SafeAreaView>
    );
  }

  const bookingSnapshot: Booking = booking;
  const serverNow = new Date(booking.serverNow).getTime() + (tick - loadedAt);
  const countdown = timeRemaining(bookingDeadline(booking), serverNow);

  async function run(actionCall: () => Promise<Booking | undefined>) {
    setBusy(true);
    try {
      const next = await actionCall();
      if (next) {
        setBooking(next);
        setLoadedAt(Date.now());
        setAction(undefined);
        setRefund(undefined);
        setReason("");
      }
    } catch (cause) {
      Alert.alert("Could not update booking", cause instanceof Error ? cause.message : "Try again");
      await load();
    } finally {
      setBusy(false);
    }
  }

  const updateFrom = (result: Awaited<ReturnType<typeof api.POST>>) => {
    if (!result.data || !("id" in result.data)) throw new Error(apiErrorMessage(result.error));
    return result.data as Booking;
  };

  async function confirmPayment() {
    const booking = bookingSnapshot;
    await run(async () => {
      let current = booking;
      if (current.status === "DRAFT") {
        const checkout = await api.POST("/api/v1/bookings/{id}/checkout", {
          params: { path: { id: current.id } },
          body: { version: current.version },
        });
        current = updateFrom(checkout);
      }
      const confirmed = await api.POST("/api/v1/bookings/{id}/confirm", {
        params: { path: { id: current.id } },
        body: { version: current.version },
      });
      return updateFrom(confirmed);
    });
  }

  async function openAction(next: BookingAllowedAction) {
    const booking = bookingSnapshot;
    if (next === "CONFIRM_PAYMENT_STUB") {
      Alert.alert("Confirm test payment?", "Phase 5 replaces this test checkout with Stripe.", [
        { text: "Not now", style: "cancel" },
        { text: "Confirm", onPress: () => void confirmPayment() },
      ]);
      return;
    }
    if (next === "COMPLETE") {
      Alert.alert("Mark this booking complete?", "The customer will have 48 hours to raise a dispute.", [
        { text: "Not yet", style: "cancel" },
        { text: "Complete", onPress: () => void run(async () => updateFrom(await api.POST("/api/v1/bookings/{id}/complete", { params: { path: { id: booking.id } }, body: { version: booking.version } }))) },
      ]);
      return;
    }
    if (next === "RESPOND_RESCHEDULE") return;
    setReason("");
    setRefund(undefined);
    setTravelFee(booking.price.travelFeeCents ? String(booking.price.travelFeeCents / 100) : "");
    setRescheduleDate(addCalendarDays(booking.eventDate, 7));
    setRescheduleStart(booking.startTime ?? "");
    setRescheduleEnd(booking.endTime ?? "");
    setRescheduleTimezone(booking.eventTimezone);
    if (next === "CANCEL") {
      setBusy(true);
      const result = await api.GET("/api/v1/bookings/{id}/refund-preview", { params: { path: { id: booking.id } } });
      setBusy(false);
      if (!result.data) {
        Alert.alert("Could not calculate refund", apiErrorMessage(result.error));
        return;
      }
      setRefund(result.data);
    }
    setAction(next as FormAction);
  }

  async function submitForm() {
    const booking = bookingSnapshot;
    if (!action) return;
    await run(async () => {
      if (action === "ACCEPT") {
        const result = await api.POST("/api/v1/bookings/{id}/accept", {
          params: { path: { id: booking.id } },
          body: {
            version: booking.version,
            travelFeeCents: travelFee.trim() ? Math.round(Number(travelFee) * 100) : undefined,
          },
        });
        return updateFrom(result);
      }
      if (action === "DECLINE") {
        const result = await api.POST("/api/v1/bookings/{id}/decline", {
          params: { path: { id: booking.id } },
          body: { version: booking.version, reason: declineReason, message: reason || undefined },
        });
        return updateFrom(result);
      }
      if (action === "CANCEL") {
        if (!reason.trim()) throw new Error("Please give a cancellation reason");
        if (!refund) throw new Error("Refresh the refund preview before cancelling");
        const result = await api.POST("/api/v1/bookings/{id}/cancel", {
          params: { path: { id: booking.id } },
          body: {
            version: booking.version,
            reason: reason.trim(),
            refundCalculationId: refund.calculationId,
          },
        });
        return updateFrom(result);
      }
      if (action === "DISPUTE") {
        if (!reason.trim()) throw new Error("Please explain what went wrong");
        const result = await api.POST("/api/v1/bookings/{id}/dispute", {
          params: { path: { id: booking.id } },
          body: { version: booking.version, reason: reason.trim() },
        });
        return updateFrom(result);
      }
      const result = await api.POST("/api/v1/bookings/{id}/reschedule-propose", {
        params: { path: { id: booking.id } },
        body: {
          version: booking.version,
          eventDate: rescheduleDate,
          eventTimezone: rescheduleTimezone.trim(),
          startTime: rescheduleStart || undefined,
          endTime: rescheduleEnd || undefined,
        },
      });
      return updateFrom(result);
    });
  }

  async function respondReschedule(accept: boolean) {
    const booking = bookingSnapshot;
    await run(async () => updateFrom(await api.POST("/api/v1/bookings/{id}/reschedule-confirm", {
      params: { path: { id: booking.id } },
      body: { accept, version: booking.version },
    })));
  }

  return (
    <SafeAreaView style={styles.safe}>
      <Stack.Screen options={{ headerShown: false }} />
      <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
        <View style={styles.topbar}>
          <Pressable style={styles.back} onPress={() => router.back()}><Text style={styles.backText}>‹</Text></Pressable>
          <View style={styles.flex}>
            <Text style={styles.code}>{booking.code}</Text>
            <Text style={styles.title}>{booking.packageTitle}</Text>
          </View>
          {loading && <ActivityIndicator color={tokens.colors.brandPink} />}
        </View>

        <View style={styles.heroCard}>
          <View style={styles.statusRow}>
            <View style={styles.status}><Text style={styles.statusText}>{BOOKING_STATUS_LABELS[booking.status]}</Text></View>
            {countdown && <Text style={styles.countdown}>{countdown}</Text>}
          </View>
          <Text style={styles.vendorName}>{booking.vendorName}</Text>
          <Text style={styles.eventDate}>{readableDate(booking.eventDate)}</Text>
          <Text style={styles.muted}>{booking.venueLocationMode === "VENDOR" ? "At vendor location" : "Vendor travels to venue"}</Text>
          {(booking.venueCity || booking.venueCountry) && <Text style={styles.muted}>{[booking.venueCity, booking.venueCountry].filter(Boolean).join(", ")}</Text>}
          {(booking.startTime || booking.endTime) && <Text style={styles.muted}>{booking.startTime ?? ""}{booking.endTime ? ` – ${booking.endTime}` : ""} · {booking.eventTimezone}</Text>}
          {booking.venueAddress && <Text style={styles.muted}>⌖ {booking.venueAddress}</Text>}
        </View>

        {booking.pendingReschedule && (
          <View style={styles.noticeCard}>
            <Text style={styles.eyebrow}>RESCHEDULE PENDING</Text>
            <Text style={styles.sectionTitle}>{readableDate(booking.pendingReschedule.eventDate)}</Text>
            <Text style={styles.muted}>
              Customer {booking.pendingReschedule.customerApproved ? "approved" : "waiting"} · Vendor {booking.pendingReschedule.vendorApproved ? "approved" : "waiting"}
            </Text>
            {booking.allowedActions.includes("RESPOND_RESCHEDULE") && (
              <View style={styles.actionRow}>
                <Pressable style={[styles.outline, styles.flex]} disabled={busy} onPress={() => void respondReschedule(false)}><Text style={styles.dangerText}>Decline</Text></Pressable>
                <Pressable style={[styles.primary, styles.flex]} disabled={busy} onPress={() => void respondReschedule(true)}><Text style={styles.primaryText}>Accept date</Text></Pressable>
              </View>
            )}
          </View>
        )}

        <View style={styles.card}>
          <Text style={styles.sectionTitle}>Price snapshot</Text>
          <PriceRow label="Package" value={money(booking.price.subtotalCents, booking.price.currency)} />
          {booking.addOns.map((item) => <PriceRow key={item.addOnId} label={`${item.title} × ${item.qty}`} value={money(item.totalCents, booking.price.currency)} />)}
          {!!booking.price.travelFeeCents && <PriceRow label="Travel" value={money(booking.price.travelFeeCents, booking.price.currency)} />}
          {!!booking.price.discountCents && <PriceRow label="Discount" value={`−${money(booking.price.discountCents, booking.price.currency)}`} />}
          <View style={styles.totalRow}><Text style={styles.totalLabel}>Total</Text><Text style={styles.total}>{money(booking.price.totalCents, booking.price.currency)}</Text></View>
          <Text style={styles.policy}>{booking.price.depositPct}% deposit ({money(booking.price.depositCents, booking.price.currency)}) · {booking.price.cancellationPolicy.toLowerCase()} cancellation</Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.sectionTitle}>Event details</Text>
          <Detail label="Booking mode" value={booking.bookingMode === "INSTANT" ? "Instant book" : "Request to book"} />
          {booking.guests != null && <Detail label="Guests" value={String(booking.guests)} />}
          <Detail label="Timezone" value={booking.eventTimezone} />
          {booking.notes && <Detail label="Notes" value={booking.notes} />}
          {booking.cancelReason && <Detail label="Cancellation" value={booking.cancelReason} />}
        </View>

        {booking.allowedActions.length > 0 && (
          <View style={styles.card}>
            <Text style={styles.sectionTitle}>{activeRole === "VENDOR" ? "Respond" : "What happens next"}</Text>
            <View style={styles.actions}>
              {booking.allowedActions.filter((item) => item !== "RESPOND_RESCHEDULE").map((item) => (
                <Pressable
                  key={item}
                  style={[isPrimaryAction(item) ? styles.primary : styles.outline, busy && styles.disabled]}
                  disabled={busy}
                  onPress={() => void openAction(item)}
                >
                  <Text style={isPrimaryAction(item) ? styles.primaryText : item === "CANCEL" || item === "DECLINE" || item === "DISPUTE" ? styles.dangerText : styles.outlineText}>
                    {actionLabel(item)}
                  </Text>
                </Pressable>
              ))}
            </View>
          </View>
        )}

        <View style={styles.card}>
          <Text style={styles.sectionTitle}>Timeline</Text>
          {booking.events.map((event, index) => (
            <View style={styles.event} key={event.id}>
              <View style={styles.eventRail}>
                <View style={styles.eventDot} />
                {index < booking.events.length - 1 && <View style={styles.eventLine} />}
              </View>
              <View style={styles.eventBody}>
                <Text style={styles.eventTitle}>{BOOKING_STATUS_LABELS[event.toStatus] ?? event.toStatus}</Text>
                <Text style={styles.eventMeta}>{event.actor.toLowerCase()} · {readableTimestamp(event.createdAt)}</Text>
              </View>
            </View>
          ))}
        </View>
      </ScrollView>

      <ActionModal
        action={action}
        booking={booking}
        busy={busy}
        refund={refund}
        reason={reason}
        declineReason={declineReason}
        travelFee={travelFee}
        rescheduleDate={rescheduleDate}
        rescheduleStart={rescheduleStart}
        rescheduleEnd={rescheduleEnd}
        rescheduleTimezone={rescheduleTimezone}
        setReason={setReason}
        setDeclineReason={setDeclineReason}
        setTravelFee={setTravelFee}
        setRescheduleDate={setRescheduleDate}
        setRescheduleStart={setRescheduleStart}
        setRescheduleEnd={setRescheduleEnd}
        setRescheduleTimezone={setRescheduleTimezone}
        onClose={() => setAction(undefined)}
        onSubmit={submitForm}
      />
    </SafeAreaView>
  );
}

function ActionModal(props: {
  action?: FormAction;
  booking: Booking;
  busy: boolean;
  refund?: RefundCalculation;
  reason: string;
  declineReason: DeclineReason;
  travelFee: string;
  rescheduleDate: string;
  rescheduleStart: string;
  rescheduleEnd: string;
  rescheduleTimezone: string;
  setReason: (value: string) => void;
  setDeclineReason: (value: DeclineReason) => void;
  setTravelFee: (value: string) => void;
  setRescheduleDate: (value: string) => void;
  setRescheduleStart: (value: string) => void;
  setRescheduleEnd: (value: string) => void;
  setRescheduleTimezone: (value: string) => void;
  onClose: () => void;
  onSubmit: () => void;
}) {
  const { action, booking, busy } = props;
  if (!action) return null;
  return (
    <Modal transparent visible animationType="slide" onRequestClose={props.onClose}>
      <View style={styles.modalBackdrop}>
        <ScrollView style={styles.modalCard} contentContainerStyle={styles.modalContent} keyboardShouldPersistTaps="handled">
          <View style={styles.modalHeader}>
            <View><Text style={styles.eyebrow}>BOOKING ACTION</Text><Text style={styles.sectionTitle}>{modalTitle(action)}</Text></View>
            <Pressable onPress={props.onClose}><Text style={styles.close}>×</Text></Pressable>
          </View>
          {action === "ACCEPT" && <><Text style={styles.muted}>Accepting soft-holds the date for 24 hours while the customer pays. The published base price cannot change.</Text><Text style={styles.label}>Travel fee ({booking.price.currency}, optional)</Text><TextInput style={styles.input} value={props.travelFee} onChangeText={props.setTravelFee} keyboardType="decimal-pad" placeholder="0" /></>}
          {action === "DECLINE" && <><Text style={styles.muted}>A clear reason helps the customer choose an alternative.</Text><View style={styles.reasonGrid}>{DECLINE_REASONS.map((item) => <Pressable key={item.value} style={[styles.reasonChip, props.declineReason === item.value && styles.reasonChipActive]} onPress={() => props.setDeclineReason(item.value)}><Text style={[styles.reasonText, props.declineReason === item.value && styles.reasonTextActive]}>{item.label}</Text></Pressable>)}</View><Text style={styles.label}>Optional message</Text><TextInput style={[styles.input, styles.textarea]} value={props.reason} onChangeText={props.setReason} multiline textAlignVertical="top" /></>}
          {action === "CANCEL" && <><View style={styles.refundCard}><Text style={styles.refundPercent}>{props.refund?.refundPercent ?? 0}% refund</Text><Text style={styles.refundAmount}>{money(props.refund?.refundableCents ?? 0, booking.price.currency)}</Text><Text style={styles.muted}>{props.refund?.policy.toLowerCase()} policy · {props.refund?.daysToEvent} days before event</Text>{props.refund?.vendorPenalty && <Text style={styles.dangerText}>Vendor cancellation carries a trust penalty.</Text>}</View><Text style={styles.label}>Why are you cancelling?</Text><TextInput style={[styles.input, styles.textarea]} value={props.reason} onChangeText={props.setReason} multiline textAlignVertical="top" /></>}
          {action === "DISPUTE" && <><Text style={styles.muted}>Describe the issue for the operations team. Raising a dispute freezes release while it is reviewed.</Text><Text style={styles.label}>What went wrong?</Text><TextInput style={[styles.input, styles.textarea]} value={props.reason} onChangeText={props.setReason} multiline textAlignVertical="top" /></>}
          {action === "PROPOSE_RESCHEDULE" && <><Text style={styles.muted}>One mutual reschedule is allowed at least 14 days before the event. The date is rechecked after both parties approve.</Text><Text style={styles.label}>New date (YYYY-MM-DD)</Text><TextInput style={styles.input} value={props.rescheduleDate} onChangeText={props.setRescheduleDate} keyboardType="numbers-and-punctuation" /><View style={styles.actionRow}><View style={styles.flex}><Text style={styles.label}>Start</Text><TextInput style={styles.input} value={props.rescheduleStart} onChangeText={props.setRescheduleStart} placeholder="09:00" /></View><View style={styles.flex}><Text style={styles.label}>End</Text><TextInput style={styles.input} value={props.rescheduleEnd} onChangeText={props.setRescheduleEnd} placeholder="17:00" /></View></View><Text style={styles.label}>Timezone</Text><TextInput style={styles.input} value={props.rescheduleTimezone} onChangeText={props.setRescheduleTimezone} autoCapitalize="none" /></>}
          <Pressable style={[styles.primary, busy && styles.disabled]} disabled={busy} onPress={props.onSubmit}><Text style={styles.primaryText}>{busy ? "Updating…" : modalSubmitLabel(action)}</Text></Pressable>
        </ScrollView>
      </View>
    </Modal>
  );
}

function PriceRow({ label, value }: { label: string; value: string }) { return <View style={styles.priceRow}><Text style={styles.muted}>{label}</Text><Text style={styles.priceValue}>{value}</Text></View>; }
function Detail({ label, value }: { label: string; value: string }) { return <View style={styles.detail}><Text style={styles.detailLabel}>{label}</Text><Text style={styles.detailValue}>{value}</Text></View>; }
function isPrimaryAction(action: BookingAllowedAction) { return action === "ACCEPT" || action === "CONFIRM_PAYMENT_STUB" || action === "COMPLETE"; }
function actionLabel(action: BookingAllowedAction) { return ({ CONFIRM_PAYMENT_STUB: "Continue to payment", ACCEPT: "Accept request", DECLINE: "Decline request", CANCEL: "Cancel booking", COMPLETE: "Mark complete", DISPUTE: "Raise dispute", PROPOSE_RESCHEDULE: "Propose new date", RESPOND_RESCHEDULE: "Respond to new date" } satisfies Record<BookingAllowedAction, string>)[action]; }
function modalTitle(action: FormAction) { return ({ ACCEPT: "Accept request", DECLINE: "Decline request", CANCEL: "Review cancellation", DISPUTE: "Raise a dispute", PROPOSE_RESCHEDULE: "Propose a new date" } satisfies Record<FormAction, string>)[action]; }
function modalSubmitLabel(action: FormAction) { return ({ ACCEPT: "Accept & hold date", DECLINE: "Decline request", CANCEL: "Confirm cancellation", DISPUTE: "Submit dispute", PROPOSE_RESCHEDULE: "Send proposal" } satisfies Record<FormAction, string>)[action]; }

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: tokens.colors.background }, screen: { flex: 1 }, content: { padding: 16, paddingBottom: 70, gap: 13 }, loader: { flex: 1, backgroundColor: tokens.colors.background },
  topbar: { flexDirection: "row", alignItems: "center", gap: 12, paddingVertical: 5 }, back: { width: 42, height: 42, borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: 21, alignItems: "center", justifyContent: "center", backgroundColor: tokens.colors.surface }, backText: { fontSize: 30, lineHeight: 32 }, flex: { flex: 1 }, code: { color: tokens.colors.brandPink, fontSize: 10, fontWeight: "800", letterSpacing: 1 }, title: { color: tokens.colors.onSurface, fontSize: 21, fontWeight: "800" },
  heroCard: { borderRadius: tokens.radii.xl, padding: 20, backgroundColor: tokens.colors.primaryDeep }, statusRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" }, status: { borderRadius: tokens.radii.pill, paddingHorizontal: 10, paddingVertical: 6, backgroundColor: "rgba(255,255,255,.16)" }, statusText: { color: tokens.colors.pureWhite, fontSize: 10, fontWeight: "800" }, countdown: { color: "#ffd8e7", fontSize: 11, fontWeight: "800" }, vendorName: { color: tokens.colors.pureWhite, fontSize: 14, fontWeight: "700", marginTop: 17 }, eventDate: { color: tokens.colors.pureWhite, fontSize: 26, fontWeight: "800", marginTop: 3 },
  card: { borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.xl, padding: 17, backgroundColor: tokens.colors.surface }, noticeCard: { borderWidth: 1, borderColor: tokens.colors.brandPink, borderRadius: tokens.radii.xl, padding: 17, backgroundColor: tokens.colors.pinkSoft }, eyebrow: { color: tokens.colors.brandPink, fontSize: 9, fontWeight: "800", letterSpacing: 1.4 }, sectionTitle: { color: tokens.colors.onSurface, fontSize: 18, fontWeight: "800" }, muted: { color: tokens.colors.textMuted, fontSize: 12, lineHeight: 18, marginTop: 4 },
  priceRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginTop: 11 }, priceValue: { color: tokens.colors.onSurface, fontSize: 12, fontWeight: "700" }, totalRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", borderTopWidth: 1, borderTopColor: tokens.colors.surfaceVariant, marginTop: 14, paddingTop: 14 }, totalLabel: { fontSize: 15, fontWeight: "800" }, total: { color: tokens.colors.brandPink, fontSize: 22, fontWeight: "800" }, policy: { color: tokens.colors.textMuted, fontSize: 10, marginTop: 7 },
  detail: { borderTopWidth: 1, borderTopColor: tokens.colors.surfaceVariant, paddingTop: 11, marginTop: 11 }, detailLabel: { color: tokens.colors.textMuted, fontSize: 9, fontWeight: "800", textTransform: "uppercase", letterSpacing: 1 }, detailValue: { color: tokens.colors.onSurface, fontSize: 13, lineHeight: 19, marginTop: 3 }, actions: { gap: 9 }, primary: { minHeight: 48, alignItems: "center", justifyContent: "center", borderRadius: tokens.radii.pill, backgroundColor: tokens.colors.brandPink, paddingHorizontal: 17, marginTop: 11 }, primaryText: { color: tokens.colors.pureWhite, fontSize: 13, fontWeight: "800" }, outline: { minHeight: 47, alignItems: "center", justifyContent: "center", borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.pill, paddingHorizontal: 17, marginTop: 9 }, outlineText: { color: tokens.colors.brandPink, fontSize: 13, fontWeight: "800" }, dangerText: { color: tokens.colors.danger, fontSize: 12, fontWeight: "800" }, disabled: { opacity: .5 },
  actionRow: { flexDirection: "row", gap: 9 }, event: { flexDirection: "row", minHeight: 56, marginTop: 8 }, eventRail: { width: 22, alignItems: "center" }, eventDot: { width: 10, height: 10, borderRadius: 5, marginTop: 5, backgroundColor: tokens.colors.brandPink }, eventLine: { width: 1, flex: 1, backgroundColor: tokens.colors.surfaceVariant }, eventBody: { flex: 1, paddingBottom: 9 }, eventTitle: { color: tokens.colors.onSurface, fontSize: 13, fontWeight: "700" }, eventMeta: { color: tokens.colors.textMuted, fontSize: 10, marginTop: 3 },
  errorState: { flex: 1, alignItems: "center", justifyContent: "center", padding: 30 }, linkButton: { minHeight: 44, justifyContent: "center" }, linkText: { color: tokens.colors.brandPink, fontWeight: "700" }, modalBackdrop: { flex: 1, justifyContent: "flex-end", backgroundColor: "rgba(0,0,0,.4)" }, modalCard: { maxHeight: "86%", borderTopLeftRadius: tokens.radii["2xl"], borderTopRightRadius: tokens.radii["2xl"], backgroundColor: tokens.colors.surface }, modalContent: { padding: 22, paddingBottom: 46 }, modalHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" }, close: { color: tokens.colors.textMuted, fontSize: 30, padding: 5 }, label: { color: tokens.colors.onSurface, fontSize: 12, fontWeight: "700", marginTop: 16, marginBottom: 6 }, input: { minHeight: 47, borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.md, paddingHorizontal: 12, backgroundColor: tokens.colors.surface }, textarea: { minHeight: 105, paddingTop: 11 }, reasonGrid: { flexDirection: "row", flexWrap: "wrap", gap: 7, marginTop: 14 }, reasonChip: { borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.pill, paddingHorizontal: 11, paddingVertical: 8 }, reasonChipActive: { borderColor: tokens.colors.brandPink, backgroundColor: tokens.colors.pinkSoft }, reasonText: { color: tokens.colors.secondary, fontSize: 10, fontWeight: "700" }, reasonTextActive: { color: tokens.colors.brandPink }, refundCard: { borderRadius: tokens.radii.xl, padding: 18, backgroundColor: tokens.colors.pinkSoft, alignItems: "center", marginTop: 15 }, refundPercent: { color: tokens.colors.brandPink, fontSize: 14, fontWeight: "800" }, refundAmount: { color: tokens.colors.onSurface, fontSize: 29, fontWeight: "800", marginTop: 3 },
});
