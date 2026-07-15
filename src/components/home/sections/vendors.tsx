import Image from "next/image";
import { FavoriteButton } from "@/components/home/favorite-button";
import { Icon } from "@/components/home/icon";
import { SectionHeading } from "@/components/home/section-heading";
import { vendors } from "@/data/home";

export function VendorsSection() {
  return (
    <section className="shell scroll-section pb-4" id="featured-vendors">
      <SectionHeading
        description="Highly rated professionals for your big day"
        title="Featured Vendors"
        viewAllHref="#hero-search"
      />
      <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
        {vendors.map((vendor, index) => (
          <article
            className={`editorial-card vendor-card group ${
              index === 1 ? "hidden sm:block" : index > 1 ? "hidden lg:block" : ""
            }`}
            key={vendor.name}
          >
            <div className="vendor-image">
              {vendor.image ? (
                <Image
                  alt={vendor.alt ?? vendor.name}
                  className="object-cover transition-transform duration-700 group-hover:scale-105"
                  fill
                  sizes="(min-width: 1024px) 25vw, (min-width: 640px) 50vw, 100vw"
                  src={vendor.image}
                />
              ) : (
                <div className="vendor-placeholder" />
              )}
              {vendor.rating && (
                <span className="rating-badge">
                  <Icon filled className="text-[16px]" name="star" /> {vendor.rating}
                </span>
              )}
              {vendor.image && <FavoriteButton vendorName={vendor.name} />}
            </div>
            <div className="vendor-copy">
              <h3>{vendor.name}</h3>
              <p>{vendor.category}</p>
              <div>
                {vendor.price} <span>{vendor.priceSuffix}</span>
              </div>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
