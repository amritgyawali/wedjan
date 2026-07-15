import Image from "next/image";
import Link from "next/link";
import { HeroSearch } from "@/components/home/hero-search";

export function HeroSection() {
  return (
    <section className="hero" aria-labelledby="hero-title">
      <Image
        alt="A newly married couple celebrating together beneath falling petals"
        className="object-cover object-center"
        fill
        priority
        quality={92}
        sizes="100vw"
        src="/images/wedjan/hero.png"
      />
      <div aria-hidden="true" className="hero-overlay" />
      <div className="shell hero-content">
        <h1 id="hero-title">Your Wedding, Your Way</h1>
        <p className="hero-subtitle">
          Find the best wedding vendors with thousands of trusted reviews
        </p>
        <HeroSearch />
        <div className="popular-searches">
          <span>Popular Searches:</span>
          <Link href="#featured-vendors">Wedding Photographers in India</Link>
          <i aria-hidden="true">|</i>
          <Link href="#featured-vendors">Bridal Makeup Artists in India</Link>
          <i aria-hidden="true">|</i>
          <Link href="#categories">Wedding Cards in India</Link>
        </div>
      </div>
    </section>
  );
}
