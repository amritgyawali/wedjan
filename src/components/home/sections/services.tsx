import Image from "next/image";
import Link from "next/link";
import { Icon } from "@/components/home/icon";
import { SectionHeading } from "@/components/home/section-heading";
import { services } from "@/data/home";

export function ServicesSection() {
  return (
    <section className="shell scroll-section" id="services">
      <SectionHeading
        centered
        description="Expert services designed to make your wedding planning seamless"
        title="wedjan In-house Services"
      />
      <div className="grid gap-8 md:grid-cols-2">
        {services.map((service) => (
          <article className="editorial-card service-card group" key={service.title}>
            <div className="relative h-56 overflow-hidden bg-surface-gray sm:h-64">
              <Image
                alt={service.alt}
                className="object-cover transition-transform duration-700 group-hover:scale-105"
                fill
                sizes="(min-width: 768px) 50vw, 100vw"
                src={service.image}
              />
            </div>
            <div className="service-copy">
              <div>
                <h3>{service.title}</h3>
                <p>{service.description}</p>
              </div>
              <Link className="text-link" href="#featured-vendors">
                Know More <Icon className="text-[17px]" name="arrow_forward" />
              </Link>
            </div>
          </article>
        ))}
      </div>

      <article className="editorial-card venue-service-card group mt-8">
        <div className="relative h-64 overflow-hidden bg-surface-gray md:h-auto md:w-1/2">
          <Image
            alt="An elegant indoor wedding venue with floral table settings"
            className="object-cover transition-transform duration-700 group-hover:scale-105"
            fill
            sizes="(min-width: 768px) 50vw, 100vw"
            src="/images/wedjan/service-venue.png"
          />
        </div>
        <div className="venue-service-copy">
          <h3>Venue Booking Service</h3>
          <p>Best price guaranteed on premium locations</p>
          <Link className="secondary-button" href="#venues">
            Explore Venues
          </Link>
        </div>
      </article>
    </section>
  );
}
