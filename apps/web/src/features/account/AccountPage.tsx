import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, Navigate, NavLink, Route, Routes, useLocation } from "react-router-dom";
import { FileText, Inbox, LayoutDashboard, LogOut, Map, UserRound } from "lucide-react";
import { useSession } from "../auth/useSession";
import { homeFor, signOut } from "../auth/authApi";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { getProfile } from "./accountApi";
import { VerificationBanner } from "./VerificationBanner";
import { AccountOverview } from "./AccountOverview";
import { AccountEnquiries } from "./AccountEnquiries";
import { AccountBookings } from "./AccountBookings";
import { AccountDocuments } from "./AccountDocuments";
import { AccountProfile } from "./AccountProfile";

/** The customer's own area. Its navigation is separate from the staff sidebar and only lists what customers can use. */
const sections = [
  { to: "/account", label: "Overview", icon: LayoutDashboard, end: true },
  { to: "/account/enquiries", label: "Enquiries", icon: Inbox },
  { to: "/account/bookings", label: "Bookings", icon: Map },
  { to: "/account/documents", label: "Invoices & quotes", icon: FileText },
  { to: "/account/profile", label: "Profile", icon: UserRound }
];

export function AccountPage() {
  useDocumentTitle("My account | SL Tours");
  const session = useSession();
  const location = useLocation();
  const [registered] = useState<string | undefined>(() => (location.state as { registered?: string } | null)?.registered);
  const [error, setError] = useState(""); const [pending, setPending] = useState(false);
  const isCustomer = session.data?.role === "CUSTOMER";
  const profile = useQuery({ queryKey: ["account", "profile"], queryFn: getProfile, enabled: isCustomer, retry: false });
  async function logout() {
    setPending(true); setError("");
    // A full page load to the website drops every cached account record from memory.
    try { await signOut(); window.location.replace("/"); }
    catch (err) { setError((err as Error).message); } finally { setPending(false); }
  }
  if (session.isLoading) return <LoadingState label="Checking your session" />;
  if (session.isError) return <ErrorState message={session.error.message} />;
  if (!session.data?.authenticated) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />;
  if (!isCustomer) return <Navigate to={homeFor(session.data.role)} replace />;
  const verified = profile.data?.emailVerified ?? session.data.emailVerified ?? false;

  return <div className="account-shell">
    <header className="account-header">
      <div className="account-header-inner">
        <a className="account-brand" href="/"><img src="/assets/logo.png" alt="" width="44" height="44" /><span>SL Tours<strong>My account</strong></span></a>
        <div className="account-header-actions">
          <a className="account-header-link" href="/">Back to website</a>
          <button type="button" className="account-header-link" onClick={logout} disabled={pending}><LogOut aria-hidden="true" />{pending ? "Signing out…" : "Sign out"}</button>
        </div>
      </div>
    </header>
    <main className="account-main" id="main">
      <div className="account-intro">
        <p className="eyebrow">Your account</p>
        <h1>Hello, {profile.data?.name ?? session.data.name}</h1>
      </div>
      <nav className="account-nav" aria-label="My account">
        {sections.map(({ to, label, icon: Icon, end }) => <NavLink key={to} to={to} end={end}><Icon aria-hidden="true" />{label}</NavLink>)}
      </nav>
      {error && <ErrorState message={error} />}
      {registered && <div className="account-notice" role="status">
        <strong>Welcome to SL Tours.</strong>{" "}
        {registered === "SENT" ? <>We have emailed a confirmation link to <strong>{session.data.email}</strong>.</>
          : registered === "NOT_CONFIGURED" ? "Your account is ready. Email confirmation is not available on this site yet."
          : "Your account is ready, but the confirmation email could not be sent. Use the button below to try again."}
      </div>}
      {!verified && <VerificationBanner email={session.data.email ?? ""} />}
      <Routes>
        <Route index element={<AccountOverview verified={verified} />} />
        <Route path="enquiries" element={<AccountEnquiries />} />
        <Route path="bookings" element={<AccountBookings />} />
        <Route path="documents" element={<AccountDocuments />} />
        <Route path="profile" element={<AccountProfile />} />
        <Route path="*" element={<Navigate to="/account" replace />} />
      </Routes>
      <p className="account-help">Need help? <Link to="/contact">Contact SL Tours</Link>.</p>
    </main>
  </div>;
}
