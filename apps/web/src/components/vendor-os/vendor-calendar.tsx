"use client";

import { useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  AvailabilityException,
  AvailabilityExceptionRequest,
  AvailabilitySettings,
  AvailabilitySettingsRequest,
  AvailabilitySlot,
} from "@wedjan/shared";
import { apiErrorMessage } from "@wedjan/shared";
import { api } from "@/lib/api";
import { useAuth } from "@/components/platform/auth-context";
import {
  dateRange,
  formatDateOnly,
  formatDateTime,
  localDateValue,
  monthRange,
  titleCase,
} from "@/lib/booking-format";
import {
  AsyncState,
  AvailabilityCalendar,
  bookingStyles as styles,
} from "@/components/marketplace/booking/booking-shared";

const WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

export function VendorCalendarWorkspace() {
  const client = useQueryClient();
  const { me } = useAuth();
  const today = localDateValue();
  const [month, setMonth] = useState(`${today.slice(0, 7)}-01`);
  const [selectedDate, setSelectedDate] = useState(today);
  const range = monthRange(month);
  const settings = useQuery({
    queryKey: ["vendor-availability-settings", me?.account.id],
    enabled: Boolean(me),
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/vendors/me/availability");
      if (!data) throw new Error(apiErrorMessage(error, "Availability settings could not be loaded"));
      return data;
    },
  });
  const calendar = useQuery({
    queryKey: ["vendor-calendar", me?.account.id, range.from, range.to],
    enabled: Boolean(me),
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/vendors/me/calendar", { params: { query: range } });
      if (!data) throw new Error(apiErrorMessage(error, "Calendar could not be loaded"));
      return data;
    },
    refetchInterval: 60_000,
  });
  function refresh() {
    void client.invalidateQueries({ queryKey: ["vendor-availability-settings"] });
    void client.invalidateQueries({ queryKey: ["vendor-calendar"] });
    void client.invalidateQueries({ queryKey: ["vendor-availability"] });
  }
  const manual = settings.data?.exceptions.find((item) => item.date === selectedDate && item.source === "MANUAL");
  const imported = settings.data?.exceptions.filter((item) => item.date === selectedDate && item.source === "ICS") ?? [];
  const selectedDay = calendar.data?.days.find((item) => item.date === selectedDate);
  function changeMonth(nextMonth: string) {
    setMonth(nextMonth);
    if (!selectedDate.startsWith(nextMonth.slice(0, 7))) setSelectedDate(nextMonth);
  }

  return (
    <>
      <h1 className="app-page-title">Availability calendar</h1>
      <p className="app-page-subtitle">Weekly capacity, one-off exceptions, booking blocks, and connected calendars in venue-local time.</p>
      <div className={styles.calendarWorkspace}>
        <div className={styles.stack}>
          <section className={styles.panel}>
            <p className={styles.eyebrow}>Live availability</p>
            <h2>Calendar</h2>
            <p className={styles.panelIntro}>Select a date to add a blackout, extra opening, or custom slots. Imported and booked dates remain protected.</p>
            <AsyncState loading={calendar.isLoading} error={calendar.error?.message} onRetry={() => void calendar.refetch()}>
              <AvailabilityCalendar month={month} days={calendar.data?.days ?? []} minDate={today} selectedDate={selectedDate} onMonthChange={changeMonth} onSelect={(_, date) => setSelectedDate(date)} />
            </AsyncState>
          </section>
          <AsyncState loading={settings.isLoading} error={settings.error?.message} onRetry={() => void settings.refetch()}>
            {settings.data ? (
              <AvailabilitySettingsEditor
                key={`${settings.data.mode}-${settings.data.timezone}-${settings.data.rules.map((rule) => `${rule.id}:${rule.isAvailable}:${rule.jobsPerDay}:${rule.slots.length}`).join("|")}`}
                settings={settings.data}
                onChanged={refresh}
              />
            ) : null}
          </AsyncState>
          <CalendarIntegrations />
        </div>
        <aside className={styles.stickyColumn}>
          <ExceptionEditor
            existing={manual}
            imported={imported}
            key={`${selectedDate}-${manual?.id ?? "new"}`}
            selectedDate={selectedDate}
            selectedDay={selectedDay}
            timezone={settings.data?.timezone ?? calendar.data?.timezone ?? "UTC"}
            onChanged={refresh}
          />
          <BulkBlackout onChanged={refresh} />
          {settings.data && <ImportedExceptions exceptions={settings.data.exceptions.filter((item) => item.source === "ICS")} timezone={settings.data.timezone} />}
        </aside>
      </div>
    </>
  );
}

