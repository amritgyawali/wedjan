"use client";

import { useState } from "react";
import { Icon } from "@/components/home/icon";

type FavoriteButtonProps = {
  vendorName: string;
};

export function FavoriteButton({ vendorName }: FavoriteButtonProps) {
  const [isFavorite, setIsFavorite] = useState(false);

  return (
    <button
      aria-label={`${isFavorite ? "Remove" : "Add"} ${vendorName} ${
        isFavorite ? "from" : "to"
      } favorites`}
      aria-pressed={isFavorite}
      className="absolute right-3 top-3 z-10 grid size-10 place-items-center rounded-full bg-white/90 text-secondary shadow-sm backdrop-blur-md transition hover:text-brand-pink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-pink"
      onClick={() => setIsFavorite((current) => !current)}
      type="button"
    >
      <Icon filled={isFavorite} name="favorite" className="text-[20px]" />
    </button>
  );
}

