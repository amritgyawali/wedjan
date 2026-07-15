import Link from "next/link";
import { Icon } from "@/components/home/icon";

const footerGroups = [
  {
    title: "Company",
    links: ["About Us", "Careers", "Contact Us"],
  },
  {
    title: "Legal",
    links: ["Terms & Conditions", "Privacy Policy"],
  },
  {
    title: "Business",
    links: ["Vendor Login", "Register as Vendor"],
  },
];

export function Footer() {
  return (
    <footer className="site-footer">
      <div className="shell flex flex-col gap-12">
        <div className="footer-main">
          <div className="lg:w-1/3">
            <Link className="wordmark mb-4 inline-block" href="/">
              wedjan
            </Link>
            <p className="max-w-sm text-sm leading-6 text-secondary">
              Your personal wedding planner. Find trusted vendors, transparent prices, and
              thoughtful inspiration in one seamless place.
            </p>
          </div>
          <div className="grid w-full grid-cols-2 gap-8 sm:grid-cols-3 lg:w-2/3">
            {footerGroups.map((group) => (
              <div className="flex flex-col gap-4" key={group.title}>
                <h3 className="font-display text-lg font-semibold">{group.title}</h3>
                {group.links.map((link) => (
                  <Link className="footer-link" href="#" key={link}>
                    {link}
                  </Link>
                ))}
              </div>
            ))}
          </div>
        </div>
        <div className="footer-bottom">
          <p>© 2026 wedjan. All rights reserved.</p>
          <div className="flex gap-3">
            <Link aria-label="Share wedjan" className="social-link" href="#">
              <Icon filled className="text-[20px]" name="share" />
            </Link>
            <Link aria-label="Email wedjan" className="social-link" href="mailto:hello@wedjan.com">
              <Icon filled className="text-[20px]" name="mail" />
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
