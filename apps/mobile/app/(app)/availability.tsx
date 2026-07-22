import { useCallback, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Linking,
  Modal,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { Stack, useFocusEffect, useRouter } from "expo-router";
import {
  apiErrorMessage,
  type AvailabilityDay,
  type AvailabilityException,
  type AvailabilityExceptionRequest,
  type AvailabilityMode,
  type AvailabilitySettings,
  type CalendarExport,
  type ExternalCalendar,
} from "@wedjan/shared";
import { tokens } from "@wedjan/ui-tokens";
import { api } from "@/lib/api";
import {
  AVAILABILITY_STATUS_LABELS,
  addCalendarDays,
  localIsoDate,
  readableTimestamp,
} from "@/lib/booking-ui";

type SlotDraft = { start: string; end: string; label: string };
type RuleDraft = {
  weekday: number;
  isAvailable: boolean;
  jobsPerDay: string;
  slots: SlotDraft[];
};
type ExceptionDraft = AvailabilityExceptionRequest & { id?: string };

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const DEFAULT_SLOT: SlotDraft = { start: "09:00", end: "17:00", label: "Daytime" };

export default function AvailabilityScreen() {
  const router = useRouter();
  const [settings, setSettings] = useState<AvailabilitySettings>();
  const [mode, setMode] = useState<AvailabilityMode>("DATE");
  const [timezone, setTimezone] = useState("Asia/Kathmandu");
  const [rules, setRules] = useState<RuleDraft[]>(defaultRules());
  const [anchor, setAnchor] = useState(() => localIsoDate(new Date()).slice(0, 7) + "-01");
  const [days, setDays] = useState<AvailabilityDay[]>([]);
  const [calendars, setCalendars] = useState<ExternalCalendar[]>([]);
  const [calendarExport, setCalendarExport] = useState<CalendarExport>();
  const [icsUrl, setIcsUrl] = useState("");
  const [exception, setException] = useState<ExceptionDraft>();
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const window = useMemo(() => monthWindow(anchor), [anchor]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    const [settingsResult, calendarResult, calendarsResult, exportResult] = await Promise.all([
      api.GET("/api/v1/vendors/me/availability"),
      api.GET("/api/v1/vendors/me/calendar", {
        params: { query: { from: window.from, to: window.to } },
      }),
      api.GET("/api/v1/vendors/me/calendars"),
      api.GET("/api/v1/vendors/me/calendar-export"),
    ]);
    if (!settingsResult.data) {
      setError(apiErrorMessage(settingsResult.error, "Could not load availability"));
    } else {
      applySettings(settingsResult.data);
      setSettings(settingsResult.data);
    }
    if (calendarResult.data) setDays(calendarResult.data.days);
    if (calendarsResult.data) setCalendars(calendarsResult.data);
    if (exportResult.data) setCalendarExport(exportResult.data);
    setLoading(false);
  }, [window.from, window.to]);

  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  function applySettings(value: AvailabilitySettings) {
    setMode(value.mode);
    setTimezone(value.timezone);
    const byDay = new Map(value.rules.map((rule) => [rule.weekday, rule]));
    setRules(
      WEEKDAYS.map((_, weekday) => {
        const rule = byDay.get(weekday);
        return {
          weekday,
          isAvailable: rule?.isAvailable ?? (weekday > 0 && weekday < 6),
          jobsPerDay: String(rule?.jobsPerDay ?? 1),
          slots: rule?.slots.length
            ? rule.slots.map((slot) => ({ start: slot.start, end: slot.end, label: slot.label ?? "" }))
            : [{ ...DEFAULT_SLOT }],
        };
      }),
    );
  }

  async function run(action: () => Promise<void>) {
    setBusy(true);
    try {
      await action();
    } catch (cause) {
      Alert.alert("Could not update calendar", cause instanceof Error ? cause.message : "Try again");
    } finally {
      setBusy(false);
    }
  }

  const saveSettings = () =>
    run(async () => {
      const body = {
        mode,
        timezone: timezone.trim(),
        rules: rules.map((rule) => ({
          weekday: rule.weekday,
          isAvailable: rule.isAvailable,
          jobsPerDay: Math.max(1, Number(rule.jobsPerDay) || 1),
          slots: mode === "SLOT" && rule.isAvailable ? rule.slots : [],
        })),
      };
      const result = await api.PUT("/api/v1/vendors/me/availability", { body });
      if (!result.data) throw new Error(apiErrorMessage(result.error));
      setSettings(result.data);
      applySettings(result.data);
      await refreshCalendar();
      Alert.alert("Availability saved", "Customers now see these weekly rules.");
    });

  async function refreshCalendar() {
    const result = await api.GET("/api/v1/vendors/me/calendar", {
      params: { query: { from: window.from, to: window.to } },
    });
    if (!result.data) throw new Error(apiErrorMessage(result.error));
    setDays(result.data.days);
  }

  function openDate(date: string) {
    const existing = settings?.exceptions.find((item) => item.date === date && item.source === "MANUAL");
    setException({
      id: existing?.id,
      date,
      type: existing?.type ?? "BLACKOUT",
      slots: existing?.slots.length
        ? existing.slots
        : mode === "SLOT"
          ? [{ ...DEFAULT_SLOT }]
          : [],
      note: existing?.note ?? "",
    });
  }

  const saveException = () =>
    exception &&
    run(async () => {
      const body: AvailabilityExceptionRequest = {
        date: exception.date,
        type: exception.type,
        note: exception.note || undefined,
        slots: exception.type === "CUSTOM_SLOTS" ? exception.slots : [],
      };
      const result = exception.id
        ? await api.PATCH("/api/v1/vendors/me/availability/exceptions/{id}", {
            params: { path: { id: exception.id } },
            body,
          })
        : await api.POST("/api/v1/vendors/me/availability/exceptions", { body });
      if (!result.data) throw new Error(apiErrorMessage(result.error));
      setException(undefined);
      await load();
    });

  const deleteException = () =>
    exception?.id &&
    run(async () => {
      const result = await api.DELETE("/api/v1/vendors/me/availability/exceptions/{id}", {
        params: { path: { id: exception.id! } },
      });
      if (!result.response.ok) throw new Error(apiErrorMessage(result.error));
      setException(undefined);
      await load();
    });

  const connectCalendar = () =>
    run(async () => {
      const result = await api.POST("/api/v1/vendors/me/calendars", {
        body: { icsUrl: icsUrl.trim() },
      });
      if (!result.data) throw new Error(apiErrorMessage(result.error));
      setIcsUrl("");
      await load();
    });

  if (loading && !settings) {
    return <ActivityIndicator style={styles.loader} color={tokens.colors.brandPink} />;
  }

  if (error && !settings) {
    return (
      <SafeAreaView style={styles.safe}>
        <Stack.Screen options={{ headerShown: false }} />
        <View style={styles.errorState}>
          <Text style={styles.sectionTitle}>Calendar unavailable</Text>
          <Text style={styles.muted}>{error}</Text>
          <Pressable style={styles.primary} onPress={() => void load()}>
            <Text style={styles.primaryText}>Try again</Text>
          </Pressable>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <Stack.Screen options={{ headerShown: false }} />
      <ScrollView style={styles.screen} contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.topbar}>
          <Pressable style={styles.back} onPress={() => router.back()}>
            <Text style={styles.backText}>‹</Text>
          </Pressable>
          <View style={styles.flex}>
            <Text style={styles.eyebrow}>VENDOR CALENDAR</Text>
            <Text style={styles.title}>Availability</Text>
          </View>
          {loading && <ActivityIndicator color={tokens.colors.brandPink} />}
        </View>

        <View style={styles.card}>
          <Text style={styles.sectionTitle}>How customers book time</Text>
          <Text style={styles.muted}>Whole dates suit event teams; slots suit venues and studios.</Text>
          <View style={styles.segment}>
            {(["DATE", "SLOT"] as const).map((value) => (
              <Pressable
                key={value}
                style={[styles.segmentItem, mode === value && styles.segmentActive]}
                onPress={() => setMode(value)}
              >
                <Text style={[styles.segmentText, mode === value && styles.segmentTextActive]}>
                  {value === "DATE" ? "Whole dates" : "Time slots"}
                </Text>
              </Pressable>
            ))}
          </View>
          <Text style={styles.label}>Venue timezone</Text>
          <TextInput
            style={styles.input}
            value={timezone}
            onChangeText={setTimezone}
            autoCapitalize="none"
            autoCorrect={false}
            placeholder="Asia/Kathmandu"
          />
        </View>

        <View style={styles.card}>
          <Text style={styles.sectionTitle}>Weekly rhythm</Text>
          <Text style={styles.muted}>Tap a day to open or close it. Times use {timezone || "your timezone"}.</Text>
          {rules.map((rule) => (
            <RuleEditor
              key={rule.weekday}
              mode={mode}
              rule={rule}
              onChange={(next) => setRules((current) => current.map((item) => item.weekday === next.weekday ? next : item))}
            />
          ))}
          <Pressable style={[styles.primary, busy && styles.disabled]} disabled={busy} onPress={saveSettings}>
            <Text style={styles.primaryText}>{busy ? "Saving…" : "Save weekly availability"}</Text>
          </Pressable>
        </View>

        <View style={styles.card}>
          <View style={styles.monthHeader}>
            <Pressable style={styles.monthButton} onPress={() => setAnchor(moveMonth(anchor, -1))}>
              <Text style={styles.monthButtonText}>‹</Text>
            </Pressable>
            <Text style={styles.sectionTitle}>{monthTitle(anchor)}</Text>
            <Pressable style={styles.monthButton} onPress={() => setAnchor(moveMonth(anchor, 1))}>
              <Text style={styles.monthButtonText}>›</Text>
            </Pressable>
          </View>
          <Text style={styles.muted}>Tap a date to add a blackout, extra opening, or custom slots.</Text>
          <View style={styles.weekHeader}>
            {WEEKDAYS.map((day) => <Text style={styles.weekday} key={day}>{day.slice(0, 1)}</Text>)}
          </View>
          <View style={styles.monthGrid}>
            {window.cells.map((date) => (
              <CalendarCell
                key={date}
                date={date}
                currentMonth={date.slice(0, 7) === anchor.slice(0, 7)}
                day={days.find((item) => item.date === date)}
                hasManualException={settings?.exceptions.some((item) => item.date === date && item.source === "MANUAL") ?? false}
                onPress={() => openDate(date)}
              />
            ))}
          </View>
          <View style={styles.legend}>
            {(["AVAILABLE", "LIMITED", "BOOKED", "BLACKED_OUT"] as const).map((status) => (
              <View style={styles.legendItem} key={status}>
                <View style={[styles.legendDot, statusColor(status)]} />
                <Text style={styles.legendText}>{AVAILABILITY_STATUS_LABELS[status]}</Text>
              </View>
            ))}
          </View>
        </View>

        <View style={styles.card}>
          <Text style={styles.sectionTitle}>Calendar sync</Text>
          <Text style={styles.muted}>Paste a private iCal URL. Busy dates import hourly and stay private.</Text>
          <TextInput
            style={styles.input}
            value={icsUrl}
            onChangeText={setIcsUrl}
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
            placeholder="https://calendar.example/private.ics"
          />
          <Pressable
            style={[styles.outline, (!icsUrl.trim() || busy) && styles.disabled]}
            disabled={!icsUrl.trim() || busy}
            onPress={connectCalendar}
          >
            <Text style={styles.outlineText}>Connect calendar</Text>
          </Pressable>
          {calendars.map((calendar) => (
            <ExternalCalendarRow key={calendar.id} calendar={calendar} busy={busy} reload={load} run={run} />
          ))}
        </View>

        {calendarExport && (
          <View style={styles.card}>
            <Text style={styles.sectionTitle}>Export confirmed bookings</Text>
            <Text style={styles.muted}>Treat this secret link like a password. Rotate it if it is shared accidentally.</Text>
            <Text selectable style={styles.exportUrl}>{calendarExport.url}</Text>
            <View style={styles.buttonRow}>
              <Pressable style={[styles.outline, styles.flex]} onPress={() => void Linking.openURL(calendarExport.url)}>
                <Text style={styles.outlineText}>Open feed</Text>
              </Pressable>
              <Pressable
                style={[styles.outline, styles.flex]}
                onPress={() =>
                  Alert.alert("Rotate secret link?", "The old calendar URL will stop working.", [
                    { text: "Keep it", style: "cancel" },
                    {
                      text: "Rotate",
                      style: "destructive",
                      onPress: () => void run(async () => {
                        const result = await api.POST("/api/v1/vendors/me/calendar-export/rotate", {});
                        if (!result.data) throw new Error(apiErrorMessage(result.error));
                        setCalendarExport(result.data);
                      }),
                    },
                  ])
                }
              >
                <Text style={styles.dangerText}>Rotate link</Text>
              </Pressable>
            </View>
          </View>
        )}
      </ScrollView>

      <ExceptionModal
        value={exception}
        mode={mode}
        busy={busy}
        onChange={setException}
        onClose={() => setException(undefined)}
        onSave={saveException}
        onDelete={deleteException}
      />
    </SafeAreaView>
  );
}

