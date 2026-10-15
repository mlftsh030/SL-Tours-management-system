import { Link, Navigate, Route, Routes, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { Menu, LogOut } from "lucide-react";
import { AdminNav, currentPageLabel } from "./AdminNav";
import { useSession } from "../auth/useSession";
import { signOut } from "../auth/authApi";
import { LoadingState, ErrorState } from "../../components/StateBlocks";
import { DashboardPanel } from "./DashboardPanel";
import { ToursPanel } from "./ToursPanel";
import { BookingsPanel } from "./BookingsPanel";
import { CustomersPage } from "../customers/CustomersPage";
import { FollowUpPanel } from "../reports/FollowUpPanel";
import { ExportsPanel } from "../reports/ExportsPanel";
import { EnquiriesPanel } from "./EnquiriesPanel";
import { VehiclesPanel } from "./VehiclesPanel";
import { DriversPanel } from "./DriversPanel";
import { AssignmentsPanel } from "./AssignmentsPanel";
import { PricingPanel } from "./PricingPanel";
import { SiteImagesPanel } from "./SiteImagesPanel";
import { ActivitiesPanel } from "./ActivitiesPanel";
import { PartnersPanel } from "./PartnersPanel";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { useAdminLayout } from "./useAdminLayout";
import { BillingPanel } from "../billing/BillingPanel";
import { BillingSettingsPanel } from "../billing/BillingSettingsPanel";

export function OperationsPage() {
  useDocumentTitle("Administration | SL Tours");
  const session = useSession();
  const client = useQueryClient(); const navigate = useNavigate();
  const [error, setError] = useState(""); const [pending, setPending] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const { pathname } = useLocation();
  // Callback ref: the main area only exists once the session check has finished.
  const [main, setMain] = useState<HTMLElement | null>(null);
  useAdminLayout(main);
  useEffect(() => setMenuOpen(false), [pathname]);
  async function logout() {
    setPending(true); setError("");
    try { await signOut(); client.clear(); navigate("/login?admin=1", { replace: true }); }
    catch (err) { setError((err as Error).message); } finally { setPending(false); }
  }
  if (session.isLoading) return <LoadingState label="Checking your session" />;
  if (session.isError) return <ErrorState message={session.error.message} />;
  if (!session.data?.authenticated) return <Navigate to="/login?admin=1" replace />;
  if (session.data.role === "DRIVER") return <Navigate to="/driver" replace />;
  if (session.data.role === "PARTNER") return <Navigate to="/partner" replace />;
  if (session.data.role !== "ADMIN" && session.data.role !== "STAFF") return <main className="auth-page"><div className="form-surface"><h1>Staff access required</h1><p>This account does not have permission to access administration.</p><Link to="/account">My bookings</Link><button className="button button-secondary" onClick={logout} disabled={pending}>Sign out</button>{error && <ErrorState message={error} />}</div></main>;
  const isAdmin = session.data.role === "ADMIN";
  return <div className="admin-shell">
    <aside className={`admin-sidebar${menuOpen ? " is-open" : ""}`}>
      <Link className="admin-brand" to="/operations"><img src="/assets/logo.png" width="44" height="44" alt="" /><span>SL Tours<strong>Administration</strong></span></Link>
      <button className="admin-menu-toggle" type="button" aria-expanded={menuOpen} aria-controls="admin-nav" onClick={() => setMenuOpen(open => !open)}>
        <Menu aria-hidden="true" />{currentPageLabel(pathname, isAdmin) ?? "Menu"}
      </button>
      <AdminNav pathname={pathname} isAdmin={isAdmin} onNavigate={() => setMenuOpen(false)} />
      <div className="admin-sidebar-bottom"><a href="/">View website</a><button onClick={logout} disabled={pending}><LogOut aria-hidden="true" />{pending ? "Signing out…" : "Sign out"}</button></div>
    </aside>
    <main className="admin-main" ref={setMain}>
      <header className="admin-topline"><span>Operations workspace</span><span>{session.data.name} · {session.data.role}</span></header>
      {error && <ErrorState message={error} />}
      <Routes>
        <Route index element={<DashboardPanel />} />
        <Route path="tours" element={<ToursPanel canEdit={isAdmin} />} />
        <Route path="bookings" element={<BookingsPanel isAdmin={isAdmin} />} />
        <Route path="trips" element={<AssignmentsPanel isAdmin={isAdmin} />} />
        <Route path="vehicles" element={<VehiclesPanel isAdmin={isAdmin} />} />
        <Route path="pricing" element={<PricingPanel isAdmin={isAdmin} />} />
        {isAdmin && <Route path="drivers" element={<DriversPanel />} />}
        {isAdmin && <Route path="site-images" element={<SiteImagesPanel />} />}
        {isAdmin && <Route path="activities" element={<ActivitiesPanel />} />}
        {isAdmin && <Route path="partners" element={<PartnersPanel />} />}
        <Route path="customers/*" element={<CustomersPage isAdmin={isAdmin} />} />
        <Route path="follow-up" element={<FollowUpPanel />} />
        {isAdmin && <Route path="exports" element={<ExportsPanel />} />}
        <Route path="billing/*" element={<BillingPanel isAdmin={isAdmin} />} />
        <Route path="billing-settings" element={<BillingSettingsPanel isAdmin={isAdmin} />} />
        <Route path="enquiries" element={<EnquiriesPanel />} />
        <Route path="*" element={<Navigate to="/operations" replace />} />
      </Routes>
    </main>
  </div>;
}
