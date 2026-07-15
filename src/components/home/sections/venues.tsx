import Image from "next/image";
import Link from "next/link";
import { SectionHeading } from "@/components/home/section-heading";
import { venues } from "@/data/home";

export function VenuesSection() {
  return (
    <section className="shell scroll-section" id="venues">
      <SectionHeading
        description="Discover top-rated venues for your special day"
        title="Popular Venue Searches"
        viewAllHref="#hero-search"
      />
      <div className="grid gap-8 md:grid-cols-3">
        {venues.map((venue) => (
          <Link className="editorial-card group" href="#hero-search" key={venue.title}>
            <div className="relative h-56 overflow-hidden bg-surface-gray">
              <Image
                alt={venue.alt}
                className="object-cover transition-transform duration-700 group-hover:scale-105"
                fill
                sizes="(min-width: 768px) 33vw, 100vw"
                src={venue.image}
              />
              <div className="image-soft-overlay" />
            </div>
            <div className="card-copy">
              <h3>{venue.title}</h3>
              <p>{venue.locations}</p>
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}
