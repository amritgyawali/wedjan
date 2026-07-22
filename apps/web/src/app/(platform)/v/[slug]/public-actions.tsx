"use client";

import { useEffect, useState } from "react";

export function PublicActions({ slug }: { slug: string }) {
  const [favorite, setFavorite] = useState(false);
  const [intent, setIntent] = useState<"message" | null>(null);
  useEffect(() => {
    const timer = window.setTimeout(
      () => setFavorite(localStorage.getItem(`wedjan.favorite.${slug}`) === "true"),
      0,
    );
    return () => window.clearTimeout(timer);
  }, [slug]);
  const toggle = () => { const next = !favorite; setFavorite(next); localStorage.setItem(`wedjan.favorite.${slug}`, String(next)); };
  const open = (value: "message") => {
    setIntent(value);
    const events = JSON.parse(localStorage.getItem("wedjan.intentEvents") ?? "[]") as unknown[];
    localStorage.setItem("wedjan.intentEvents", JSON.stringify([...events, { slug, intent: value, at: new Date().toISOString() }]));
  };
  return <>
    <div className="publicActions"><button onClick={() => document.getElementById("booking")?.scrollIntoView({behavior:"smooth",block:"start"})}>Check availability</button><button className="secondary" onClick={() => open("message")}>Message vendor</button><button className="favorite" aria-label="Favorite vendor" aria-pressed={favorite} onClick={toggle}>{favorite ? "♥" : "♡"}</button></div>
    {intent && <div className="intentBackdrop" role="presentation" onClick={() => setIntent(null)}><div className="intentModal" role="dialog" aria-modal="true" aria-labelledby="message-intent-title" onClick={(e) => e.stopPropagation()}><button aria-label="Close message dialog" className="close" onClick={() => setIntent(null)}>×</button><span>Coming soon</span><h2 id="message-intent-title">Messaging is almost here</h2><p>We saved your interest. This flow opens when messaging launches in Phase 6.</p><button onClick={() => setIntent(null)}>Got it</button></div></div>}
  </>;
}
