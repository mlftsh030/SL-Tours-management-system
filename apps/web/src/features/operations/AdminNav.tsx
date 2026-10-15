import { useEffect, useState } from "react";
import { NavLink } from "react-router-dom";
import { ChevronDown, Globe, LayoutDashboard, Map, Truck, Users, Wallet, type LucideIcon } from "lucide-react";

export type AdminLink = { to: string; label: string; adminOnly?: boolean };
type AdminGroup = { id: string; label: string; icon: LucideIcon; links: AdminLink[] };

export const dashboardLink = { to: "/operations", label: "Dashboard" };

export const adminGroups: AdminGroup[] = [
  { id: "sales", label: "Sales & Customers", icon: Users, links: [
    { to: "/operations/follow-up", label: "Follow-up" },
    { to: "/operations/enquiries", label: "Enquiries" },
    { to: "/operations/bookings", label: "Bookings" },
    { to: "/operations/customers", label: "Customers" },
    { to: "/operations/exports", label: "Data exports", adminOnly: true }] },
  { id: "finance", label: "Finance", icon: Wallet, links: [
    { to: "/operations/billing", label: "Invoices & quotes" },
    { to: "/operations/billing-settings", label: "Billing settings" }] },
  { id: "operations", label: "Operations", icon: Truck, links: [
    { to: "/operations/trips", label: "Trips & inspections" },
    { to: "/operations/vehicles", label: "Vehicles" },
    { to: "/operations/drivers", label: "Drivers", adminOnly: true }] },
  { id: "tours", label: "Tours & Experiences", icon: Map, links: [
    { to: "/operations/tours", label: "Tours & prices" },
    { to: "/operations/pricing", label: "Private-trip rates" },
    { to: "/operations/activities", label: "Explore activities", adminOnly: true }] },
  { id: "website", label: "Website & Partners", icon: Globe, links: [
    { to: "/operations/site-images", label: "Website images", adminOnly: true },
    { to: "/operations/partners", label: "Partners", adminOnly: true }] }
];

const matches = (pathname: string, to: string) => pathname === to || pathname.startsWith(`${to}/`);

/** The groups and links this user may open; groups with nothing accessible are left out. */
export function visibleGroups(isAdmin: boolean) {
  return adminGroups.map(g => ({ ...g, links: g.links.filter(l => isAdmin || !l.adminOnly) })).filter(g => g.links.length > 0);
}

/** The label of the page at this path, for the mobile menu button. */
export function currentPageLabel(pathname: string, isAdmin: boolean) {
  if (pathname === dashboardLink.to) return dashboardLink.label;
  return visibleGroups(isAdmin).flatMap(g => g.links).find(l => matches(pathname, l.to))?.label;
}

export function AdminNav({ pathname, isAdmin, onNavigate }: { pathname: string; isAdmin: boolean; onNavigate: () => void }) {
  const groups = visibleGroups(isAdmin);
  const activeGroup = groups.find(g => g.links.some(l => matches(pathname, l.to)))?.id ?? null;
  // Only one group is open at a time; moving to another page opens the group that holds it.
  const [open, setOpen] = useState<string | null>(activeGroup);
  useEffect(() => setOpen(activeGroup), [activeGroup, pathname]);

  return <nav id="admin-nav" aria-label="Administration">
    <NavLink to={dashboardLink.to} end onClick={onNavigate}><LayoutDashboard aria-hidden="true" />{dashboardLink.label}</NavLink>
    {groups.map(({ id, label, icon: Icon, links }) => {
      const expanded = open === id;
      return <div className="admin-nav-group" key={id}>
        <button type="button" className={`admin-nav-heading${activeGroup === id ? " has-active" : ""}`} aria-expanded={expanded}
          aria-controls={`admin-nav-${id}`} onClick={() => setOpen(expanded ? null : id)}>
          <Icon aria-hidden="true" /><span>{label}</span>
          {activeGroup === id && !expanded && <span className="sr-only"> (current page inside)</span>}
          <ChevronDown className="admin-nav-chevron" aria-hidden="true" />
        </button>
        <ul id={`admin-nav-${id}`} hidden={!expanded}>
          {links.map(l => <li key={l.to}><NavLink to={l.to} onClick={onNavigate}>{l.label}</NavLink></li>)}
        </ul>
      </div>;
    })}
  </nav>;
}
