"use client";

/* eslint-disable @next/next/no-img-element -- media host is runtime configured */
import Link from "next/link";
import { type FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import type { VendorSearchResponse } from "@wedjan/shared";
import { api } from "@/lib/api";
import styles from "../discovery.module.css";
import searchStyles from "./search.module.css";

interface SearchResultState {
  key: string;
  data?: VendorSearchResponse;
  error: string;
}

export function SearchExperience() {
  const params = useSearchParams();
  const router = useRouter();
  const key = params.toString();
  const selectedDate = params.get("date") ?? "";
  const [result, setResult] = useState<SearchResultState>({ key: "", error: "" });
  const [compare, setCompare] = useState<string[]>([]);

  useEffect(() => {
    let active = true;
    const query = Object.fromEntries(new URLSearchParams(key).entries());
    void api.GET("/api/v1/search/vendors", { params: { query } }).then(({ data, error }) => {
      if (!active) return;
      setResult({
        key,
        data,
        error: error ? "Search is temporarily unavailable." : "",
      });
    });
    return () => {
      active = false;
    };
  }, [key]);

  const loading = result.key !== key;
  const data = result.key === key ? result.data : undefined;
  const error = result.key === key ? result.error : "";
  const total = data?.total ?? 0;
  const selected = useMemo(() => new Set(compare), [compare]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const next = new URLSearchParams(params.toString());

    for (const name of new Set(Array.from(values.keys(), String))) {
      const value = String(values.get(name) ?? "").trim();
      if (value) next.set(name, value);
      else next.delete(name);
    }

    next.delete("cursor");
    router.push(searchPath(next));
  }

  function suggestion(type: string, value: string) {
    let next = new URLSearchParams(params.toString());
    if (type === "NEARBY_CITY") next.set("city", value);
    else if (type === "ADJACENT_CATEGORY") next.set("category", value);
    else if (type === "WIDEN_PRICE") {
      next.delete("price_min");
      next.delete("price_max");
    } else {
      next = new URLSearchParams();
    }
    next.delete("cursor");
    router.push(searchPath(next));
  }

  async function addToShortlist(vendorId: string, packageId: string) {
    let list = (await api.GET("/api/v1/discovery/shortlists")).data?.items[0];
    if (!list) {
      list = (
        await api.POST("/api/v1/discovery/shortlists", { body: { name: "My event" } })
      ).data;
    }
    if (!list) {
      router.push(`/login?next=${encodeURIComponent(searchPath(new URLSearchParams(key)))}`);
      return;
    }
    const response = await api.POST(
      "/api/v1/discovery/shortlists/{shortlistId}/items",
      {
        params: { path: { shortlistId: list.id } },
        body: { vendorId, packageId },
      },
    );
    if (response.error) {
      router.push(`/login?next=${encodeURIComponent(searchPath(new URLSearchParams(key)))}`);
      return;
    }
    window.alert(`Added to ${list.name}`);
  }

  return (
    <div className={styles.page}>
      <DiscoveryHeader />
      <main className={styles.shell}>
        <section className={styles.hero}>
          <h1>Find the right team, with real prices.</h1>
          <p>
            Search verified vendors, combine every filter, and compare transparent packages
            before you reach out.
          </p>
        </section>

        <form
          className={`${styles.searchForm} ${searchStyles.searchWithDate}`}
          key={`search-${key}`}
          onSubmit={submit}
        >
          <input
            aria-label="Search terms"
            className={styles.input}
            name="q"
            defaultValue={params.get("q") ?? ""}
            placeholder="Try “photographer”"
          />
          <input
            aria-label="Vendor category"
            className={styles.input}
            name="category"
            defaultValue={params.get("category") ?? ""}
            placeholder="Category slug"
          />
          <input
            aria-label="Event city"
            className={styles.input}
            name="city"
            defaultValue={params.get("city") ?? ""}
            placeholder="City"
          />
          <input
            aria-label="Event date"
            className={styles.input}
            name="date"
            type="date"
            defaultValue={selectedDate}
          />
          <button className={styles.button}>Search</button>
        </form>

        <div className={styles.layout}>
          <form className={styles.filters} key={`filters-${key}`} onSubmit={submit}>
            <h2>Refine results</h2>
            <label htmlFor="search-price-min">Minimum price</label>
            <input
              className={styles.input}
              id="search-price-min"
              name="price_min"
              type="number"
              defaultValue={params.get("price_min") ?? ""}
            />
            <label htmlFor="search-price-max">Maximum price</label>
            <input
              className={styles.input}
              id="search-price-max"
              name="price_max"
              type="number"
              defaultValue={params.get("price_max") ?? ""}
            />
            <label htmlFor="search-guests">Guests</label>
            <input
              className={styles.input}
              id="search-guests"
              name="guests"
              type="number"
              min="1"
              defaultValue={params.get("guests") ?? ""}
            />
            <label htmlFor="search-booking-mode">Booking mode</label>
            <select
              className={styles.select}
              id="search-booking-mode"
              name="booking_mode"
              defaultValue={params.get("booking_mode") ?? ""}
            >
              <option value="">Any</option>
              <option value="INSTANT">Instant</option>
              <option value="REQUEST">Request</option>
            </select>
            <label htmlFor="search-sort">Sort</label>
            <select
              className={styles.select}
              id="search-sort"
              name="sort"
              defaultValue={params.get("sort") ?? "RELEVANCE"}
            >
              <option value="RELEVANCE">Best match</option>
              <option value="PRICE_ASC">Price: low to high</option>
              <option value="PRICE_DESC">Price: high to low</option>
              <option value="NEWEST">Newest</option>
            </select>
            <button className={styles.button}>Apply</button>
          </form>

          <section>
            <div className={styles.resultsHead}>
              <h2>{loading ? "Searching…" : `${total} vendor${total === 1 ? "" : "s"}`}</h2>
              <span>{data?.nextCursor ? "More matches available" : "All matches shown"}</span>
            </div>
            {error && <div className={styles.empty}>{error}</div>}
            {!loading && data?.items.length === 0 && (
              <div className={styles.empty}>
                <h3>No exact matches yet</h3>
                <p>Try one of these server-computed relaxations.</p>
                <div className={styles.suggestions}>
                  {data.suggestions.map((item) => (
                    <button
                      key={`${item.type}-${item.value}`}
                      type="button"
                      onClick={() => suggestion(item.type, item.value)}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
              </div>
            )}

            <div className={styles.grid}>
              {data?.items.map((card) => (
                <article className={styles.card} key={card.vendorId}>
                  {card.coverUrl ? (
                    <img className={styles.cardImage} src={card.coverUrl} alt="" />
                  ) : (
                    <div className={`${styles.cardImage} ${styles.placeholder}`}>◇</div>
                  )}
                  <div className={styles.cardBody}>
                    <h3>
                      <Link href={vendorPath(card.slug, card.cheapestPackage.id, selectedDate)}>{card.businessName}</Link>
                    </h3>
                    <p>
                      {card.categories.join(" · ")} · {card.city}
                    </p>
                    <strong className={styles.price}>
                      {money(
                        card.cheapestPackage.priceCents,
                        card.cheapestPackage.currency,
                      )}
                    </strong>
                    <AvailableDates
                      dates={card.nextAvailableDates}
                      selectedDate={selectedDate}
                    />
                    <div className={styles.chips}>
                      {card.badges.map((badge) => (
                        <span className={styles.chip} key={badge}>
                          {label(badge)}
                        </span>
                      ))}
                      <span className={styles.chip}>
                        {label(card.cheapestPackage.bookingMode)}
                      </span>
                    </div>
                    <div className={styles.cardActions}>
                      <Link href={vendorPath(card.slug, card.cheapestPackage.id, selectedDate)}>View</Link>
                      <button
                        type="button"
                        onClick={async () => {
                          const { error: favoriteError } = await api.POST(
                            "/api/v1/discovery/favorites",
                            { body: { entityType: "VENDOR", entityId: card.vendorId } },
                          );
                          if (favoriteError) {
                            router.push(
                              `/login?next=${encodeURIComponent(searchPath(new URLSearchParams(key)))}`,
                            );
                          }
                        }}
                      >
                        ♡ Save
                      </button>
                      <button
                        type="button"
                        onClick={() => void addToShortlist(card.vendorId, card.cheapestPackage.id)}
                      >
                        + List
                      </button>
                      <button
                        type="button"
                        aria-pressed={selected.has(card.cheapestPackage.id)}
                        onClick={() =>
                          setCompare((current) =>
                            current.includes(card.cheapestPackage.id)
                              ? current.filter((id) => id !== card.cheapestPackage.id)
                              : current.length < 4
                                ? [...current, card.cheapestPackage.id]
                                : current,
                          )
                        }
                      >
                        {selected.has(card.cheapestPackage.id) ? "✓ Compare" : "Compare"}
                      </button>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </section>
        </div>

        {compare.length > 0 && (
          <div className={styles.compareBar}>
            <span>{compare.length}/4 selected</span>
            <Link className={styles.button} href={`/compare?packages=${compare.join(",")}`}>
              Compare packages
            </Link>
          </div>
        )}
      </main>
    </div>
  );
}

function AvailableDates({ dates, selectedDate }: { dates: string[]; selectedDate: string }) {
  const nextDates = Array.from(new Set(dates)).slice(0, 3);
  if (!selectedDate && nextDates.length === 0) return null;

  return (
    <div className={searchStyles.availability}>
      <strong>
        {selectedDate ? `Available ${formatDate(selectedDate)}` : "Next available"}
      </strong>
      {nextDates.length > 0 && (
        <div className={searchStyles.availableDates} aria-label="Next available dates">
          {nextDates.map((date) => (
            <time dateTime={date} key={date}>
              {formatDate(date)}
            </time>
          ))}
        </div>
      )}
    </div>
  );
}

export function DiscoveryHeader() {
  return (
    <header className={styles.header}>
      <Link className={styles.wordmark} href="/">
        wedjan
      </Link>
      <nav className={styles.nav}>
        <Link href="/search">Vendors</Link>
        <Link href="/inspiration">Inspiration</Link>
        <Link href="/login">Sign in</Link>
      </nav>
    </header>
  );
}

function searchPath(params: URLSearchParams): string {
  const query = params.toString();
  return query ? `/search?${query}` : "/search";
}

function vendorPath(slug: string, packageId: string, date: string): string {
  const query = new URLSearchParams({ package: packageId });
  if (date) query.set("date", date);
  return `/v/${encodeURIComponent(slug)}?${query}`;
}

function formatDate(value: string): string {
  const [year, month, day] = value.split("-").map(Number);
  if (!year || !month || !day) return value;
  return new Intl.DateTimeFormat("en", { day: "numeric", month: "short" }).format(
    new Date(year, month - 1, day),
  );
}

function money(cents: number, currency: string) {
  return new Intl.NumberFormat("en", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(cents / 100);
}

function label(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/^./, (first) => first.toUpperCase());
}