function AvailabilitySettingsEditor({ settings, onChanged }: { settings: AvailabilitySettings; onChanged: () => void }) {
  const [draft, setDraft] = useState<AvailabilitySettingsRequest>({
    mode: settings.mode,
    timezone: settings.timezone,
    rules: settings.rules.map(({ weekday, isAvailable, slots, jobsPerDay }) => ({ weekday, isAvailable, slots, jobsPerDay })),
  });
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  function updateRule(weekday: number, change: Partial<AvailabilitySettingsRequest["rules"][number]>) {
    setDraft((current) => ({ ...current, rules: current.rules.map((rule) => rule.weekday === weekday ? { ...rule, ...change } : rule) }));
    setSaved(false);
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSubmitting(true); setError(""); setSaved(false);
    try {
      const { data, error: body } = await api.PUT("/api/v1/vendors/me/availability", { body: draft });
      if (!data) setError(apiErrorMessage(body, "Availability rules could not be saved"));
      else { setSaved(true); onChanged(); }
    } finally { setSubmitting(false); }
  }
  return (
    <section className={styles.panel}>
      <p className={styles.eyebrow}>Weekly defaults</p><h2>Availability rules</h2>
      <form className={styles.stack} onSubmit={submit}>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            Mode
            <select
              value={draft.mode}
              onChange={(event) => {
                const mode = event.target.value as AvailabilitySettingsRequest["mode"];
                setDraft((current) => ({
                  ...current,
                  mode,
                  rules: current.rules.map((rule) => mode === "SLOT" && rule.isAvailable && !rule.slots?.length
                    ? { ...rule, slots: [{ start: "09:00:00", end: "17:00:00", label: "Day slot" }] }
                    : rule),
                }));
                setSaved(false);
              }}
            >
              <option value="DATE">Whole-day capacity</option>
              <option value="SLOT">Time slots</option>
            </select>
          </label>
          <label className={styles.field}>IANA timezone<input required maxLength={64} value={draft.timezone} onChange={(event) => setDraft((current) => ({ ...current, timezone: event.target.value }))} /></label>
        </div>
        <div className={styles.ruleList}>
          {[...draft.rules].sort((a, b) => a.weekday - b.weekday).map((rule) => (
            <div className={styles.rule} key={rule.weekday}>
              <strong>{WEEKDAYS[rule.weekday]}</strong>
              <label className={styles.field}><span>Open</span><input checked={rule.isAvailable} type="checkbox" onChange={(event) => updateRule(rule.weekday, { isAvailable: event.target.checked })} /></label>
              {draft.mode === "DATE" ? (
                <label className={styles.field}>Jobs per day<input min="1" max="100" type="number" value={rule.jobsPerDay} onChange={(event) => updateRule(rule.weekday, { jobsPerDay: Number(event.target.value) })} /></label>
              ) : (
                <SlotEditor slots={rule.slots ?? []} onChange={(slots) => updateRule(rule.weekday, { slots })} />
              )}
            </div>
          ))}
        </div>
        {error && <div className={styles.notice} data-tone="error" role="alert">{error}</div>}
        {saved && <div className={styles.notice} data-tone="success">Weekly availability saved.</div>}
        <button className={styles.button} disabled={submitting} type="submit">{submitting ? "Saving…" : "Save weekly rules"}</button>
      </form>
    </section>
  );
}

