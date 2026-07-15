import type { ReactNode } from "react";
import { PlatformProviders } from "@/components/platform/providers";
import "./platform.css";

export default function PlatformLayout({ children }: { children: ReactNode }) {
  return <PlatformProviders>{children}</PlatformProviders>;
}