function RuleEditor({ mode, rule, onChange }: { mode: AvailabilityMode; rule: RuleDraft; onChange: (value: RuleDraft) => void }) {
  const updateSlot = (index: number, patch: Partial<SlotDraft>) => onChange({
    ...rule,
    slots: rule.slots.map((slot, slotIndex) => slotIndex === index ? { ...slot, ...patch } : slot),
  });
  return (
    <View style={styles.rule}>
      <Pressable
        style={[styles.dayToggle, rule.isAvailable && styles.dayToggleActive]}
        onPress={() => onChange({ ...rule, isAvailable: !rule.isAvailable })}
      >
        <Text style={[styles.dayText, rule.isAvailable && styles.dayTextActive]}>{WEEKDAYS[rule.weekday]}</Text>
      </Pressable>
      <View style={styles.ruleBody}>
        {!rule.isAvailable ? (
          <Text style={styles.closed}>Closed</Text>
        ) : mode === "DATE" ? (
          <View style={styles.capacityRow}>
            <Text style={styles.ruleHint}>Jobs per day</Text>
            <TextInput
              style={styles.smallInput}
              value={rule.jobsPerDay}
              onChangeText={(jobsPerDay) => onChange({ ...rule, jobsPerDay })}
              keyboardType="number-pad"
            />
          </View>
        ) : (
          <View style={styles.slotList}>
            {rule.slots.map((slot, index) => (
              <View style={styles.slotRow} key={`${rule.weekday}-${index}`}>
                <TextInput style={styles.timeInput} value={slot.start} onChangeText={(start) => updateSlot(index, { start })} placeholder="09:00" />
                <Text style={styles.slotDash}>–</Text>
                <TextInput style={styles.timeInput} value={slot.end} onChangeText={(end) => updateSlot(index, { end })} placeholder="17:00" />
                <TextInput style={styles.slotLabel} value={slot.label} onChangeText={(label) => updateSlot(index, { label })} placeholder="Label" />
                {rule.slots.length > 1 && (
                  <Pressable onPress={() => onChange({ ...rule, slots: rule.slots.filter((_, slotIndex) => slotIndex !== index) })}>
                    <Text style={styles.remove}>×</Text>
                  </Pressable>
                )}
              </View>
            ))}
            <Pressable style={styles.addSlot} onPress={() => onChange({ ...rule, slots: [...rule.slots, { ...DEFAULT_SLOT, label: "" }] })}>
              <Text style={styles.addSlotText}>+ Add slot</Text>
            </Pressable>
          </View>
        )}
      </View>
    </View>
  );
}

