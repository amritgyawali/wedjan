import Link from "next/link";
import type { ReactNode } from "react";

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="auth-shell">
      <div className="auth-card">
        <Link href="/" className="wordmark">
          wedjan
        </Link>
        {children}
      </div>
    </div>
  );
}
