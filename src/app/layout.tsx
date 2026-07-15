import type { Metadata, Viewport } from "next";
import { Playfair_Display, Plus_Jakarta_Sans } from "next/font/google";
import "material-symbols/outlined.css";
import "./globals.css";

const playfair = Playfair_Display({
  subsets: ["latin"],
  variable: "--font-playfair",
  display: "swap",
});

const jakarta = Plus_Jakarta_Sans({
  subsets: ["latin"],
  variable: "--font-jakarta",
  display: "swap",
});

export const metadata: Metadata = {
  title: "wedjan — Your Wedding, Your Way",
  description:
    "Discover trusted wedding venues, vendors, real celebrations, and beautifully curated inspiration with wedjan.",
  applicationName: "wedjan",
  keywords: ["wedding planning", "wedding vendors", "wedding venues", "wedjan"],
  openGraph: {
    title: "wedjan — Your Wedding, Your Way",
    description: "Plan your celebration with trusted wedding vendors and curated inspiration.",
    siteName: "wedjan",
    type: "website",
  },
};

export const viewport: Viewport = {
  themeColor: "#e72e77",
  colorScheme: "light",
  viewportFit: "cover",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={`${playfair.variable} ${jakarta.variable}`}>{children}</body>
    </html>
  );
}
