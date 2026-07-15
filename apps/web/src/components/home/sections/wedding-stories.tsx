import Image from "next/image";
import Link from "next/link";
import { Icon } from "@/components/home/icon";
import { SectionHeading } from "@/components/home/section-heading";
import { weddingStories } from "@/data/home";

export function WeddingStoriesSection() {
  return (
    <section className="shell scroll-section" id="real-weddings">
      <SectionHeading
        description="Get inspired by beautiful celebrations"
        title="Real Wedding Stories"
        viewAllHref="/inspiration?eventType=WEDDING"
      />
      <div className="grid gap-8 md:grid-cols-2 lg:grid-cols-3">
        {weddingStories.map((story, index) => (
          <Link
            className={`editorial-card story-card group ${index === 2 ? "hidden lg:flex" : "flex"}`}
            href="/inspiration?eventType=WEDDING"
            key={story.couple}
          >
            <div className="story-image">
              {story.image ? (
                <>
                  <Image
                    alt={story.alt ?? "Wedding celebration"}
                    className="object-cover transition-transform duration-700 group-hover:scale-105"
                    fill
                    sizes="(min-width: 1024px) 33vw, (min-width: 768px) 50vw, 100vw"
                    src={story.image}
                  />
                  {story.video ? (
                    <div className="video-overlay">
                      <span className="video-button">
                        <Icon filled className="text-[26px]" name="play_arrow" />
                      </span>
                    </div>
                  ) : (
                    <div className="image-soft-overlay" />
                  )}
                </>
              ) : (
                <>
                  <Icon filled className="text-5xl text-surface-variant" name="favorite" />
                  <span className="placeholder-wash" />
                </>
              )}
            </div>
            <div className="story-copy">
              <div>
                <h3>{story.couple}</h3>
                <p>{story.description}</p>
              </div>
              <span className="story-date">
                <Icon className="text-[16px]" name="calendar_today" /> {story.date}
              </span>
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}
