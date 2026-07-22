import type { Metadata } from "next";
/* eslint-disable @next/next/no-img-element -- vendor R2 hosts are runtime-configured */
import Link from "next/link";
import { notFound } from "next/navigation";
import type { VendorPublic } from "@wedjan/shared";
import { BookingWidget } from "@/components/marketplace/booking/booking-widget";
import { PublicActions } from "./public-actions";
import styles from "./profile.module.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const WEB_URL = process.env.NEXT_PUBLIC_WEB_URL ?? "http://localhost:3000";

async function getVendor(slug: string): Promise<VendorPublic | null> {
  const response = await fetch(`${API_URL}/api/v1/vendors/${encodeURIComponent(slug)}`, { next: { revalidate: 300 } });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error("Vendor profile unavailable");
  return response.json() as Promise<VendorPublic>;
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const vendor = await getVendor((await params).slug);
  if (!vendor) return { title: "Vendor not found — wedjan" };
  const from = vendor.packages.length ? formatPrice(Math.min(...vendor.packages.map((p) => p.priceCents)), vendor.currency) : "";
  return { title: `${vendor.businessName} — ${vendor.baseCity} | wedjan`, description: vendor.tagline ?? vendor.about?.slice(0, 155), openGraph: { title: vendor.businessName, description: `${vendor.baseCity}${from ? ` · Packages from ${from}` : ""}`, images: [{ url: `/v/${vendor.slug}/opengraph-image` }] } };
}

export default async function VendorProfilePage({ params }: { params: Promise<{ slug: string }> }) {
  const vendor = await getVendor((await params).slug); if (!vendor) notFound();
  const cover = vendor.media.find((x) => x.kind === "COVER"); const logo = vendor.media.find((x) => x.kind === "LOGO"); const gallery = vendor.media.filter((x) => x.kind === "GALLERY");
  const jsonLd = { "@context": "https://schema.org", "@type": "LocalBusiness", name: vendor.businessName, description: vendor.about, url: `${WEB_URL}/v/${vendor.slug}`, image: cover?.url, address: { "@type": "PostalAddress", addressLocality: vendor.baseCity, addressCountry: vendor.baseCountry }, knowsLanguage: vendor.languages, makesOffer: vendor.packages.map((pkg) => ({ "@type": "Offer", price: (pkg.priceCents / 100).toFixed(2), priceCurrency: pkg.currency, itemOffered: { "@type": "Service", name: pkg.title, description: pkg.descriptionMd } })) };
  const faqJsonLd = vendor.faqs.length ? { "@context": "https://schema.org", "@type": "FAQPage", mainEntity: vendor.faqs.map((faq) => ({ "@type": "Question", name: faq.question, acceptedAnswer: { "@type": "Answer", text: faq.answerMd } })) } : null;
  return <div className={styles.page}><header className={styles.header}><Link className={styles.wordmark} href="/">wedjan</Link><Link className={styles.back} href="/">← Explore wedjan</Link></header>
    <section className={styles.hero}>{cover?.url && <img className={styles.heroImage} src={cover.url} alt="" />}<div className={styles.heroContent}><div>{<div className={styles.logo}>{logo?.url ? <img src={logo.url} alt={`${vendor.businessName} logo`} /> : vendor.businessName.slice(0, 1)}</div>}<h1>{vendor.businessName}</h1><p>{vendor.categories[0]?.name} · {vendor.baseCity}, {vendor.baseCountry}</p><div className={styles.badges}>{vendor.badges.map((b) => <span className={styles.badge} key={b.badge}>✓ {badgeName(b.badge)}</span>)}<span className={styles.badge}>Responds in ~1 day</span>{vendor.tagline && <span className={styles.badge}>{vendor.tagline}</span>}</div></div><PublicActions slug={vendor.slug} /></div></section>
    <div className={styles.content}><main className={styles.main}><section className={styles.section}><h2>About</h2><p>{vendor.about}</p>{vendor.languages?.length ? <div className={styles.chips}>{vendor.languages.map((l) => <span key={l}>{l}</span>)}</div> : null}</section>
      {gallery.length > 0 && <section className={styles.section}><h2>Selected work</h2><div className={styles.gallery}>{gallery.map((item) => item.url && <a href={item.url} target="_blank" key={item.id}><img src={item.url} alt={item.caption ?? `${vendor.businessName} portfolio`} /></a>)}</div></section>}
      <section className={styles.section}><h2>Packages</h2><div className={styles.packages}>{vendor.packages.map((pkg) => <article className={styles.package} key={pkg.id}><div className={styles.packageImage}>{pkg.coverUrl ? <img src={pkg.coverUrl} alt="" /> : "◇"}</div><div className={styles.packageBody}><h3>{pkg.title}</h3><p>{pkg.descriptionMd}</p><span className={styles.price}>{formatPrice(pkg.priceCents, pkg.currency)}{priceSuffix(pkg.pricingModel)}</span><div className={styles.chips}><span>{pkg.depositPct}% deposit</span><span>{pkg.cancellationPolicy.toLowerCase()} cancellation</span>{pkg.bookingMode === "INSTANT" && <span>⚡ Instant Book</span>}</div></div></article>)}</div></section>
      {vendor.addOns?.length ? <section className={styles.section}><h2>Optional add-ons</h2><div className={styles.addOns}>{vendor.addOns.map((item) => <div className={styles.addOn} key={item.id}><strong>{item.title}</strong><span>+{formatPrice(item.priceCents, vendor.currency)}{priceSuffix(item.pricingModel)}</span></div>)}</div></section> : null}
      {vendor.faqs.length > 0 && <section className={styles.section}><h2>Frequently asked questions</h2>{vendor.faqs.map((faq) => <details className={styles.faq} key={faq.question}><summary>{faq.question}</summary><p>{faq.answerMd}</p></details>)}</section>}
      <div className={styles.reviews}><strong>New on wedjan</strong><span>Verified work first. Reviews appear after the first completed booking.</span></div></main>
      <aside className={styles.aside}><div className={styles.sticky}><BookingWidget vendorSlug={vendor.slug} packages={vendor.packages} addOns={vendor.addOns ?? []}/><div className={styles.card}><h2>Service area</h2>{vendor.serviceAreas.map((area) => <div className={styles.area} key={area.id}><span className={styles.areaIcon}>●</span><div><strong>{area.city}, {area.country}</strong><span>{area.mode === "CITY_RADIUS" ? `Within ${area.radiusKm} km` : "Regional service"}{area.travelFeeCents ? ` · ${formatPrice(area.travelFeeCents, vendor.currency)} travel fee` : ""}</span></div></div>)}<div className={styles.map}>⌖</div></div><div className={styles.card}><h3>Transparent from first click</h3><p>Every package has a real price. No “price on request.”</p></div></div></aside></div>
    <footer className={styles.footer}>Verified vendors. Visible prices. Protected booking from first click.</footer><script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, "\\u003c") }} />{faqJsonLd&&<script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(faqJsonLd).replace(/</g, "\\u003c") }} />}</div>;
}

function formatPrice(cents: number, currency: string) { return new Intl.NumberFormat("en", { style: "currency", currency, maximumFractionDigits: 0 }).format(cents / 100); }
function priceSuffix(model: string) { return model === "PER_HOUR" ? "/hour" : model === "PER_GUEST" ? "/guest" : model === "STARTING_AT" ? " starting" : ""; }
function badgeName(value: string) { return value.toLowerCase().split("_").map((x) => x[0].toUpperCase() + x.slice(1)).join(" "); }
