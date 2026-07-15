import { ImageResponse } from "next/og";
/* eslint-disable @next/next/no-img-element -- ImageResponse renders runtime vendor media */
import type { VendorPublic } from "@wedjan/shared";

export const alt = "wedjan vendor profile";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default async function Image({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const response = await fetch(`${process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"}/api/v1/vendors/${slug}`);
  const vendor = response.ok ? await response.json() as VendorPublic : null;
  const cover = vendor?.media.find((x) => x.kind === "COVER")?.url;
  const starting = vendor?.packages.length ? Math.min(...vendor.packages.map((p) => p.priceCents)) : 0;
  return new ImageResponse(<div style={{ width: "100%", height: "100%", display: "flex", position: "relative", alignItems: "flex-end", padding: 70, color: "white", background: "linear-gradient(135deg,#8f0043,#e72e77)", fontFamily: "sans-serif" }}>{cover && <img src={cover} alt="" style={{ position: "absolute", inset: 0, width: "100%", height: "100%", objectFit: "cover" }} />}<div style={{ position: "absolute", inset: 0, background: "linear-gradient(0deg,rgba(0,0,0,.82),rgba(0,0,0,.1))" }} /><div style={{ display: "flex", flexDirection: "column", position: "relative" }}><span style={{ fontSize: 28, color: "#ffb1cf" }}>wedjan · verified vendor</span><strong style={{ fontSize: 72, marginTop: 12 }}>{vendor?.businessName ?? "wedjan vendor"}</strong><span style={{ fontSize: 30, marginTop: 15 }}>{vendor ? `${vendor.baseCity} · Packages from ${new Intl.NumberFormat("en", { style: "currency", currency: vendor.currency, maximumFractionDigits: 0 }).format(starting / 100)}` : "Visible prices. Verified work."}</span></div></div>, size);
}
