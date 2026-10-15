import { applyReferral } from "../lib/referral";
import { WhatsAppIcon } from "../components/WhatsAppIcon";
import { Menu, Search, UserRound, X } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { getSession, homeFor } from "../features/auth/authApi";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { LanguageSelector } from "../components/LanguageSelector";
import { SocialLinks } from "../components/SocialLinks";
import { address, contact } from "../lib/contact";
import { CurrencySelect } from "../lib/currency";

/** Primary navigation. `match` lists the pages that belong to each section, so related pages show it as active. */
const navItems = [
  { to: "/", label: "Home", match: [] as string[] },
  { to: "/about", label: "About", match: [] },
  { to: "/experiences", label: "Our Tours", match: ["/experiences", "/book/"] },
  { to: "/book-private-trip", label: "Private Trips", match: ["/book-private-trip", "/private-tours", "/transfers"] },
  { to: "/gallery", label: "Gallery", match: [] },
  { to: "/explore", label: "Things to Do in Cape Town", match: ["/explore"] },
  { to: "/contact", label: "Contact Us", match: [] }
];
const isActive = (item: (typeof navItems)[number], pathname: string) =>
  item.to === "/" ? pathname === "/" : pathname === item.to || pathname.startsWith(item.to + "/") || item.match.some(m => pathname === m || pathname.startsWith(m.endsWith("/") ? m : m + "/"));

const whyChooseUs = [
  "Our guides are knowledgeable and friendly",
  "We have clean and safe Vehicles",
  "We are reliable and always on time",
  "Happiness of our Guests is our priority"
];

/** Where the account link goes for the signed-in person, or null when nobody is signed in. Browsing never needs an account. */
function useAccountLink() {
  const session = useQuery({ queryKey: ["session"], queryFn: getSession, retry: false, staleTime: 60_000 });
  if (!session.data?.authenticated) return null;
  const role = session.data.role;
  return { to: homeFor(role), label: role === "CUSTOMER" ? "My account" : role === "DRIVER" ? "Driver portal" : role === "PARTNER" ? "Partner portal" : "Staff area" };
}

function SearchForm({ onDone }: { onDone?: () => void }) {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");

  function submit(event: FormEvent) {
    event.preventDefault();
    const trimmed = query.trim();
    navigate(trimmed ? `/experiences?q=${encodeURIComponent(trimmed)}` : "/experiences");
    onDone?.();
  }

  return (
    <form className="header-search" role="search" onSubmit={submit}>
      <label>
        <span className="sr-only">Search tours</span>
        <input autoFocus value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search tours..." />
      </label>
      <button className="icon-button" type="submit" aria-label="Search">
        <Search aria-hidden="true" />
      </button>
    </form>
  );
}

