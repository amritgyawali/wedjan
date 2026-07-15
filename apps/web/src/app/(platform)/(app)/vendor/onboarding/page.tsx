"use client";
/* eslint-disable @next/next/no-img-element -- authenticated R2 previews use runtime URLs */

import { useCallback, useEffect, useState, type ChangeEvent } from "react";
import { useRouter } from "next/navigation";
import { apiErrorMessage, type Category, type VendorMe } from "@wedjan/shared";
import { api } from "@/lib/api";
import { uploadMedia } from "@/lib/media-upload";
import { Icon } from "@/components/platform/icon";

const STEPS = ["Business basics", "Categories", "Service areas", "Packages", "Media", "Verification", "Review"];
const CURRENCIES = ["AUD", "NPR", "USD", "GBP"] as const;

type Basics = {
  businessName: string; tagline: string; about: string; foundedYear: string; teamSize: string;
  languages: string; baseCity: string; baseCountry: string; lat: string; lng: string;
  website: string; instagram: string; currency: "AUD" | "NPR" | "USD" | "GBP";
};

const EMPTY_BASICS: Basics = { businessName: "", tagline: "", about: "", foundedYear: "", teamSize: "",
  languages: "English", baseCity: "", baseCountry: "", lat: "", lng: "", website: "", instagram: "", currency: "AUD" };

