"use client";

import { FormEvent, useRef, useState } from "react";
import { cities, vendorTypes } from "@/data/home";
import { Icon } from "@/components/home/icon";

export function HeroSearch() {
  const [vendorType, setVendorType] = useState("");
  const [city, setCity] = useState("");
  const [announcement, setAnnouncement] = useState("");
  const vendorRef = useRef<HTMLSelectElement>(null);
  const cityRef = useRef<HTMLSelectElement>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!vendorType) {
      setAnnouncement("Choose a vendor type to continue.");
      vendorRef.current?.focus();
      return;
    }

    if (!city) {
      setAnnouncement("Choose a city to continue.");
      cityRef.current?.focus();
      return;
    }

    const vendorLabel = vendorTypes.find((item) => item.value === vendorType)?.label;
    const cityLabel = cities.find((item) => item.value === city)?.label;
    const url = new URL(window.location.href);
    url.searchParams.set("vendor", vendorType);
    url.searchParams.set("city", city);
    url.hash = "featured-vendors";
    window.history.replaceState({}, "", url);
    setAnnouncement(`Showing ${vendorLabel} near ${cityLabel}.`);
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    document
      .getElementById("featured-vendors")
      ?.scrollIntoView({ behavior: prefersReducedMotion ? "auto" : "smooth" });
  }

  return (
    <form
      aria-label="Find wedding vendors"
      className="hero-search-panel"
      id="hero-search"
      onSubmit={handleSubmit}
    >
      <label className="hero-search-field">
        <span className="sr-only">Vendor type</span>
        <Icon className="mr-3 text-[22px] text-brand-pink" name="storefront" />
        <select
          ref={vendorRef}
          className="hero-select"
          onChange={(event) => setVendorType(event.target.value)}
          value={vendorType}
        >
          <option value="">Select vendor type</option>
          {vendorTypes.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <Icon className="pointer-events-none text-[20px] text-secondary" name="expand_more" />
      </label>

      <span aria-hidden="true" className="hidden h-10 w-px bg-surface-variant md:block" />

      <label className="hero-search-field">
        <span className="sr-only">City</span>
        <Icon className="mr-3 text-[22px] text-brand-pink" name="location_on" />
        <select
          ref={cityRef}
          className="hero-select"
          onChange={(event) => setCity(event.target.value)}
          value={city}
        >
          <option value="">Select city</option>
          {cities.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <Icon className="pointer-events-none text-[20px] text-secondary" name="expand_more" />
      </label>

      <button className="primary-button hero-search-submit" type="submit">
        Get Started
        <Icon className="text-[19px] md:hidden" name="arrow_forward" />
      </button>
      <p aria-live="polite" className="sr-only">
        {announcement}
      </p>
    </form>
  );
}