export function AppLayout() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const closeButton = useRef<HTMLButtonElement>(null);
  const menuButton = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const { pathname, search } = useLocation();
  // Partner referral links (?ref=CODE): validate and keep the code for later bookings and enquiries.
  useEffect(() => {
    const code = new URLSearchParams(search).get("ref");
    if (code && /^[A-Za-z0-9]{4,20}$/.test(code)) applyReferral(code, "LINK").catch(() => undefined);
  }, [search]);
  const overlay = pathname === "/";
  const account = useAccountLink();

  useEffect(() => {
    setMenuOpen(false);
    setSearchOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!menuOpen) {
      return;
    }
    closeButton.current?.focus();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") { setMenuOpen(false); return; }
      // Keep keyboard focus inside the open menu.
      if (event.key !== "Tab" || !menu.current) return;
      const items = [...menu.current.querySelectorAll<HTMLElement>("a[href], button")];
      const first = items[0], last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", onKey);
      menuButton.current?.focus();
    };
  }, [menuOpen]);

  return (
    <div className="site-shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className={overlay ? "site-header site-header-overlay" : "site-header"}>
        <div className="top-bar">
          <div className="top-bar-inner">
            <div className="top-bar-contact">
              <a href={contact.phoneHref}>{contact.phoneDisplay}</a>
              <a href={`mailto:${contact.email}`}>{contact.email}</a>
            </div>
            <div className="top-bar-tools">
              <CurrencySelect className="currency-select" />
              <LanguageSelector />
              <SocialLinks className="social-links top-bar-social" />
            </div>
          </div>
        </div>
        <div className="nav-bar">
          <NavLink to="/" className="brand" aria-label="SL Tours home">
            <img src="/assets/logo.png" alt="" width="56" height="56" />
          </NavLink>
          <nav className="desktop-nav" aria-label="Primary navigation">
            {navItems.map((item) => {
              const active = isActive(item, pathname);
              return <Link key={item.to} to={item.to} className={active ? "active" : undefined} aria-current={active ? "page" : undefined}>{item.label}</Link>;
            })}
          </nav>
          <div className="nav-actions">
            <button
              className="icon-button"
              type="button"
              onClick={() => setSearchOpen((open) => !open)}
              aria-expanded={searchOpen}
              aria-label="Search tours"
            >
              <Search aria-hidden="true" />
            </button>
            {account ? <Link className="text-link desktop-only" to={account.to}>{account.label}</Link> : <>
              <Link className="text-link desktop-only" to="/login">Sign in</Link>
              <Link className="button button-ghost button-compact desktop-only" to="/register">Create account</Link>
            </>}
            <NavLink className="button button-primary desktop-only" to="/experiences">
              Find a Tour
            </NavLink>
            <Link className="icon-button mobile-only" to={account?.to ?? "/login"} aria-label={account?.label ?? "Sign in"}>
              <UserRound aria-hidden="true" />
            </Link>
            <button ref={menuButton} className="icon-button mobile-only" type="button" onClick={() => setMenuOpen(true)} aria-label="Open menu"
              aria-expanded={menuOpen} aria-controls="mobile-menu">
              <Menu aria-hidden="true" />
            </button>
          </div>
        </div>
        {searchOpen && <SearchForm onDone={() => setSearchOpen(false)} />}
        {menuOpen && (
          <div ref={menu} id="mobile-menu" className="mobile-menu" role="dialog" aria-modal="true" aria-label="Menu">
            <div className="mobile-menu-header">
              <img src="/assets/logo.png" alt="SL Tours" width="48" height="48" />
              <button ref={closeButton} className="icon-button" type="button" onClick={() => setMenuOpen(false)} aria-label="Close menu">
                <X aria-hidden="true" />
              </button>
            </div>
            <nav aria-label="Mobile navigation links">
              {navItems.map((item) => {
                const active = isActive(item, pathname);
                return <Link key={item.to} to={item.to} className={active ? "active" : undefined} aria-current={active ? "page" : undefined}
                  onClick={() => setMenuOpen(false)}>{item.label}</Link>;
              })}
            </nav>
            <div className="mobile-menu-account">
              {account ? <Link className="button button-secondary" to={account.to} onClick={() => setMenuOpen(false)}>{account.label}</Link> : <>
                <Link className="button button-secondary" to="/login" onClick={() => setMenuOpen(false)}>Sign in</Link>
                <Link className="button button-secondary" to="/register" onClick={() => setMenuOpen(false)}>Create account</Link>
              </>}
            </div>
            <Link className="button button-primary" to="/experiences" onClick={() => setMenuOpen(false)}>Find a Tour</Link>
            <a className="button button-secondary mobile-menu-whatsapp" href={contact.whatsapp} target="_blank" rel="noopener noreferrer">
              <WhatsAppIcon aria-hidden="true" />
              WhatsApp SL Tours
            </a>
          </div>
        )}
      </header>
      <main id="main">
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="footer-inner">
          <div className="footer-brand">
            <img src="/assets/logo.png" alt="SL Tours" width="72" height="72" />
            <p>SL Tours offers excellent opportunities to explore South Africa.</p>
            <a className="footer-awards" href={contact.tripadvisorReview} target="_blank" rel="noopener noreferrer">
              <img src="/assets/home/award-2.svg" alt="Tripadvisor" width="140" height="30" loading="lazy" />
              <img src="/assets/home/award-1.svg" alt="SL Tours on Tripadvisor" width="65" height="65" loading="lazy" />
            </a>
          </div>
          <div>
            <h2>Why choose us</h2>
            <ul className="footer-list">
              {whyChooseUs.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
          <div>
            <h2>Explore</h2>
            <ul className="footer-list">
              <li>
                <Link to="/experiences">Our Tours</Link>
              </li>
              <li>
                <Link to="/book-private-trip">Private Trips</Link>
              </li>
              <li>
                <Link to="/explore">Things to Do in Cape Town</Link>
              </li>
              <li>
                <Link to="/plan-my-trip">Ask us to plan your trip</Link>
              </li>
              <li>
                <Link to="/partner-with-us">Partner with us</Link>
              </li>
              <li>
                <Link to="/account">My account</Link>
              </li>
              <li>
                <Link to="/privacy">Privacy notice</Link>
              </li>
              <li>
                <a href="/operations">Operations</a>
              </li>
            </ul>
          </div>
          <div>
            <h2>Contact Info</h2>
            <ul className="footer-list footer-contact">
              <li>Address: {address}</li>
              <li>Serving: {contact.serviceArea}</li>
              <li>
                Phone: <a href={contact.phoneHref}>{contact.phoneDisplay}</a>
              </li>
              <li>
                Email: <a href={`mailto:${contact.email}`}>{contact.email}</a>
              </li>
            </ul>
            <SocialLinks className="social-links footer-social" />
          </div>
        </div>
        <p className="copyright">Copyright {new Date().getFullYear()} SL tours, All Rights Reserved</p>
      </footer>
      <a className="whatsapp-float" href={contact.whatsapp} target="_blank" rel="noopener noreferrer" aria-label="Chat with SL Tours on WhatsApp">
        <WhatsAppIcon aria-hidden="true" />
      </a>
    </div>
  );
}