export default function VendorOnboardingPage() {
  const router = useRouter();
  const [workspace, setWorkspace] = useState<VendorMe | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [step, setStep] = useState(1);
  const [basics, setBasics] = useState<Basics>(EMPTY_BASICS);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const load = useCallback(async () => {
    const [vendor, taxonomy] = await Promise.all([
      api.GET("/api/v1/vendors/me"),
      api.GET("/api/v1/categories"),
    ]);
    if (!vendor.data) throw new Error(apiErrorMessage(vendor.error, "Could not load vendor workspace"));
    setWorkspace(vendor.data);
    setCategories(taxonomy.data?.items ?? []);
    const p = vendor.data.profile;
    setBasics({
      businessName: p.businessName ?? "", tagline: p.tagline ?? "", about: p.about ?? "",
      foundedYear: p.foundedYear?.toString() ?? "", teamSize: p.teamSize?.toString() ?? "",
      languages: p.languages?.join(", ") ?? "English", baseCity: p.baseCity ?? "",
      baseCountry: p.baseCountry ?? "", lat: p.lat?.toString() ?? "", lng: p.lng?.toString() ?? "",
      website: p.website ?? "", instagram: p.instagram ?? "", currency: p.currency,
    });
    setStep(Math.min(p.onboardingStep || 1, 7));
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void load().catch((e: unknown) => setError(e instanceof Error ? e.message : "Could not load"));
    }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const reload = async () => {
    const result = await api.GET("/api/v1/vendors/me");
    if (result.data) setWorkspace(result.data);
  };

  const run = async (action: () => Promise<void>) => {
    setBusy(true); setError("");
    try { await action(); } catch (e) { setError(e instanceof Error ? e.message : "Something went wrong"); }
    finally { setBusy(false); }
  };

  const saveBasics = async (nextStep = step) => {
    const { data, error: apiError } = await api.PATCH("/api/v1/vendors/me", { body: {
      businessName: basics.businessName || undefined, tagline: basics.tagline || undefined,
      about: basics.about || undefined, foundedYear: basics.foundedYear ? Number(basics.foundedYear) : undefined,
      teamSize: basics.teamSize ? Number(basics.teamSize) : undefined,
      languages: basics.languages.split(",").map((x) => x.trim()).filter(Boolean),
      baseCity: basics.baseCity || undefined, baseCountry: basics.baseCountry.toUpperCase() || undefined,
      lat: basics.lat ? Number(basics.lat) : undefined, lng: basics.lng ? Number(basics.lng) : undefined,
      website: basics.website || undefined, instagram: basics.instagram || undefined,
      currency: basics.currency, onboardingStep: nextStep,
    } });
    if (!data) throw new Error(apiErrorMessage(apiError, "Could not save"));
    setWorkspace(data); setSaved(true); setTimeout(() => setSaved(false), 1800);
  };

  if (!workspace) return <p className="app-page-subtitle">Loading your vendor workspace…</p>;

  return (
    <div className="vendor-workspace">
      <header className="vendor-wizard-header">
        <div><p className="eyebrow">Vendor onboarding</p><h1 className="app-page-title">Build a listing customers can trust</h1>
          <p className="app-page-subtitle">Transparent prices, real work, verified identity. Your progress is saved.</p></div>
        <div className="strength-dial" aria-label={`${workspace.listingStrength}% listing strength`}><strong>{workspace.listingStrength}%</strong><span>strength</span></div>
      </header>
      <ol className="wizard-progress" aria-label="Onboarding progress">
        {STEPS.map((label, index) => <li key={label} data-active={step === index + 1} data-done={step > index + 1}>
          <button type="button" onClick={() => setStep(index + 1)}><span>{index + 1}</span>{label}</button></li>)}
      </ol>
      {error && <div className="auth-error" role="alert">{error}</div>}
      {saved && <div className="autosave-chip">Saved</div>}

      <section className="wizard-card">
        {step === 1 && <BasicsStep value={basics} onChange={setBasics} onSave={() => run(async () => { await saveBasics(2); setStep(2); })} busy={busy} />}
        {step === 2 && <CategoriesStep categories={categories} selected={workspace.categories} busy={busy} onSave={(items) => run(async () => {
          const { data, error: e } = await api.PUT("/api/v1/vendors/me/categories", { body: { items } });
          if (!data) throw new Error(apiErrorMessage(e)); setWorkspace(data); setStep(3);
        })} />}
        {step === 3 && <ServiceAreasStep workspace={workspace} busy={busy} onChange={reload} run={run} onNext={() => setStep(4)} />}
        {step === 4 && <PackagesStep workspace={workspace} categories={categories} busy={busy} run={run} onChange={reload} onNext={() => setStep(5)} />}
        {step === 5 && <MediaStep workspace={workspace} busy={busy} run={run} onChange={reload} onNext={() => setStep(6)} />}
        {step === 6 && <DocumentsStep workspace={workspace} busy={busy} run={run} onChange={reload} onNext={() => setStep(7)} />}
        {step === 7 && <ReviewStep workspace={workspace} busy={busy} submitted={submitted} run={run} onChange={reload} onEdit={setStep} onSubmit={() => run(async () => {
          const { data, error: e } = await api.POST("/api/v1/vendors/me/submit", {});
          if (!data) throw new Error(apiErrorMessage(e, "Submission is blocked"));
          setWorkspace(data); setSubmitted(true);
        })} onPreview={() => workspace.profile.slug && router.push(`/v/${workspace.profile.slug}`)} />}
      </section>
    </div>
  );
}

function BasicsStep({ value, onChange, onSave, busy }: { value: Basics; onChange: (v: Basics) => void; onSave: () => void; busy: boolean }) {
  const field = (key: keyof Basics) => (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => onChange({ ...value, [key]: event.target.value });
  return <form onSubmit={(e) => { e.preventDefault(); onSave(); }} className="wizard-form">
    <StepTitle number={1} title="Business basics" body="Tell customers who you are and what makes your work distinct." />
    <div className="form-grid two"><Field label="Business name" required><input className="form-input" value={value.businessName} onChange={field("businessName")} required /></Field>
      <Field label="Tagline"><input className="form-input" value={value.tagline} onChange={field("tagline")} placeholder="Modern celebrations, beautifully captured" /></Field></div>
    <Field label={`About (${value.about.trim().length}/200 minimum)`}><textarea className="form-input textarea" value={value.about} onChange={field("about")} rows={7} required /></Field>
    <div className="form-grid three"><Field label="Founded year"><input className="form-input" inputMode="numeric" value={value.foundedYear} onChange={field("foundedYear")} /></Field>
      <Field label="Team size"><input className="form-input" inputMode="numeric" value={value.teamSize} onChange={field("teamSize")} /></Field>
      <Field label="Currency"><select className="form-input" value={value.currency} onChange={field("currency")}>{CURRENCIES.map((c) => <option key={c}>{c}</option>)}</select></Field></div>
    <Field label="Languages (comma separated)"><input className="form-input" value={value.languages} onChange={field("languages")} /></Field>
    <div className="form-grid two"><Field label="Base city"><input className="form-input" value={value.baseCity} onChange={field("baseCity")} required /></Field>
      <Field label="Country code"><input className="form-input" maxLength={2} value={value.baseCountry} onChange={field("baseCountry")} placeholder="NP" required /></Field></div>
    <div className="form-grid two"><Field label="Latitude"><input className="form-input" inputMode="decimal" value={value.lat} onChange={field("lat")} /></Field>
      <Field label="Longitude"><input className="form-input" inputMode="decimal" value={value.lng} onChange={field("lng")} /></Field></div>
    <div className="form-grid two"><Field label="Website"><input className="form-input" type="url" value={value.website} onChange={field("website")} /></Field>
      <Field label="Instagram"><input className="form-input" value={value.instagram} onChange={field("instagram")} /></Field></div>
    <WizardNext busy={busy} label="Save & choose categories" />
  </form>;
}

function CategoriesStep({ categories, selected, busy, onSave }: { categories: Category[]; selected: VendorMe["categories"]; busy: boolean; onSave: (items: VendorMe["categories"]) => void }) {
  const [items, setItems] = useState(selected);
  const parents = categories.filter((c) => !c.parentId);
  const toggle = (id: string) => {
    const exists = items.find((x) => x.categoryId === id);
    if (exists) { const rest = items.filter((x) => x.categoryId !== id); if (exists.isPrimary && rest[0]) rest[0] = { ...rest[0], isPrimary: true }; setItems(rest); }
    else if (items.length < 4) setItems([...items, { categoryId: id, isPrimary: items.length === 0 }]);
  };
  return <div><StepTitle number={2} title="Choose your categories" body="Select one primary category and up to three supporting categories." />
    <div className="category-picker">{parents.map((parent) => { const children = categories.filter((c) => c.parentId === parent.id); const options = children.length ? children : [parent]; return <div key={parent.id} className="category-group"><h3>{parent.name}</h3>{options.map((c) => {
      const chosen = items.find((x) => x.categoryId === c.id); return <div className="category-choice" key={c.id} data-selected={!!chosen}>
        <button type="button" onClick={() => toggle(c.id)} disabled={!chosen && items.length >= 4}><Icon name={parent.icon ?? "category"} />{c.name}</button>
        {chosen && <label><input type="radio" name="primary" checked={chosen.isPrimary} onChange={() => setItems(items.map((x) => ({ ...x, isPrimary: x.categoryId === c.id })))} /> Primary</label>}
      </div>; })}</div>; })}</div>
    <WizardNext busy={busy} disabled={items.length === 0} label="Save & add service area" onClick={() => onSave(items)} />
  </div>;
}

function ServiceAreasStep({ workspace, busy, run, onChange, onNext }: { workspace: VendorMe; busy: boolean; run: (a: () => Promise<void>) => Promise<void>; onChange: () => Promise<void>; onNext: () => void }) {
  const [form, setForm] = useState({ city: workspace.profile.baseCity ?? "Kathmandu", country: workspace.profile.baseCountry ?? "NP", lat: workspace.profile.lat?.toString() ?? "27.7172", lng: workspace.profile.lng?.toString() ?? "85.3240", radius: "50", fee: "", note: "" });
  const update = (key: keyof typeof form) => (e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, [key]: e.target.value });
  const add = () => run(async () => { const { data, error } = await api.POST("/api/v1/vendors/me/service-areas", { body: { mode: "CITY_RADIUS", city: form.city, country: form.country.toUpperCase(), lat: Number(form.lat), lng: Number(form.lng), radiusKm: Number(form.radius), travelFeeCents: form.fee ? Math.round(Number(form.fee) * 100) : undefined, travelFeeNote: form.note || undefined } }); if (!data) throw new Error(apiErrorMessage(error)); await onChange(); });
  return <div><StepTitle number={3} title="Where do you work?" body="Set a city, radius, and any honest travel fee." />
    <div className="map-panel"><Icon name="location_on" /><strong>{form.city || "Your service area"}</strong><span>{form.radius} km radius</span><div className="map-radius" style={{ width: `${Math.min(90, 20 + Number(form.radius) / 5)}%` }} /></div>
    <div className="form-grid two"><Field label="City"><input className="form-input" value={form.city} onChange={update("city")} /></Field><Field label="Country"><input className="form-input" maxLength={2} value={form.country} onChange={update("country")} /></Field>
      <Field label="Latitude"><input className="form-input" value={form.lat} onChange={update("lat")} /></Field><Field label="Longitude"><input className="form-input" value={form.lng} onChange={update("lng")} /></Field></div>
    <Field label={`Travel radius: ${form.radius} km`}><input type="range" min="5" max="500" value={form.radius} onChange={update("radius")} /></Field>
    <div className="form-grid two"><Field label="Travel fee (optional)"><input className="form-input" inputMode="decimal" value={form.fee} onChange={update("fee")} /></Field><Field label="Fee note"><input className="form-input" value={form.note} onChange={update("note")} /></Field></div>
    <button type="button" className="ghost-button" disabled={busy} onClick={add}>Add service area</button>
    <div className="record-list">{workspace.serviceAreas.map((area) => <div className="record-row" key={area.id}><div><strong>{area.city}, {area.country}</strong><span>{area.radiusKm} km radius{area.travelFeeCents ? ` · ${(area.travelFeeCents / 100).toFixed(2)} travel fee` : ""}</span></div><button className="text-button danger" onClick={() => void run(async () => { await api.DELETE("/api/v1/vendors/me/service-areas/{areaId}", { params: { path: { areaId: area.id } } }); await onChange(); })}>Remove</button></div>)}</div>
    <WizardNext busy={busy} disabled={!workspace.serviceAreas.length} label="Continue to packages" onClick={onNext} />
  </div>;
}

function PackagesStep({ workspace, categories, busy, run, onChange, onNext }: { workspace: VendorMe; categories: Category[]; busy: boolean; run: (a: () => Promise<void>) => Promise<void>; onChange: () => Promise<void>; onNext: () => void }) {
  const selectedIds = workspace.categories.map((x) => x.categoryId);
  const available = categories.filter((x) => selectedIds.includes(x.id));
  const [form, setForm] = useState({ title: "", categoryId: available[0]?.id ?? "", description: "", price: "", pricingModel: "FLAT" as "FLAT" | "PER_HOUR" | "PER_GUEST" | "STARTING_AT", included: "Planning consultation\nEvent-day service\nPost-event support", excluded: "", bookingMode: "REQUEST" as "REQUEST" | "INSTANT", deposit: "25", policy: "MODERATE" as "FLEXIBLE" | "MODERATE" | "STRICT", coverMediaId: "" });
  const [editingId, setEditingId] = useState<string | null>(null);
  const change = (key: keyof typeof form) => (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => setForm({ ...form, [key]: e.target.value });
  const uploadCover = async (e: ChangeEvent<HTMLInputElement>) => { const file = e.target.files?.[0]; if (!file) return; await run(async () => { const media = await uploadMedia(file, "IMAGE"); setForm((x) => ({ ...x, coverMediaId: media.id })); }); };
  const create = () => run(async () => { const body = { title: form.title, categoryId: form.categoryId, descriptionMd: form.description || undefined, priceCents: Math.round(Number(form.price) * 100), pricingModel: form.pricingModel, whatsIncludedMd: form.included, whatsExcludedMd: form.excluded || undefined, bookingMode: form.bookingMode, depositPct: Number(form.deposit), cancellationPolicy: form.policy, coverMediaId: form.coverMediaId || undefined }; if (editingId) { const updated = await api.PATCH("/api/v1/vendors/me/packages/{packageId}", { params: { path: { packageId: editingId } }, body }); if (!updated.data) throw new Error(apiErrorMessage(updated.error)); } else { const created = await api.POST("/api/v1/vendors/me/packages", { body }); if (!created.data) throw new Error(apiErrorMessage(created.error)); const publish = await api.POST("/api/v1/vendors/me/packages/{packageId}/publish", { params: { path: { packageId: created.data.id } } }); if (!publish.data) throw new Error(apiErrorMessage(publish.error)); } setEditingId(null); setForm({ ...form, title: "", description: "", price: "", coverMediaId: "" }); await onChange(); });
  return <div><StepTitle number={4} title="Create a priced package" body="Every offer has a real price. Customers should understand exactly what they get." />
    <div className="package-builder"><div className="wizard-form"><div className="form-grid two"><Field label="Package title"><input className="form-input" value={form.title} onChange={change("title")} /></Field><Field label="Category"><select className="form-input" value={form.categoryId} onChange={change("categoryId")}>{available.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field></div>
      <Field label="Description"><textarea className="form-input textarea" rows={3} value={form.description} onChange={change("description")} /></Field>
      <div className="form-grid two"><Field label={`Price (${workspace.profile.currency})`}><input className="form-input" inputMode="decimal" value={form.price} onChange={change("price")} placeholder="1250.00" /></Field><Field label="Pricing model"><select className="form-input" value={form.pricingModel} onChange={change("pricingModel")}><option value="FLAT">Flat</option><option value="PER_HOUR">Per hour</option><option value="PER_GUEST">Per guest</option><option value="STARTING_AT">Starting at</option></select></Field></div>
      <Field label="What's included (one item per line, minimum 3)"><textarea className="form-input textarea" rows={4} value={form.included} onChange={change("included")} /></Field>
      <Field label="What's excluded"><textarea className="form-input textarea" rows={2} value={form.excluded} onChange={change("excluded")} /></Field>
      <div className="form-grid three"><Field label="Booking"><select className="form-input" value={form.bookingMode} onChange={change("bookingMode")}><option value="REQUEST">Request</option><option value="INSTANT">Instant</option></select></Field><Field label="Deposit %"><input className="form-input" type="number" min="10" max="100" value={form.deposit} onChange={change("deposit")} /></Field><Field label="Cancellation"><select className="form-input" value={form.policy} onChange={change("policy")}><option>FLEXIBLE</option><option>MODERATE</option><option>STRICT</option></select></Field></div>
      <Field label="Package photo"><input type="file" accept="image/*" onChange={(e) => void uploadCover(e)} />{form.coverMediaId && <span className="inline-success">Uploaded</span>}</Field>
      <button type="button" className="primary-button" disabled={busy || !form.title || !form.price || !form.coverMediaId || !form.categoryId} onClick={create}>{editingId ? "Save package changes" : "Create & publish package"}</button></div>
      <div className="package-preview"><p className="eyebrow">Live preview</p><div className="preview-image"><Icon name="photo_camera" /></div><h3>{form.title || "Your package title"}</h3><p>{form.description || "A clear summary of the experience."}</p><strong>{workspace.profile.currency} {Number(form.price || 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}{priceSuffix(form.pricingModel)}</strong><div><span>{form.deposit}% deposit</span><span>{form.policy.toLowerCase()}</span></div></div></div>
    <div className="record-list">{workspace.packages.map((pkg) => <div className="record-row" key={pkg.id}><div><strong>{pkg.title}</strong><span>{pkg.currency} {(pkg.priceCents / 100).toFixed(2)} · {pkg.status}</span></div><div><button className="text-button" onClick={() => { setEditingId(pkg.id); setForm({ title: pkg.title, categoryId: pkg.categoryId, description: pkg.descriptionMd ?? "", price: (pkg.priceCents / 100).toString(), pricingModel: pkg.pricingModel, included: pkg.whatsIncludedMd ?? "", excluded: pkg.whatsExcludedMd ?? "", bookingMode: pkg.bookingMode, deposit: pkg.depositPct.toString(), policy: pkg.cancellationPolicy, coverMediaId: pkg.coverMediaId ?? "" }); }}>Edit</button>{pkg.status === "DRAFT" && <button className="text-button" onClick={() => void run(async () => { const r = await api.POST("/api/v1/vendors/me/packages/{packageId}/publish", { params: { path: { packageId: pkg.id } } }); if (!r.data) throw new Error(apiErrorMessage(r.error)); await onChange(); })}>Publish</button>}<button className="text-button danger" onClick={() => void run(async () => { await api.DELETE("/api/v1/vendors/me/packages/{packageId}", { params: { path: { packageId: pkg.id } } }); await onChange(); })}>Archive</button></div></div>)}</div>
    <AddOnBuilder workspace={workspace} busy={busy} run={run} onChange={onChange} />
    <WizardNext busy={busy} disabled={!workspace.packages.some((x) => x.status === "PUBLISHED")} label="Continue to media" onClick={onNext} />
  </div>;
}

function MediaStep({ workspace, busy, run, onChange, onNext }: { workspace: VendorMe; busy: boolean; run: (a: () => Promise<void>) => Promise<void>; onChange: () => Promise<void>; onNext: () => void }) {
  const save = async (items: { mediaId: string; kind: "GALLERY" | "COVER" | "LOGO" | "SHOWREEL"; caption?: string; sort: number }[]) => { const r = await api.PUT("/api/v1/vendors/me/media", { body: { items } }); if (!r.data) throw new Error(apiErrorMessage(r.error)); await onChange(); };
  const current = () => workspace.media.map((x) => ({ mediaId: x.mediaId, kind: x.kind, caption: x.caption, sort: x.sort }));
  const upload = (kind: "GALLERY" | "COVER" | "LOGO") => async (e: ChangeEvent<HTMLInputElement>) => { const files = Array.from(e.target.files ?? []); if (!files.length) return; await run(async () => { const uploaded = []; for (const file of files) uploaded.push(await uploadMedia(file, "IMAGE")); const kept = kind === "COVER" || kind === "LOGO" ? current().filter((x) => x.kind !== kind) : current(); await save([...kept, ...uploaded.map((x, i) => ({ mediaId: x.id, kind, sort: kept.length + i }))]); }); };
  const gallery = workspace.media.filter((x) => x.kind === "GALLERY");
  const move = (id: string, direction: number) => void run(async () => { const list = current(); const galleries = list.filter((x) => x.kind === "GALLERY").sort((a, b) => a.sort - b.sort); const index = galleries.findIndex((x) => x.mediaId === id); const target = index + direction; if (target < 0 || target >= galleries.length) return; [galleries[index], galleries[target]] = [galleries[target], galleries[index]]; galleries.forEach((x, i) => x.sort = i); await save([...list.filter((x) => x.kind !== "GALLERY"), ...galleries]); });
  return <div><StepTitle number={5} title="Show your best work" body="Add at least five gallery images and one separate cover image. Large photos are compressed before upload." />
    <div className="upload-grid"><UploadBox icon="collections" title={`Gallery (${gallery.length}/5)`} multiple onChange={upload("GALLERY")} /><UploadBox icon="panorama" title={workspace.media.some((x) => x.kind === "COVER") ? "Replace cover" : "Add cover"} onChange={upload("COVER")} /><UploadBox icon="account_circle" title="Add logo" onChange={upload("LOGO")} /></div>
    <div className="media-grid">{workspace.media.map((item) => <div className="media-tile" key={item.id}>{item.url ? <img src={item.url} alt={item.caption ?? `${item.kind} image`} /> : <Icon name="image" />}<span>{item.kind}</span><div><button disabled={item.kind !== "GALLERY"} onClick={() => move(item.mediaId, -1)}>←</button><button disabled={item.kind !== "GALLERY"} onClick={() => move(item.mediaId, 1)}>→</button><button onClick={() => void run(async () => save(current().filter((x) => x.mediaId !== item.mediaId)))}>×</button></div></div>)}</div>
    <WizardNext busy={busy} disabled={gallery.length < 5 || !workspace.media.some((x) => x.kind === "COVER")} label="Continue to verification" onClick={onNext} />
  </div>;
}

function DocumentsStep({ workspace, busy, run, onChange, onNext }: { workspace: VendorMe; busy: boolean; run: (a: () => Promise<void>) => Promise<void>; onChange: () => Promise<void>; onNext: () => void }) {
  const types = [{ key: "GOVERNMENT_ID", title: "Government ID", body: "Passport, driving licence, or national ID" }, { key: "BUSINESS_REGISTRATION", title: "Business registration", body: "Current registration or incorporation certificate" }, { key: "INSURANCE", title: "Insurance (optional)", body: "Upload to earn the Insured badge" }] as const;
  const upload = (type: typeof types[number]["key"]) => async (e: ChangeEvent<HTMLInputElement>) => { const file = e.target.files?.[0]; if (!file) return; await run(async () => { const media = await uploadMedia(file, file.type === "application/pdf" ? "DOC" : "IMAGE"); const r = await api.POST("/api/v1/vendors/me/documents", { body: { type, mediaId: media.id } }); if (!r.data) throw new Error(apiErrorMessage(r.error)); await onChange(); }); };
  return <div><StepTitle number={6} title="Verify your business" body="Documents are private and only visible to wedjan reviewers." />
    <div className="document-list">{types.map((type) => { const doc = workspace.documents.find((x) => x.type === type.key); return <div className="document-card" key={type.key}><Icon name={doc?.status === "APPROVED" ? "verified" : "description"} /><div><strong>{type.title}</strong><p>{type.body}</p>{doc?.reviewerNote && <small>{doc.reviewerNote}</small>}</div><span className={`status-chip ${doc?.status?.toLowerCase() ?? "missing"}`}>{doc?.status ?? "MISSING"}</span><label className="ghost-button">{doc ? "Replace" : "Upload"}<input hidden type="file" accept="image/*,application/pdf" onChange={(e) => void upload(type.key)(e)} /></label></div>; })}</div>
    <WizardNext busy={busy} disabled={!types.slice(0, 2).every((t) => workspace.documents.some((d) => d.type === t.key && d.status !== "REJECTED"))} label="Review listing" onClick={onNext} />
  </div>;
}

function ReviewStep({ workspace, busy, submitted, run, onChange, onEdit, onSubmit, onPreview }: { workspace: VendorMe; busy: boolean; submitted: boolean; run: (a: () => Promise<void>) => Promise<void>; onChange: () => Promise<void>; onEdit: (n: number) => void; onSubmit: () => void; onPreview: () => void }) {
  const ready = workspace.gates.every((g) => g.passed);
  if (submitted || workspace.profile.status === "SUBMITTED") return <div className="success-screen"><div className="success-burst"><Icon name="celebration" /></div><p className="eyebrow">Submitted</p><h2>Your listing is in review</h2><p>We’ll review your identity and business documents. Once both are approved, your listing goes live automatically.</p><button className="ghost-button" onClick={onPreview}>Preview profile</button></div>;
  return <div><StepTitle number={7} title="Ready to meet your customers?" body="Fix any unfinished item, then send your listing for verification." />
    <div className="review-summary"><div className="strength-bar"><span style={{ width: `${workspace.listingStrength}%` }} /></div><strong>{workspace.listingStrength}% listing strength</strong></div>
    <div className="gate-list">{workspace.gates.map((gate) => <button key={gate.key} type="button" data-pass={gate.passed} onClick={() => onEdit(gate.step)}><Icon name={gate.passed ? "check_circle" : "error"} /><span><strong>{gate.message}</strong><small>Step {gate.step} · {gate.passed ? "Complete" : "Needs attention"}</small></span><Icon name="chevron_right" /></button>)}</div>
    <FaqEditor workspace={workspace} busy={busy} run={run} onChange={onChange} />
    <button type="button" className="primary-button wizard-submit" disabled={busy || !ready} onClick={onSubmit}>{busy ? "Submitting…" : "Submit for verification"}</button>
    {!ready && <p className="form-hint">Complete every checklist item to submit.</p>}
  </div>;
}

function StepTitle({ number, title, body }: { number: number; title: string; body: string }) { return <header className="step-title"><span>{number}</span><div><h2>{title}</h2><p>{body}</p></div></header>; }
function Field({ label, children, required }: { label: string; children: React.ReactNode; required?: boolean }) { return <label className="field"><span>{label}{required && " *"}</span>{children}</label>; }
function WizardNext({ busy, disabled, label, onClick }: { busy: boolean; disabled?: boolean; label: string; onClick?: () => void }) { return <button type={onClick ? "button" : "submit"} className="primary-button wizard-next" disabled={busy || disabled} onClick={onClick}>{busy ? "Saving…" : label}<Icon name="arrow_forward" /></button>; }
function UploadBox({ icon, title, multiple, onChange }: { icon: string; title: string; multiple?: boolean; onChange: (e: ChangeEvent<HTMLInputElement>) => void }) { return <label className="upload-box"><Icon name={icon} /><strong>{title}</strong><span>JPG, PNG or WebP</span><input hidden type="file" accept="image/*" multiple={multiple} onChange={onChange} /></label>; }
function priceSuffix(model: string) { return model === "PER_HOUR" ? "/hour" : model === "PER_GUEST" ? "/guest" : model === "STARTING_AT" ? " starting" : ""; }

function AddOnBuilder({ workspace, busy, run, onChange }: { workspace: VendorMe; busy: boolean; run: (a: () => Promise<void>) => Promise<void>; onChange: () => Promise<void> }) {
  const [title, setTitle] = useState(""); const [price, setPrice] = useState(""); const [packageId, setPackageId] = useState("");
  return <div className="sub-builder"><h3>Optional add-ons</h3><p>Offer clearly priced extras for one package or your whole storefront.</p><div className="form-grid three"><Field label="Title"><input className="form-input" value={title} onChange={(e) => setTitle(e.target.value)} /></Field><Field label={`Price (${workspace.profile.currency})`}><input className="form-input" inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} /></Field><Field label="Applies to"><select className="form-input" value={packageId} onChange={(e) => setPackageId(e.target.value)}><option value="">All packages</option>{workspace.packages.map((p) => <option key={p.id} value={p.id}>{p.title}</option>)}</select></Field></div><button className="ghost-button" disabled={busy || !title || !price} onClick={() => void run(async () => { const r = await api.POST("/api/v1/vendors/me/add-ons", { body: { packageId: packageId || undefined, title, priceCents: Math.round(Number(price) * 100), pricingModel: "FLAT" } }); if (!r.data) throw new Error(apiErrorMessage(r.error)); setTitle(""); setPrice(""); await onChange(); })}>Add priced extra</button>{workspace.addOns.map((item) => <div className="record-row" key={item.id}><div><strong>{item.title}</strong><span>{workspace.profile.currency} {(item.priceCents / 100).toFixed(2)}</span></div><button className="text-button danger" onClick={() => void run(async () => { await api.DELETE("/api/v1/vendors/me/add-ons/{addOnId}", { params: { path: { addOnId: item.id } } }); await onChange(); })}>Remove</button></div>)}</div>;
}

function FaqEditor({ workspace, busy, run, onChange }: { workspace: VendorMe; busy: boolean; run: (a: () => Promise<void>) => Promise<void>; onChange: () => Promise<void> }) {
  const [question, setQuestion] = useState(""); const [answer, setAnswer] = useState("");
  return <div className="sub-builder"><h3>Public FAQs <small>optional</small></h3><div className="form-grid two"><Field label="Question"><input className="form-input" value={question} onChange={(e) => setQuestion(e.target.value)} /></Field><Field label="Answer"><input className="form-input" value={answer} onChange={(e) => setAnswer(e.target.value)} /></Field></div><button className="ghost-button" disabled={busy || question.length < 5 || answer.length < 5 || workspace.faqs.length >= 12} onClick={() => void run(async () => { const r = await api.PUT("/api/v1/vendors/me/faqs", { body: { items: [...workspace.faqs, { question, answerMd: answer }] } }); if (!r.data) throw new Error(apiErrorMessage(r.error)); setQuestion(""); setAnswer(""); await onChange(); })}>Add FAQ</button>{workspace.faqs.map((faq, index) => <div className="record-row" key={faq.question}><div><strong>{faq.question}</strong><span>{faq.answerMd}</span></div><button className="text-button danger" onClick={() => void run(async () => { const r = await api.PUT("/api/v1/vendors/me/faqs", { body: { items: workspace.faqs.filter((_, i) => i !== index) } }); if (!r.data) throw new Error(apiErrorMessage(r.error)); await onChange(); })}>Remove</button></div>)}</div>;
}
