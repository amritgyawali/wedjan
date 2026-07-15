"use client";

import Link from "next/link";
import { FormEvent, useEffect, useRef, useState } from "react";
import { navLinks } from "@/data/home";
import { Icon } from "@/components/home/icon";

export function Header() {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isScrolled, setIsScrolled] = useState(false);
  const [loginMessage, setLoginMessage] = useState("");
  const dialogRef = useRef<HTMLDialogElement>(null);
  const headerRef = useRef<HTMLElement>(null);

  useEffect(() => {
    let frame = 0;
    const updateNav = () => {
      frame = 0;
      setIsScrolled(window.scrollY > 20);
    };
    const onScroll = () => {
      if (!frame) frame = window.requestAnimationFrame(updateNav);
    };
    updateNav();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, []);

  useEffect(() => {
    if (!isMenuOpen) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setIsMenuOpen(false);
    };
    const closeOnOutsidePress = (event: PointerEvent) => {
      if (!headerRef.current?.contains(event.target as Node)) setIsMenuOpen(false);
    };
    window.addEventListener("keydown", closeOnEscape);
    window.addEventListener("pointerdown", closeOnOutsidePress);
    return () => {
      window.removeEventListener("keydown", closeOnEscape);
      window.removeEventListener("pointerdown", closeOnOutsidePress);
    };
  }, [isMenuOpen]);

  function focusSearch() {
    setIsMenuOpen(false);
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    document
      .getElementById("hero-search")
      ?.scrollIntoView({ behavior: prefersReducedMotion ? "auto" : "smooth" });
    window.setTimeout(
      () => {
        document
          .querySelector<HTMLSelectElement>("#hero-search select")
          ?.focus({ preventScroll: true });
      },
      prefersReducedMotion ? 0 : 500,
    );
  }

  function openLogin() {
    setIsMenuOpen(false);
    setLoginMessage("");
    dialogRef.current?.showModal();
  }

  function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoginMessage("Thanks — your secure sign-in flow is ready to connect.");
  }

  return (
    <>
      <header
        className="site-header"
        data-scrolled={isScrolled ? "true" : "false"}
        ref={headerRef}
      >
        <div className="site-header-inner">
          <Link aria-label="wedjan home" className="wordmark" href="/">
            wedjan
          </Link>

          <nav aria-label="Primary navigation" className="hidden flex-1 justify-center md:flex">
            <ul className="flex items-center gap-7 lg:gap-10">
              {navLinks.map((link) => (
                <li key={`${link.label}-${link.href}`}>
                  <Link className="nav-link" href={link.href}>
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>

          <div className="flex items-center gap-2 sm:gap-4">
            <button
              aria-label="Jump to vendor search"
              className="nav-icon-button hidden md:grid"
              onClick={focusSearch}
              type="button"
            >
              <Icon className="text-[24px]" name="search" />
            </button>
            <button className="login-button" onClick={openLogin} type="button">
              Log In
            </button>
            <button
              aria-expanded={isMenuOpen}
              aria-label="Toggle navigation menu"
              className="nav-icon-button md:hidden"
              onClick={() => setIsMenuOpen((current) => !current)}
              type="button"
            >
              <Icon className="text-[24px]" name={isMenuOpen ? "close" : "menu"} />
            </button>
          </div>
        </div>

        <nav
          aria-label="Mobile navigation"
          className="mobile-nav md:hidden"
          data-open={isMenuOpen ? "true" : "false"}
        >
          <ul className="shell grid grid-cols-2 gap-2 py-4">
            {navLinks.map((link) => (
              <li key={`${link.label}-${link.href}`}>
                <Link
                  className="mobile-nav-link"
                  href={link.href}
                  onClick={() => setIsMenuOpen(false)}
                >
                  {link.label}
                </Link>
              </li>
            ))}
            <li className="col-span-2">
              <button className="mobile-nav-link w-full" onClick={focusSearch} type="button">
                <Icon className="text-[20px]" name="search" /> Find a vendor
              </button>
            </li>
          </ul>
        </nav>
      </header>

      <dialog
        aria-labelledby="login-title"
        className="auth-dialog"
        onClick={(event) => {
          if (event.currentTarget === event.target) event.currentTarget.close();
        }}
        ref={dialogRef}
      >
        <div className="relative">
          <form method="dialog">
            <button aria-label="Close login" className="dialog-close" type="submit">
              <Icon className="text-[22px]" name="close" />
            </button>
          </form>
          <p className="mb-2 text-sm font-semibold text-brand-pink">Welcome to wedjan</p>
          <h2 className="font-display text-3xl font-semibold" id="login-title">
            Plan your day, beautifully.
          </h2>
          <p className="mt-3 text-sm leading-6 text-secondary">
            Sign in to save vendors, shortlist ideas, and keep your wedding plans together.
          </p>
          <form className="mt-7 space-y-4" onSubmit={handleLogin}>
            <label className="form-label">
              Email address
              <input
                autoComplete="email"
                className="form-input"
                name="email"
                placeholder="you@example.com"
                required
                type="email"
              />
            </label>
            <button className="primary-button w-full" type="submit">
              Continue with email
            </button>
          </form>
          {loginMessage && (
            <p aria-live="polite" className="mt-4 rounded-lg bg-pink-soft p-3 text-sm text-primary-deep">
              {loginMessage}
            </p>
          )}
        </div>
      </dialog>
    </>
  );
}
