import Image from "next/image";
import Link from "next/link";
import { SectionHeading } from "@/components/home/section-heading";
import { categories } from "@/data/home";

export function CategoriesSection() {
  return (
    <section className="category-band scroll-section" id="categories">
      <div className="shell">
        <SectionHeading
          centered
          description="Find exactly what you're looking for"
          title="Explore Categories"
        />
        <div className="flex flex-wrap justify-center gap-8 lg:gap-10">
          {categories.map((category) => (
            <Link className="category-link group" href="#featured-vendors" key={category.title}>
              <span className="category-image">
                <Image
                  alt={category.alt}
                  className="object-cover transition-transform duration-700 group-hover:scale-110"
                  fill
                  sizes="176px"
                  src={category.image}
                />
                <span aria-hidden="true" className="image-soft-overlay" />
              </span>
              <span>{category.title}</span>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}
