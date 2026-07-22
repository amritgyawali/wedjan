import { useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { useRouter } from "expo-router";
import {
  apiErrorMessage,
  type AvailabilityDay,
  type AvailabilityResponse,
  type BookingConfigurationRequest,
  type BookingQuote,
  type VenueLocationMode,
  type VendorPackage,
  type VendorPublic,
} from "@wedjan/shared";
import { tokens } from "@wedjan/ui-tokens";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import {
  addCalendarDays,
  createIdempotencyKey,
  localIsoDate,
  money,
  shortDate,
} from "@/lib/booking-ui";

export function VendorBookingWidget({ vendor }: { vendor: VendorPublic }) {
  const router = useRouter();
  const { status, me, setActiveRole, refreshMe } = useAuth();
  const [selectedPackage, setSelectedPackage] = useState<VendorPackage | undefined>(vendor.packages[0]);
  const [availability, setAvailability] = useState<AvailabilityResponse>();
  const [selectedDate, setSelectedDate] = useState<string>();
  const [selectedSlot, setSelectedSlot] = useState<{ start: string; end: string }>();
  const [guests, setGuests] = useState("");
  const [venueLocationMode, setVenueLocationMode] = useState<VenueLocationMode>("TRAVEL");
  const [venueAddress, setVenueAddress] = useState("");
  const [venueCity, setVenueCity] = useState("");
  const [venueCountry, setVenueCountry] = useState("");
  const [venueLat, setVenueLat] = useState("");
  const [venueLng, setVenueLng] = useState("");
  const [notes, setNotes] = useState("");
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [quote, setQuote] = useState<BookingQuote>();
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();

  useEffect(() => {
    const from = localIsoDate();
    const to = addCalendarDays(from, 60);
    setLoading(true);
    void api.GET("/api/v1/vendors/{slug}/availability", {
      params: { path: { slug: vendor.slug }, query: { from, to } },
    }).then((result) => {
      if (result.data) setAvailability(result.data);
      else setError(apiErrorMessage(result.error, "Could not load availability"));
      setLoading(false);
    });
  }, [vendor.slug]);

  const bookableDays = useMemo(
    () => availability?.days.filter((day) => day.status === "AVAILABLE" || day.status === "LIMITED") ?? [],
    [availability],
  );
  const day = bookableDays.find((item) => item.date === selectedDate);
  const relevantAddOns = (vendor.addOns ?? []).filter(
    (item) => !item.packageId || item.packageId === selectedPackage?.id,
  );

  function resetQuote() {
    setQuote(undefined);
    setError(undefined);
  }

  function chooseDay(next: AvailabilityDay) {
    setSelectedDate(next.date);
    const first = next.slots.find((slot) => slot.status === "AVAILABLE" || slot.status === "LIMITED");
    setSelectedSlot(first ? { start: first.start, end: first.end } : undefined);
    resetQuote();
  }

  function configuration(): BookingConfigurationRequest {
    if (!selectedPackage || !selectedDate || !availability) throw new Error("Choose a package and available date");
    if (availability.mode === "SLOT" && !selectedSlot) throw new Error("Choose an available time slot");
    const guestCount = guests.trim() ? Number(guests) : undefined;
    if (guestCount != null && (!Number.isInteger(guestCount) || guestCount < 1)) {
      throw new Error("Guest count must be a positive whole number");
    }
    const travel = venueLocationMode === "TRAVEL";
    const latitude = Number(venueLat);
    const longitude = Number(venueLng);
    if (travel && !venueAddress.trim()) throw new Error("Enter the event venue address");
    if (travel && !venueCity.trim()) throw new Error("Enter the event venue city");
    if (travel && !/^[A-Za-z]{2}$/.test(venueCountry.trim())) {
      throw new Error("Enter a two-letter venue country code");
    }
    if (travel && (!venueLat.trim() || !Number.isFinite(latitude) || latitude < -90 || latitude > 90
      || !venueLng.trim() || !Number.isFinite(longitude) || longitude < -180 || longitude > 180)) {
      throw new Error("Enter valid venue latitude and longitude");
    }
    return {
      packageId: selectedPackage.id,
      expectedPackageVersion: selectedPackage.version,
      eventDate: selectedDate,
      eventTimezone: availability.timezone,
      startTime: selectedSlot?.start,
      endTime: selectedSlot?.end,
      guests: guestCount,
      venueLocationMode,
      venueAddress: travel ? venueAddress.trim() : undefined,
      venueCity: travel ? venueCity.trim() : undefined,
      venueCountry: travel ? venueCountry.trim().toUpperCase() : undefined,
      venueLat: travel ? latitude : undefined,
      venueLng: travel ? longitude : undefined,
      notes: notes.trim() || undefined,
      addOns: Object.entries(quantities)
        .filter(([, qty]) => qty > 0)
        .map(([addOnId, qty]) => ({ addOnId, qty })),
    };
  }

  async function calculateQuote() {
    setBusy(true);
    setError(undefined);
    try {
      const result = await api.POST("/api/v1/bookings/quote", { body: configuration() });
      if (!result.data) throw new Error(apiErrorMessage(result.error));
      setQuote(result.data);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not calculate price");
    } finally {
      setBusy(false);
    }
  }

  async function addCustomerRole() {
    setBusy(true);
    const result = await api.POST("/api/v1/auth/roles/add", { body: { role: "CUSTOMER" } });
    if (!result.data) {
      Alert.alert("Could not enable booking", apiErrorMessage(result.error));
    } else {
      await refreshMe();
      setActiveRole("CUSTOMER");
    }
    setBusy(false);
  }

  async function createBooking() {
    if (!quote) return;
    setBusy(true);
    setError(undefined);
    try {
      const result = await api.POST("/api/v1/bookings", {
        params: { header: { "Idempotency-Key": createIdempotencyKey() } },
        body: configuration(),
      });
      if (!result.data) throw new Error(apiErrorMessage(result.error));
      router.push(`/bookings/${result.data.id}` as never);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not create booking");
    } finally {
      setBusy(false);
    }
  }

  return (
    <View style={styles.shell}>
      <Text style={styles.eyebrow}>LIVE AVAILABILITY</Text>
      <Text style={styles.title}>Choose your package and date</Text>
      <Text style={styles.subtitle}>Every amount is calculated by wedjan from the vendor’s published terms.</Text>

      <Text style={styles.label}>Package</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.horizontal}>
        {vendor.packages.map((item) => (
          <Pressable
            key={item.id}
            style={[styles.packageChip, selectedPackage?.id === item.id && styles.packageChipActive]}
            onPress={() => {
              setSelectedPackage(item);
              setQuantities({});
              resetQuote();
            }}
          >
            <Text style={[styles.packageName, selectedPackage?.id === item.id && styles.pink]}>{item.title}</Text>
            <Text style={styles.packagePrice}>{money(item.priceCents, item.currency)}</Text>
            <Text style={styles.mode}>{item.bookingMode === "INSTANT" ? "Instant book" : "Request"}</Text>
          </Pressable>
        ))}
      </ScrollView>

      <Text style={styles.label}>Available date</Text>
      {loading ? (
        <ActivityIndicator style={styles.availabilityLoader} color={tokens.colors.brandPink} />
      ) : bookableDays.length ? (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.horizontal}>
          {bookableDays.map((item) => {
            const date = shortDate(item.date);
            return (
              <Pressable key={item.date} style={[styles.dateChip, selectedDate === item.date && styles.dateChipActive]} onPress={() => chooseDay(item)}>
                <Text style={[styles.weekday, selectedDate === item.date && styles.dateTextActive]}>{date.weekday}</Text>
                <Text style={[styles.dateNumber, selectedDate === item.date && styles.dateTextActive]}>{date.day}</Text>
                <Text style={[styles.month, selectedDate === item.date && styles.dateTextActive]}>{date.month}</Text>
                {item.status === "LIMITED" && <Text style={styles.limited}>Limited</Text>}
              </Pressable>
            );
          })}
        </ScrollView>
      ) : (
        <Text style={styles.empty}>No open dates in the next 60 days.</Text>
      )}

      {availability?.mode === "SLOT" && day && (
        <>
          <Text style={styles.label}>Time slot ({availability.timezone})</Text>
          <View style={styles.slotWrap}>
            {day.slots.filter((slot) => slot.status === "AVAILABLE" || slot.status === "LIMITED").map((slot) => {
              const active = selectedSlot?.start === slot.start && selectedSlot.end === slot.end;
              return <Pressable key={`${slot.start}-${slot.end}`} style={[styles.slot, active && styles.slotActive]} onPress={() => { setSelectedSlot({ start: slot.start, end: slot.end }); resetQuote(); }}><Text style={[styles.slotText, active && styles.pink]}>{slot.start}–{slot.end}{slot.label ? ` · ${slot.label}` : ""}</Text></Pressable>;
            })}
          </View>
        </>
      )}

      <Text style={styles.label}>Event location</Text>
      <View style={styles.locationModes}>
        {(["TRAVEL", "VENDOR"] as VenueLocationMode[]).map((mode) => (
          <Pressable
            key={mode}
            style={[styles.locationMode, venueLocationMode === mode && styles.locationModeActive]}
            onPress={() => { setVenueLocationMode(mode); resetQuote(); }}
          >
            <Text style={[styles.locationModeText, venueLocationMode === mode && styles.pink]}>
              {mode === "TRAVEL" ? "Vendor travels to venue" : "At vendor location"}
            </Text>
          </Pressable>
        ))}
      </View>

      {venueLocationMode === "TRAVEL" && (
        <>
          <Text style={styles.label}>Venue address</Text>
          <TextInput style={styles.input} value={venueAddress} onChangeText={(value) => { setVenueAddress(value); resetQuote(); }} placeholder="Street or venue name" />
          <View style={styles.inputRow}>
            <View style={styles.flex}>
              <Text style={styles.label}>Venue city</Text>
              <TextInput style={styles.input} value={venueCity} onChangeText={(value) => { setVenueCity(value); resetQuote(); }} placeholder="Kathmandu" />
            </View>
            <View style={styles.countryField}>
              <Text style={styles.label}>Country</Text>
              <TextInput style={styles.input} value={venueCountry} onChangeText={(value) => { setVenueCountry(value.toUpperCase().slice(0, 2)); resetQuote(); }} autoCapitalize="characters" maxLength={2} placeholder="NP" />
            </View>
          </View>
          <View style={styles.inputRow}>
            <View style={styles.flex}>
              <Text style={styles.label}>Latitude</Text>
              <TextInput style={styles.input} value={venueLat} onChangeText={(value) => { setVenueLat(value); resetQuote(); }} keyboardType="numbers-and-punctuation" placeholder="27.7172" />
            </View>
            <View style={styles.flex}>
              <Text style={styles.label}>Longitude</Text>
              <TextInput style={styles.input} value={venueLng} onChangeText={(value) => { setVenueLng(value); resetQuote(); }} keyboardType="numbers-and-punctuation" placeholder="85.3240" />
            </View>
          </View>
        </>
      )}

      <Text style={styles.label}>Guests</Text>
      <TextInput style={styles.input} value={guests} onChangeText={(value) => { setGuests(value); resetQuote(); }} keyboardType="number-pad" placeholder="Optional" />

      {relevantAddOns.length > 0 && (
        <>
          <Text style={styles.label}>Add-ons</Text>
          {relevantAddOns.map((item) => {
            const qty = quantities[item.id] ?? 0;
            return (
              <View style={styles.addOn} key={item.id}>
                <View style={styles.flex}>
                  <Text style={styles.addOnTitle}>{item.title}</Text>
                  <Text style={styles.addOnPrice}>{money(item.priceCents, vendor.currency)}</Text>
                </View>
                <View style={styles.stepper}>
                  <Pressable style={styles.stepperButton} onPress={() => { setQuantities((current) => ({ ...current, [item.id]: Math.max(0, qty - 1) })); resetQuote(); }}><Text style={styles.stepperText}>−</Text></Pressable>
                  <Text style={styles.qty}>{qty}</Text>
                  <Pressable style={styles.stepperButton} onPress={() => { setQuantities((current) => ({ ...current, [item.id]: Math.min(item.maxQty ?? 99, qty + 1) })); resetQuote(); }}><Text style={styles.stepperText}>+</Text></Pressable>
                </View>
              </View>
            );
          })}
        </>
      )}

      <Text style={styles.label}>Notes for the vendor</Text>
      <TextInput style={[styles.input, styles.notes]} value={notes} onChangeText={(value) => { setNotes(value); resetQuote(); }} multiline textAlignVertical="top" placeholder="Event details, access, timing…" />

      {error && <Text style={styles.error}>{error}</Text>}

      {!quote ? (
        <Pressable style={[styles.primary, (!selectedDate || busy) && styles.disabled]} disabled={!selectedDate || busy} onPress={() => void calculateQuote()}>
          <Text style={styles.primaryText}>{busy ? "Checking…" : "Review exact price"}</Text>
        </Pressable>
      ) : (
        <View style={styles.quote}>
          <View style={styles.quoteTop}><Text style={styles.quoteTitle}>Your exact price</Text><Text style={styles.quoteTotal}>{money(quote.price.totalCents, quote.price.currency)}</Text></View>
          <QuoteRow label="Package" value={money(quote.price.subtotalCents, quote.price.currency)} />
          {!!quote.price.addOnsCents && <QuoteRow label="Add-ons" value={money(quote.price.addOnsCents, quote.price.currency)} />}
          {!!quote.price.travelFeeCents && <QuoteRow label="Travel" value={money(quote.price.travelFeeCents, quote.price.currency)} />}
          <Text style={styles.quotePolicy}>{quote.price.depositPct}% deposit · {quote.price.cancellationPolicy.toLowerCase()} cancellation</Text>

          {status === "anonymous" ? (
            <Pressable style={styles.primary} onPress={() => router.push("/(auth)/login")}><Text style={styles.primaryText}>Log in to continue</Text></Pressable>
          ) : !me?.roles.includes("CUSTOMER") ? (
            <Pressable style={[styles.primary, busy && styles.disabled]} disabled={busy} onPress={() => void addCustomerRole()}><Text style={styles.primaryText}>Enable customer bookings</Text></Pressable>
          ) : (
            <Pressable style={[styles.primary, busy && styles.disabled]} disabled={busy} onPress={() => void createBooking()}>
              <Text style={styles.primaryText}>{busy ? "Creating…" : selectedPackage?.bookingMode === "INSTANT" ? "Start protected checkout" : "Send booking request"}</Text>
            </Pressable>
          )}
          <Pressable style={styles.editButton} onPress={resetQuote}><Text style={styles.editText}>Edit details</Text></Pressable>
        </View>
      )}
    </View>
  );
}

