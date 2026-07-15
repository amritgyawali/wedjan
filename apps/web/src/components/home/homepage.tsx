import { Footer } from "@/components/home/footer";
import { CategoriesSection } from "@/components/home/sections/categories";
import { GallerySection } from "@/components/home/sections/gallery";
import { HeroSection } from "@/components/home/sections/hero";
import { ServicesSection } from "@/components/home/sections/services";
import { VendorsSection } from "@/components/home/sections/vendors";
import { VenuesSection } from "@/components/home/sections/venues";
import { WeddingStoriesSection } from "@/components/home/sections/wedding-stories";

export function Homepage() {
  return (
    <>
      <HeroSection />
      <main className="home-main">
        <VenuesSection />
        <ServicesSection />
        <CategoriesSection />
        <WeddingStoriesSection />
        <GallerySection />
        <VendorsSection />
      </main>
      <Footer />
    </>
  );
}
