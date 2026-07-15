import Link from "next/link";
import { Icon } from "@/components/home/icon";

type SectionHeadingProps = {
  title: string;
  description: string;
  centered?: boolean;
  viewAllHref?: string;
};

export function SectionHeading({
  title,
  description,
  centered = false,
  viewAllHref,
}: SectionHeadingProps) {
  if (centered) {
    return (
      <div className="section-heading section-heading-centered">
        <h2>{title}</h2>
        <p>{description}</p>
      </div>
    );
  }

  return (
    <div className="section-heading-row">
      <div className="section-heading">
        <h2>{title}</h2>
        <p>{description}</p>
      </div>
      {viewAllHref && (
        <Link className="view-all-link" href={viewAllHref}>
          View all <Icon className="text-[17px]" name="arrow_forward" />
        </Link>
      )}
    </div>
  );
}