function ExceptionEditor({ existing, imported, selectedDate, selectedDay, timezone, onChanged }: { existing?: AvailabilityException; imported: AvailabilityException[]; selectedDate: string; selectedDay?: { status: string; capacity: number; occupied: number; slots: { start: string; end: string; label?: string; status: string }[] }; timezone: string; onChanged: () => void }) {
  const [type, setType] = useState<AvailabilityExceptionRequest["type"]>(existing?.type ?? "BLACKOUT");
  const [note, setNote] = useState(existing?.note ?? "");
  const [slots, setSlots] = useState<AvailabilitySlot[]>(existing?.slots ?? [{ start: "09:00:00", end: "17:00:00", label: "Day slot" }]);
  const [error, setError] = useState(""); const [submitting, setSubmitting] = useState(false);
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSubmitting(true); setError("");
    const body: AvailabilityExceptionRequest = { date: selectedDate, type, note: note.trim() || undefined, slots: type === "CUSTOM_SLOTS" ? slots : [] };
    try {
      const response = existing
        ? await api.PATCH("/api/v1/vendors/me/availability/exceptions/{id}", { params: { path: { id: existing.id } }, body })
        : await api.POST("/api/v1/vendors/me/availability/exceptions", { body });
      if (!response.data) setError(apiErrorMessage(response.error, "The exception could not be saved"));
      else onChanged();
    } finally { setSubmitting(false); }
  }
  async function remove() {
    if (!existing) return; setSubmitting(true); setError("");
    try {
      const { response, error: body } = await api.DELETE("/api/v1/vendors/me/availability/exceptions/{id}", { params: { path: { id: existing.id } } });
      if (!response.ok) setError(apiErrorMessage(body, "The exception could not be removed")); else onChanged();
    } finally { setSubmitting(false); }
  }
  return (
    <section className={styles.panel}>
      <p className={styles.eyebrow}>Selected date</p><h2>{formatDateOnly(selectedDate)}</h2>
      <p className={styles.panelIntro}>{timezone} · {selectedDay ? `${titleCase(selectedDay.status)} · ${selectedDay.occupied}/${selectedDay.capacity} occupied` : "No calendar fact loaded"}</p>
      {imported.length > 0 && <div className={styles.notice}>This date has {imported.length} read-only ICS block{imported.length === 1 ? "" : "s"}.</div>}
      <form className={styles.stack} onSubmit={save}>
        <label className={styles.field}>Exception type<select value={type} onChange={(event) => setType(event.target.value as AvailabilityExceptionRequest["type"])}><option value="BLACKOUT">Blackout</option><option value="EXTRA_OPEN">Extra open</option><option value="CUSTOM_SLOTS">Custom slots</option></select></label>
        {type === "CUSTOM_SLOTS" && <SlotEditor slots={slots} onChange={setSlots} />}
        <label className={styles.field}>Private note<textarea maxLength={500} value={note} onChange={(event) => setNote(event.target.value)} /></label>
        {error && <div className={styles.notice} data-tone="error" role="alert">{error}</div>}
        <div className={styles.actions}><button className={styles.button} disabled={submitting} type="submit">{submitting ? "Saving…" : existing ? "Update exception" : "Add exception"}</button>{existing && <button className={styles.dangerButton} disabled={submitting} type="button" onClick={() => void remove()}>Remove</button>}</div>
      </form>
    </section>
  );
}

function BulkBlackout({ onChanged }: { onChanged: () => void }) {
  const today = localDateValue(); const [from, setFrom] = useState(today); const [to, setTo] = useState(today); const [note, setNote] = useState(""); const [error, setError] = useState(""); const [saved, setSaved] = useState(""); const [submitting, setSubmitting] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (to < from) { setError("End date must be on or after start date."); return; }
    const dates = dateRange(from, to);
    if (dates.at(-1) !== to) { setError("Choose a range of 366 days or fewer."); return; }
    setSubmitting(true); setError(""); setSaved(""); let applied = 0;
    try {
      for (const date of dates) {
        const { data, error: body } = await api.POST("/api/v1/vendors/me/availability/exceptions", { body: { date, type: "BLACKOUT", slots: [], note: note.trim() || undefined } });
        if (!data) { setError(`${applied} date${applied === 1 ? " was" : "s were"} blocked before ${formatDateOnly(date)} failed: ${apiErrorMessage(body, "could not be blacked out")}`); onChanged(); return; }
        applied += 1;
      }
      setSaved(`${applied} date${applied === 1 ? "" : "s"} blocked.`);
      onChanged();
    } finally { setSubmitting(false); }
  }
  return (
    <section className={styles.panel}><p className={styles.eyebrow}>Accessible bulk edit</p><h2>Black out a range</h2><form className={styles.stack} onSubmit={submit}><div className={styles.formGrid}><label className={styles.field}>From<input min={today} required type="date" value={from} onChange={(event) => setFrom(event.target.value)} /></label><label className={styles.field}>To<input min={from} required type="date" value={to} onChange={(event) => setTo(event.target.value)} /></label></div><label className={styles.field}>Note<input maxLength={500} value={note} onChange={(event) => setNote(event.target.value)} /></label>{error && <div className={styles.notice} data-tone="error" role="alert">{error}</div>}{saved && <div className={styles.notice} data-tone="success">{saved}</div>}<button className={styles.dangerButton} disabled={submitting} type="submit">{submitting ? "Blocking dates…" : "Black out range"}</button></form></section>
  );
}

