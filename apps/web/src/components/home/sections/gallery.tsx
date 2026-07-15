import Image from "next/image";
import Link from "next/link";
import { Icon } from "@/components/home/icon";
import { SectionHeading } from "@/components/home/section-heading";
import { galleryItems, type GalleryItem } from "@/data/home";

const galleryClassNames: Record<GalleryItem["variant"], string> = {
  feature: "col-span-2 row-span-2",
  standard: "",
  wide: "col-span-2",
  square: "col-span-2 md:col-span-1",
};

export function GallerySection() {
  return (
    <section className="shell scroll-section" id="gallery">
      <SectionHeading
        centered
        description="Curated inspiration for every detail"
        title="Gallery to Look for"
      />
      <div className="gallery-grid">
        {galleryItems.map((item) => (
          <Link
            className={`gallery-item group ${galleryClassNames[item.variant]}`}
            href="#categories"
            key={item.title}
          >
            <Image
              alt={item.alt}
              className="object-cover transition-transform duration-700 group-hover:scale-105"
              fill
              sizes={
                item.variant === "feature" || item.variant === "wide"
                  ? "(min-width: 768px) 50vw, 100vw"
                  : "(min-width: 768px) 25vw, 50vw"
              }
              src={item.image}
            />
            {item.variant === "feature" ? (
              <span className="gallery-feature-copy">
                <span className="gallery-eyebrow">{item.eyebrow}</span>
                <strong>{item.title}</strong>
                <span className="gallery-description">
                  {item.description} <Icon className="text-[16px]" name="arrow_forward" />
                </span>
              </span>
            ) : item.variant === "wide" ? (
              <span className="gallery-wide-copy">
                <strong>{item.title}</strong>
              </span>
            ) : (
              <span className="gallery-standard-copy">
                <strong>{item.title}</strong>
              </span>
            )}
          </Link>
        ))}
      </div>
    </section>
  );
}