function CalendarCell({ date, currentMonth, day, hasManualException, onPress }: { date: string; currentMonth: boolean; day?: AvailabilityDay; hasManualException: boolean; onPress: () => void }) {
  return (
    <Pressable style={[styles.calendarCell, day && statusColor(day.status)]} onPress={onPress}>
      <Text style={[styles.calendarDay, !currentMonth && styles.calendarDayMuted]}>{Number(date.slice(-2))}</Text>
      {day && day.capacity > 0 && <Text style={styles.capacity}>{day.occupied}/{day.capacity}</Text>}
      {hasManualException && <View style={styles.exceptionMark} />}
    </Pressable>
  );
}

function ExceptionModal({ value, mode, busy, onChange, onClose, onSave, onDelete }: { value?: ExceptionDraft; mode: AvailabilityMode; busy: boolean; onChange: (value?: ExceptionDraft) => void; onClose: () => void; onSave: () => void; onDelete: () => void }) {
  if (!value) return null;
  const types = mode === "SLOT" ? (["BLACKOUT", "EXTRA_OPEN", "CUSTOM_SLOTS"] as const) : (["BLACKOUT", "EXTRA_OPEN"] as const);
  return (
    <Modal visible transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.modalBackdrop}>
        <ScrollView style={styles.modalCard} contentContainerStyle={styles.modalContent} keyboardShouldPersistTaps="handled">
          <View style={styles.modalHeader}>
            <View>
              <Text style={styles.eyebrow}>DATE OVERRIDE</Text>
              <Text style={styles.sectionTitle}>{value.date}</Text>
            </View>
            <Pressable onPress={onClose}><Text style={styles.close}>×</Text></Pressable>
          </View>
          <View style={styles.segment}>
            {types.map((type) => (
              <Pressable key={type} style={[styles.segmentItem, value.type === type && styles.segmentActive]} onPress={() => onChange({ ...value, type, slots: type === "CUSTOM_SLOTS" && !value.slots?.length ? [{ ...DEFAULT_SLOT }] : [] })}>
                <Text style={[styles.segmentText, value.type === type && styles.segmentTextActive]}>{type.replaceAll("_", " ")}</Text>
              </Pressable>
            ))}
          </View>
          {value.type === "CUSTOM_SLOTS" && (value.slots ?? []).map((slot, index) => (
            <View style={styles.slotRow} key={index}>
              <TextInput style={styles.timeInput} value={slot.start} onChangeText={(start) => onChange({ ...value, slots: value.slots!.map((item, i) => i === index ? { ...item, start } : item) })} />
              <Text>–</Text>
              <TextInput style={styles.timeInput} value={slot.end} onChangeText={(end) => onChange({ ...value, slots: value.slots!.map((item, i) => i === index ? { ...item, end } : item) })} />
              <TextInput style={styles.slotLabel} value={slot.label ?? ""} onChangeText={(label) => onChange({ ...value, slots: value.slots!.map((item, i) => i === index ? { ...item, label } : item) })} placeholder="Label" />
            </View>
          ))}
          <Text style={styles.label}>Private note</Text>
          <TextInput style={[styles.input, styles.noteInput]} value={value.note ?? ""} onChangeText={(note) => onChange({ ...value, note })} multiline textAlignVertical="top" />
          <Pressable style={[styles.primary, busy && styles.disabled]} disabled={busy} onPress={onSave}>
            <Text style={styles.primaryText}>{busy ? "Saving…" : "Save date override"}</Text>
          </Pressable>
          {value.id && (
            <Pressable style={styles.deleteButton} disabled={busy} onPress={onDelete}>
              <Text style={styles.dangerText}>Remove override</Text>
            </Pressable>
          )}
        </ScrollView>
      </View>
    </Modal>
  );
}

