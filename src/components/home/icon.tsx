import type { CSSProperties } from "react";

type IconProps = {
  name: string;
  className?: string;
  filled?: boolean;
};

export function Icon({ name, className = "", filled = false }: IconProps) {
  const style = {
    "--icon-fill": filled ? 1 : 0,
  } as CSSProperties;

  return (
    <span
      aria-hidden="true"
      className={`material-symbols-outlined icon ${className}`}
      style={style}
    >
      {name}
    </span>
  );
}