function QuoteRow({ label, value }: { label: string; value: string }) { return <View style={styles.quoteRow}><Text style={styles.quoteLabel}>{label}</Text><Text style={styles.quoteValue}>{value}</Text></View>; }

const styles = StyleSheet.create({
  shell: { margin: 18, borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii["2xl"], padding: 18, backgroundColor: tokens.colors.surface },
  eyebrow: { color: tokens.colors.brandPink, fontSize: 9, fontWeight: "800", letterSpacing: 1.5 }, title: { color: tokens.colors.onSurface, fontSize: 22, fontWeight: "800", marginTop: 4 }, subtitle: { color: tokens.colors.textMuted, fontSize: 12, lineHeight: 18, marginTop: 5 }, label: { color: tokens.colors.onSurface, fontSize: 11, fontWeight: "800", marginTop: 17, marginBottom: 7 }, horizontal: { gap: 8, paddingRight: 8 },
  packageChip: { width: 160, borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.lg, padding: 12, backgroundColor: tokens.colors.surface }, packageChipActive: { borderColor: tokens.colors.brandPink, backgroundColor: tokens.colors.pinkSoft }, packageName: { color: tokens.colors.onSurface, fontSize: 12, fontWeight: "800" }, packagePrice: { color: tokens.colors.onSurface, fontSize: 16, fontWeight: "800", marginTop: 8 }, mode: { color: tokens.colors.textMuted, fontSize: 9, marginTop: 3 }, pink: { color: tokens.colors.brandPink },
  availabilityLoader: { minHeight: 90 }, dateChip: { width: 67, minHeight: 91, alignItems: "center", justifyContent: "center", borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.lg, backgroundColor: tokens.colors.surface }, dateChipActive: { borderColor: tokens.colors.brandPink, backgroundColor: tokens.colors.brandPink }, weekday: { color: tokens.colors.textMuted, fontSize: 9, fontWeight: "700" }, dateNumber: { color: tokens.colors.onSurface, fontSize: 23, fontWeight: "800", marginVertical: 1 }, month: { color: tokens.colors.textMuted, fontSize: 9 }, dateTextActive: { color: tokens.colors.pureWhite }, limited: { color: tokens.colors.warning, fontSize: 7, fontWeight: "800", marginTop: 3 }, empty: { color: tokens.colors.textMuted, fontSize: 12, borderRadius: tokens.radii.md, backgroundColor: tokens.colors.surfaceGray, padding: 15 },
  slotWrap: { flexDirection: "row", flexWrap: "wrap", gap: 7 }, slot: { borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.pill, paddingHorizontal: 11, paddingVertical: 8 }, slotActive: { borderColor: tokens.colors.brandPink, backgroundColor: tokens.colors.pinkSoft }, slotText: { color: tokens.colors.secondary, fontSize: 10, fontWeight: "700" }, inputRow: { flexDirection: "row", gap: 8 }, flex: { flex: 1 }, countryField: { width: 92 }, input: { minHeight: 45, borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.md, paddingHorizontal: 11, backgroundColor: tokens.colors.surface }, notes: { minHeight: 88, paddingTop: 10 },
  locationModes: { flexDirection: "row", gap: 8 }, locationMode: { flex: 1, minHeight: 44, justifyContent: "center", alignItems: "center", borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.md, paddingHorizontal: 8, backgroundColor: tokens.colors.surface }, locationModeActive: { borderColor: tokens.colors.brandPink, backgroundColor: tokens.colors.pinkSoft }, locationModeText: { color: tokens.colors.secondary, fontSize: 10, fontWeight: "700", textAlign: "center" },
  addOn: { flexDirection: "row", alignItems: "center", borderTopWidth: 1, borderTopColor: tokens.colors.surfaceVariant, paddingVertical: 10 }, addOnTitle: { color: tokens.colors.onSurface, fontSize: 12, fontWeight: "700" }, addOnPrice: { color: tokens.colors.textMuted, fontSize: 10, marginTop: 2 }, stepper: { flexDirection: "row", alignItems: "center", gap: 9 }, stepperButton: { width: 32, height: 32, borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: 16, alignItems: "center", justifyContent: "center" }, stepperText: { color: tokens.colors.brandPink, fontSize: 18, fontWeight: "800" }, qty: { minWidth: 16, textAlign: "center", fontSize: 12, fontWeight: "800" },
  error: { color: tokens.colors.danger, fontSize: 11, lineHeight: 17, borderRadius: tokens.radii.md, backgroundColor: tokens.colors.dangerSoft, padding: 10, marginTop: 12 }, primary: { minHeight: 49, alignItems: "center", justifyContent: "center", borderRadius: tokens.radii.pill, backgroundColor: tokens.colors.brandPink, marginTop: 16, paddingHorizontal: 15 }, primaryText: { color: tokens.colors.pureWhite, fontSize: 13, fontWeight: "800" }, disabled: { opacity: .5 },
  quote: { borderRadius: tokens.radii.xl, backgroundColor: tokens.colors.pinkSoft, padding: 16, marginTop: 16 }, quoteTop: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 8 }, quoteTitle: { color: tokens.colors.onSurface, fontSize: 14, fontWeight: "800" }, quoteTotal: { color: tokens.colors.brandPink, fontSize: 23, fontWeight: "800" }, quoteRow: { flexDirection: "row", justifyContent: "space-between", marginTop: 6 }, quoteLabel: { color: tokens.colors.textMuted, fontSize: 10 }, quoteValue: { color: tokens.colors.onSurface, fontSize: 10, fontWeight: "700" }, quotePolicy: { color: tokens.colors.secondary, fontSize: 9, marginTop: 10 }, editButton: { minHeight: 42, alignItems: "center", justifyContent: "center" }, editText: { color: tokens.colors.brandPink, fontSize: 11, fontWeight: "800" },
});