function ExternalCalendarRow({ calendar, busy, reload, run }: { calendar: ExternalCalendar; busy: boolean; reload: () => Promise<void>; run: (action: () => Promise<void>) => Promise<void> }) {
  const degraded = calendar.syncStatus === "DEGRADED";
  return (
    <View style={styles.externalRow}>
      <View style={styles.flex}>
        <Text style={styles.externalUrl} numberOfLines={1}>{calendar.icsUrl}</Text>
        <Text style={[styles.syncState, degraded && styles.dangerText]}>
          {calendar.syncStatus} · {readableTimestamp(calendar.lastSyncedAt) ?? "Not synced yet"}
        </Text>
        {calendar.lastError && <Text style={styles.errorCopy}>{calendar.lastError}</Text>}
      </View>
      <View style={styles.externalActions}>
        <Pressable disabled={busy} onPress={() => void run(async () => {
          const result = await api.POST("/api/v1/vendors/me/calendars/{id}/sync", { params: { path: { id: calendar.id } } });
          if (!result.data) throw new Error(apiErrorMessage(result.error));
          await reload();
        })}><Text style={styles.outlineText}>Sync</Text></Pressable>
        <Pressable disabled={busy} onPress={() => Alert.alert("Disconnect calendar?", "Imported blackouts from this feed will be removed.", [
          { text: "Keep", style: "cancel" },
          { text: "Disconnect", style: "destructive", onPress: () => void run(async () => {
            const result = await api.DELETE("/api/v1/vendors/me/calendars/{id}", { params: { path: { id: calendar.id } } });
            if (!result.response.ok) throw new Error(apiErrorMessage(result.error));
            await reload();
          }) },
        ])}><Text style={styles.dangerText}>Remove</Text></Pressable>
      </View>
    </View>
  );
}

