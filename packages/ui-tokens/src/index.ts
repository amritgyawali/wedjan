/**
 * wedjan design tokens — single source of truth for web (Tailwind v4 via
 * tokens.css) and mobile (NativeWind / StyleSheet via this object).
 *
 * Derived from the approved, frozen homepage design system
 * (apps/web/src/app/globals.css). Do not invent new brand values here;
 * extend only in the same visual language.
 */

export const colors = {
  background: "#f9f9f9",
  surface: "#ffffff",
  surfaceGray: "#f4f4f4",
  surfaceVariant: "#e2e2e2",
  onSurface: "#1a1c1c",
  secondary: "#5e5e5e",
  textMuted: "#757575",
  brandPink: "#e72e77",
  brandPinkDark: "#d12166",
  primaryDeep: "#8f0043",
  pinkSoft: "#fff0f4",
  tertiary: "#006b20",
  pureWhite: "#ffffff",
  /** Feedback colors (new surfaces only — homepage untouched) */
  success: "#006b20",
  warning: "#a15c00",
  danger: "#b3261e",
  dangerSoft: "#fdeceb",
} as const;

/** Dark-scheme counterparts for new surfaces (homepage remains light-only). */
export const colorsDark = {
  background: "#141416",
  surface: "#1d1d20",
  surfaceGray: "#232326",
  surfaceVariant: "#3a3a3e",
  onSurface: "#f2f2f2",
  secondary: "#b3b3b3",
  textMuted: "#8f8f8f",
  brandPink: "#ff5c98",
  brandPinkDark: "#e72e77",
  primaryDeep: "#ffb1cf",
  pinkSoft: "#3a1524",
  tertiary: "#63d381",
  pureWhite: "#ffffff",
  success: "#63d381",
  warning: "#ffb95c",
  danger: "#ff8a80",
  dangerSoft: "#3c1513",
} as const;

export const radii = {
  sm: 6,
  md: 10,
  lg: 12,
  xl: 16,
  "2xl": 24,
  pill: 999,
} as const;

/** 4px-based spacing scale used across all new surfaces. */
export const spacing = {
  "0": 0,
  "1": 4,
  "2": 8,
  "3": 12,
  "4": 16,
  "5": 20,
  "6": 24,
  "7": 28,
  "8": 32,
  "10": 40,
  "12": 48,
  "16": 64,
  "20": 80,
  "24": 96,
} as const;

export const typography = {
  /** Large editorial serif for headlines (Playfair Display on web). */
  display: {
    family: "Playfair Display",
    /** Devanagari-capable fallback stack (Kathmandu ↔ Melbourne corridor). */
    stack:
      '"Playfair Display", "Noto Serif Devanagari", Georgia, "Times New Roman", serif',
    weights: { regular: 400, semibold: 600, bold: 700 },
  },
  /** Clean grotesk for UI (Plus Jakarta Sans on web). */
  body: {
    family: "Plus Jakarta Sans",
    stack:
      '"Plus Jakarta Sans", "Noto Sans Devanagari", Arial, "Helvetica Neue", sans-serif',
    weights: { regular: 400, medium: 500, semibold: 600, bold: 700 },
  },
  sizes: {
    xs: 12,
    sm: 14,
    base: 16,
    lg: 18,
    xl: 20,
    "2xl": 24,
    "3xl": 30,
    "4xl": 40,
    display: 56,
  },
  lineHeights: {
    tight: 1.1,
    snug: 1.2,
    normal: 1.5,
    relaxed: 1.65,
  },
} as const;

export const shadows = {
  ambient: "0 10px 30px rgba(0, 0, 0, 0.05)",
  ambientHover: "0 15px 40px rgba(0, 0, 0, 0.08)",
  brand: "0 5px 15px rgba(231, 46, 119, 0.24)",
  brandHover: "0 8px 20px rgba(231, 46, 119, 0.3)",
  dialog: "0 28px 80px rgba(0, 0, 0, 0.2)",
} as const;

export const layout = {
  maxContentWidth: 1280,
  pageGutter: 24,
  pageGutterDesktop: 80,
  headerHeight: 80,
  headerHeightMobile: 64,
  minTouchTarget: 44,
} as const;

export const tokens = {
  colors,
  colorsDark,
  radii,
  spacing,
  typography,
  shadows,
  layout,
} as const;

export type Tokens = typeof tokens;
export default tokens;
