"use client";

import { useEffect, useState } from "react";

export function PublicActions({ slug }: { slug: string }) {
  const [favorite, setFavorite] = useState(false);
  const [intent, setIntent] = useState<"availability" | "message" | null>(null);
  useEffect(() => {
    const timer = window.setTimeout(
      () => setFavorite(localStorage.getItem(`wedjan.favorite.${slug}`) === "true"),
      0,
    );
    return () => window.clearTimeout(timer);
  }, [slug]);
  const toggle = () => { const next = !favorite; setFavorite(next); localStorage.setItem(`wedjan.favorite.${slug}`, String(next)); };
  const open = (value: "availability" | "message") => {
    setIntent(value);
    const events = JSON.parse(localStorage.getItem("wedjan.intentEvents") ?? "[]") as unknown[];
    localStorage.setItem("wedjan.intentEvents", JSON.stringify([...events, { slug, intent: value, at: new Date().toISOString() }]));
  };
  return <>
    <div className="publicActions"><button onClick={() => open("availability")}>Check availability</button><button className="secondary" onClick={() => open("message")}>Message vendor</button><button className="favorite" aria-label="Favorite vendor" aria-pressed={favorite} onClick={toggle}>{favorite ? "♥" : "♡"}</button></div>
    {intent && <div className="intentBackdrop" role="presentation" onClick={() => setIntent(null)}><div className="intentModal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}><button className="close" onClick={() => setIntent(null)}>×</button><span>Coming soon</span><h2>{intent === "availability" ? "Availability is almost here" : "Messaging is almost here"}</h2><p>We saved your interest. This flow opens when {intent === "availability" ? "booking launches in Phase 4" : "messaging launches in Phase 6"}.</p><button onClick={() => setIntent(null)}>Got it</button></div></div>}
  </>;
}