function defaultRules(): RuleDraft[] {
  return WEEKDAYS.map((_, weekday) => ({ weekday, isAvailable: weekday > 0 && weekday < 6, jobsPerDay: "1", slots: [{ ...DEFAULT_SLOT }] }));
}

function monthWindow(anchor: string): { from: string; to: string; cells: string[] } {
  const [year, month] = anchor.split("-").map(Number);
  const first = new Date(year, month - 1, 1);
  const start = new Date(year, month - 1, 1 - first.getDay());
  const from = localIsoDate(start);
  const cells = Array.from({ length: 42 }, (_, index) => addCalendarDays(from, index));
  return { from, to: cells[41], cells };
}

function moveMonth(anchor: string, amount: number): string {
  const [year, month] = anchor.split("-").map(Number);
  return localIsoDate(new Date(year, month - 1 + amount, 1));
}

function monthTitle(anchor: string): string {
  const [year, month] = anchor.split("-").map(Number);
  return new Intl.DateTimeFormat("en", { month: "long", year: "numeric" }).format(new Date(year, month - 1, 1));
}

function statusColor(status: string) {
  if (status === "AVAILABLE") return styles.available;
  if (status === "LIMITED") return styles.limited;
  if (status === "BOOKED") return styles.booked;
  return styles.blackedOut;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: tokens.colors.background },
  screen: { flex: 1 },
  content: { padding: 16, paddingBottom: 70, gap: 14 },
  loader: { flex: 1, backgroundColor: tokens.colors.background },
  topbar: { flexDirection: "row", alignItems: "center", gap: 12, paddingVertical: 6 },
  back: { width: 42, height: 42, alignItems: "center", justifyContent: "center", borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: 21, backgroundColor: tokens.colors.surface },
  backText: { color: tokens.colors.onSurface, fontSize: 30, lineHeight: 32 },
  eyebrow: { color: tokens.colors.brandPink, fontSize: 10, fontWeight: "800", letterSpacing: 1.5 },
  title: { color: tokens.colors.onSurface, fontSize: 28, fontWeight: "800" },
  card: { borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.xl, padding: 17, backgroundColor: tokens.colors.surface },
  sectionTitle: { color: tokens.colors.onSurface, fontSize: 19, fontWeight: "800" },
  muted: { color: tokens.colors.textMuted, fontSize: 12, lineHeight: 18, marginTop: 4 },
  segment: { flexDirection: "row", gap: 6, marginTop: 15, padding: 4, borderRadius: tokens.radii.lg, backgroundColor: tokens.colors.surfaceGray },
  segmentItem: { flex: 1, minHeight: 40, alignItems: "center", justifyContent: "center", borderRadius: tokens.radii.md, paddingHorizontal: 5 },
  segmentActive: { backgroundColor: tokens.colors.surface },
  segmentText: { color: tokens.colors.textMuted, fontSize: 10, fontWeight: "800", textAlign: "center" },
  segmentTextActive: { color: tokens.colors.brandPink },
  label: { color: tokens.colors.onSurface, fontSize: 12, fontWeight: "700", marginTop: 15, marginBottom: 6 },
  input: { minHeight: 47, borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.md, paddingHorizontal: 12, backgroundColor: tokens.colors.surface, color: tokens.colors.onSurface },
  rule: { flexDirection: "row", alignItems: "flex-start", gap: 10, borderTopWidth: 1, borderTopColor: tokens.colors.surfaceVariant, paddingVertical: 12 },
  dayToggle: { width: 47, minHeight: 38, alignItems: "center", justifyContent: "center", borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.pill },
  dayToggleActive: { borderColor: tokens.colors.brandPink, backgroundColor: tokens.colors.pinkSoft },
  dayText: { color: tokens.colors.textMuted, fontSize: 11, fontWeight: "800" },
  dayTextActive: { color: tokens.colors.brandPink },
  ruleBody: { flex: 1, minHeight: 38, justifyContent: "center" },
  closed: { color: tokens.colors.textMuted, fontSize: 12, paddingVertical: 10 },
  capacityRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  ruleHint: { color: tokens.colors.secondary, fontSize: 12 },
  smallInput: { width: 58, height: 38, borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.md, textAlign: "center" },
  slotList: { gap: 7 },
  slotRow: { flexDirection: "row", alignItems: "center", gap: 5 },
  timeInput: { width: 57, height: 38, borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.sm, textAlign: "center", fontSize: 11 },
  slotDash: { color: tokens.colors.textMuted },
  slotLabel: { flex: 1, height: 38, borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.sm, paddingHorizontal: 7, fontSize: 11 },
  remove: { color: tokens.colors.danger, fontSize: 22, padding: 4 },
  addSlot: { alignSelf: "flex-start", paddingVertical: 5 },
  addSlotText: { color: tokens.colors.brandPink, fontSize: 11, fontWeight: "700" },
  primary: { minHeight: 48, alignItems: "center", justifyContent: "center", borderRadius: tokens.radii.pill, backgroundColor: tokens.colors.brandPink, paddingHorizontal: 18, marginTop: 16 },
  primaryText: { color: tokens.colors.pureWhite, fontSize: 13, fontWeight: "800" },
  disabled: { opacity: 0.5 },
  monthHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  monthButton: { width: 38, height: 38, alignItems: "center", justifyContent: "center", borderRadius: 19, backgroundColor: tokens.colors.surfaceGray },
  monthButtonText: { fontSize: 25, color: tokens.colors.onSurface },
  weekHeader: { flexDirection: "row", marginTop: 15 },
  weekday: { width: "14.285%", textAlign: "center", color: tokens.colors.textMuted, fontSize: 9, fontWeight: "800" },
  monthGrid: { flexDirection: "row", flexWrap: "wrap", marginTop: 5 },
  calendarCell: { width: "14.285%", aspectRatio: 0.95, borderWidth: 2, borderColor: tokens.colors.surface, borderRadius: 8, alignItems: "center", justifyContent: "center", backgroundColor: tokens.colors.surfaceGray },
  calendarDay: { color: tokens.colors.onSurface, fontSize: 12, fontWeight: "800" },
  calendarDayMuted: { opacity: 0.35 },
  capacity: { color: tokens.colors.textMuted, fontSize: 7, marginTop: 1 },
  exceptionMark: { position: "absolute", right: 4, top: 4, width: 5, height: 5, borderRadius: 3, backgroundColor: tokens.colors.brandPink },
  available: { backgroundColor: "#edf7ef" },
  limited: { backgroundColor: "#fff4e5" },
  booked: { backgroundColor: tokens.colors.pinkSoft },
  blackedOut: { backgroundColor: tokens.colors.surfaceVariant },
  legend: { flexDirection: "row", flexWrap: "wrap", gap: 10, marginTop: 13 },
  legendItem: { flexDirection: "row", alignItems: "center", gap: 4 },
  legendDot: { width: 9, height: 9, borderRadius: 5 },
  legendText: { color: tokens.colors.textMuted, fontSize: 8 },
  outline: { minHeight: 44, alignItems: "center", justifyContent: "center", borderWidth: 1, borderColor: tokens.colors.surfaceVariant, borderRadius: tokens.radii.pill, paddingHorizontal: 13, marginTop: 10 },
  outlineText: { color: tokens.colors.brandPink, fontSize: 12, fontWeight: "800" },
  externalRow: { flexDirection: "row", gap: 10, alignItems: "center", borderTopWidth: 1, borderTopColor: tokens.colors.surfaceVariant, paddingTop: 12, marginTop: 12 },
  externalUrl: { color: tokens.colors.onSurface, fontSize: 12, fontWeight: "700" },
  syncState: { color: tokens.colors.success, fontSize: 9, marginTop: 3 },
  errorCopy: { color: tokens.colors.danger, fontSize: 9, marginTop: 3 },
  externalActions: { alignItems: "flex-end", gap: 8 },
  exportUrl: { color: tokens.colors.secondary, fontSize: 10, lineHeight: 16, borderRadius: tokens.radii.md, backgroundColor: tokens.colors.surfaceGray, padding: 10, marginTop: 12 },
  buttonRow: { flexDirection: "row", gap: 8 },
  dangerText: { color: tokens.colors.danger, fontSize: 12, fontWeight: "700" },
  flex: { flex: 1 },
  modalBackdrop: { flex: 1, justifyContent: "flex-end", backgroundColor: "rgba(0,0,0,.35)" },
  modalCard: { maxHeight: "84%", borderTopLeftRadius: tokens.radii["2xl"], borderTopRightRadius: tokens.radii["2xl"], backgroundColor: tokens.colors.surface },
  modalContent: { padding: 22, paddingBottom: 44 },
  modalHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  close: { color: tokens.colors.textMuted, fontSize: 30, padding: 5 },
  noteInput: { minHeight: 90, paddingTop: 11 },
  deleteButton: { minHeight: 44, alignItems: "center", justifyContent: "center", marginTop: 8 },
  errorState: { flex: 1, alignItems: "center", justifyContent: "center", padding: 30 },
});