function CalendarIntegrations() {
  const client = useQueryClient(); const { me } = useAuth(); const [url, setUrl] = useState(""); const [error, setError] = useState(""); const [submitting, setSubmitting] = useState(false); const [copied, setCopied] = useState(false);
  const calendars = useQuery({ queryKey: ["external-calendars", me?.account.id], enabled: Boolean(me), queryFn: async () => { const { data, error: body } = await api.GET("/api/v1/vendors/me/calendars"); if (!data) throw new Error(apiErrorMessage(body, "Connected calendars could not be loaded")); return data; } });
  const exportFeed = useQuery({ queryKey: ["calendar-export", me?.account.id], enabled: Boolean(me), queryFn: async () => { const { data, error: body } = await api.GET("/api/v1/vendors/me/calendar-export"); if (!data) throw new Error(apiErrorMessage(body, "Export feed could not be loaded")); return data; } });
  function refresh() { void client.invalidateQueries({ queryKey: ["external-calendars"] }); void client.invalidateQueries({ queryKey: ["vendor-availability-settings"] }); void client.invalidateQueries({ queryKey: ["vendor-calendar"] }); }
  async function connect(event: FormEvent<HTMLFormElement>) { event.preventDefault(); setSubmitting(true); setError(""); try { const { data, error: body } = await api.POST("/api/v1/vendors/me/calendars", { body: { icsUrl: url.trim() } }); if (!data) setError(apiErrorMessage(body, "Calendar could not be connected")); else { setUrl(""); refresh(); } } finally { setSubmitting(false); } }
  async function sync(id: string) { setError(""); const { data, error: body } = await api.POST("/api/v1/vendors/me/calendars/{id}/sync", { params: { path: { id } } }); if (!data) setError(apiErrorMessage(body, "Calendar sync failed")); refresh(); }
  async function disconnect(id: string) { if (!window.confirm("Disconnect this calendar? Its imported blackout dates will be removed.")) return; const { response, error: body } = await api.DELETE("/api/v1/vendors/me/calendars/{id}", { params: { path: { id } } }); if (!response.ok) setError(apiErrorMessage(body, "Calendar could not be disconnected")); refresh(); }
  async function rotate() { if (!window.confirm("Rotate the secret feed URL? Calendar apps using the current URL will stop updating.")) return; const { data, error: body } = await api.POST("/api/v1/vendors/me/calendar-export/rotate"); if (!data) setError(apiErrorMessage(body, "Export URL could not be rotated")); else { setCopied(false); client.setQueryData(["calendar-export", me?.account.id], data); } }
  async function copyExportUrl(url: string) { try { await navigator.clipboard.writeText(url); setCopied(true); } catch { setError("Copy was blocked by the browser. Select the feed URL above and copy it manually."); } }
  return (
    <section className={styles.panel}><p className={styles.eyebrow}>Calendar sync</p><h2>iCal connections</h2><p className={styles.panelIntro}>Busy dates import as read-only blackouts. Your secret export contains confirmed bookings.</p>
      <form className={styles.actions} onSubmit={connect}><input aria-label="ICS calendar URL" className={styles.compactInput} placeholder="https://calendar.example/feed.ics" required type="url" value={url} onChange={(event) => setUrl(event.target.value)} /><button className={styles.button} disabled={submitting} type="submit">Connect</button></form>
      {(calendars.isLoading || exportFeed.isLoading) && <div className={styles.notice}>Loading calendar connections…</div>}
      {calendars.error && <div className={styles.notice} data-tone="error" role="alert">{calendars.error.message}</div>}
      {exportFeed.error && <div className={styles.notice} data-tone="error" role="alert">{exportFeed.error.message}</div>}
      <div className={styles.integrationList}>{calendars.data?.map((calendar) => <div className={styles.integration} key={calendar.id}><div className={styles.integrationHeader}><strong>{titleCase(calendar.syncStatus)}</strong><span className={styles.statusChip} data-tone={calendar.syncStatus === "DEGRADED" ? "danger" : calendar.syncStatus === "HEALTHY" ? "success" : "warning"}>{calendar.syncStatus}</span></div><code>{calendar.icsUrl}</code><small>{calendar.lastSyncedAt ? `Last synced ${formatDateTime(calendar.lastSyncedAt)}` : "Waiting for first sync"}</small>{calendar.lastError && <div className={styles.notice} data-tone="error">{calendar.lastError}</div>}<div className={styles.actions}><button className={styles.secondaryButton} type="button" onClick={() => void sync(calendar.id)}>Sync now</button><button className={styles.dangerButton} type="button" onClick={() => void disconnect(calendar.id)}>Disconnect</button></div></div>)}</div>
      {calendars.data?.length === 0 && <p className={styles.panelIntro}>No external calendars connected.</p>}
      {exportFeed.data && <div className={styles.integration}><strong>Secret confirmed-booking feed</strong><code>{exportFeed.data.url}</code><div className={styles.actions}><button className={styles.secondaryButton} type="button" onClick={() => void copyExportUrl(exportFeed.data.url)}>Copy URL</button><button className={styles.dangerButton} type="button" onClick={() => void rotate()}>Rotate secret</button></div>{copied && <span className={styles.notice} data-tone="success">Copied.</span>}</div>}
      {error && <div className={styles.notice} data-tone="error" role="alert">{error}</div>}
    </section>
  );
}

