import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  transpilePackages: ["@wedjan/shared", "@wedjan/ui-tokens"],
  images: {
    formats: ["image/avif", "image/webp"],
    qualities: [75, 92],
  },
};

export default nextConfig;