function ImportedExceptions({ exceptions, timezone }: { exceptions: AvailabilityException[]; timezone: string }) {
  return <section className={styles.panel}><p className={styles.eyebrow}>Read-only imports</p><h2>ICS blackouts</h2>{exceptions.length === 0 ? <p className={styles.panelIntro}>No imported busy dates.</p> : <div className={styles.exceptionList}>{exceptions.slice(0, 20).map((item) => <div className={styles.exceptionItem} key={item.id}><span><strong>{formatDateOnly(item.date)}</strong><small>{item.note ?? titleCase(item.type)} · {timezone}</small></span><span className={styles.statusChip}>ICS</span></div>)}</div>}</section>;
}

function SlotEditor({ slots, onChange }: { slots: AvailabilitySlot[]; onChange: (slots: AvailabilitySlot[]) => void }) {
  function update(index: number, change: Partial<AvailabilitySlot>) { onChange(slots.map((slot, slotIndex) => slotIndex === index ? { ...slot, ...change } : slot)); }
  return <div className={styles.slotEditor}>{slots.map((slot, index) => <div className={styles.slotRow} key={`${index}-${slot.start}-${slot.end}`}><label className={styles.field}>Start<input required type="time" value={slot.start.slice(0, 5)} onChange={(event) => update(index, { start: `${event.target.value}:00` })} /></label><label className={styles.field}>End<input required type="time" value={slot.end.slice(0, 5)} onChange={(event) => update(index, { end: `${event.target.value}:00` })} /></label><label className={styles.field}>Label<input maxLength={80} value={slot.label ?? ""} onChange={(event) => update(index, { label: event.target.value })} /></label><button aria-label="Remove slot" className={styles.dangerButton} type="button" onClick={() => onChange(slots.filter((_, slotIndex) => slotIndex !== index))}>×</button></div>)}<button className={styles.textButton} type="button" onClick={() => onChange([...slots, { start: "09:00:00", end: "17:00:00", label: "" }])}>+ Add slot</button></div>;
}
