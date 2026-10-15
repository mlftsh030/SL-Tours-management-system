import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AdminNav, currentPageLabel } from "./AdminNav";

function Nav({ isAdmin, onNavigate }: { isAdmin: boolean; onNavigate: () => void }) {
  const { pathname } = useLocation();
  return <><AdminNav pathname={pathname} isAdmin={isAdmin} onNavigate={onNavigate} /><output data-testid="path">{pathname}</output></>;
}
const show = (path: string, isAdmin = true, onNavigate = vi.fn()) =>
  render(<MemoryRouter initialEntries={[path]}><Nav isAdmin={isAdmin} onNavigate={onNavigate} /></MemoryRouter>);
const heading = (name: string) => screen.getByRole("button", { name: new RegExp(`^${name.replace("&", "\\&")}`) });
const expanded = () => screen.getAllByRole("button").filter(b => b.getAttribute("aria-expanded") === "true").map(b => b.textContent);

const pages: Array<[string, string, string]> = [
  ["Sales & Customers", "Follow-up", "/operations/follow-up"], ["Sales & Customers", "Data exports", "/operations/exports"],
  ["Sales & Customers", "Enquiries", "/operations/enquiries"], ["Sales & Customers", "Bookings", "/operations/bookings"],
  ["Sales & Customers", "Customers", "/operations/customers"], ["Finance", "Invoices & quotes", "/operations/billing"],
  ["Finance", "Billing settings", "/operations/billing-settings"], ["Operations", "Trips & inspections", "/operations/trips"],
  ["Operations", "Vehicles", "/operations/vehicles"], ["Operations", "Drivers", "/operations/drivers"],
  ["Tours & Experiences", "Tours & prices", "/operations/tours"], ["Tours & Experiences", "Private-trip rates", "/operations/pricing"],
  ["Tours & Experiences", "Explore activities", "/operations/activities"], ["Website & Partners", "Website images", "/operations/site-images"],
  ["Website & Partners", "Partners", "/operations/partners"]
];

describe("administration navigation", () => {
  it("keeps Dashboard on its own and collapses every group on the dashboard", () => {
    show("/operations");
    expect(screen.getByRole("link", { name: "Dashboard" })).toHaveClass("active");
    expect(expanded()).toEqual([]);
    expect(screen.getAllByRole("button").map(b => b.textContent)).toEqual(["Sales & Customers", "Finance", "Operations", "Tours & Experiences", "Website & Partners"]);
  });

  it.each(pages)("opening %s › %s directly expands its group and marks the page", (group, label, path) => {
    show(path);
    expect(heading(group)).toHaveAttribute("aria-expanded", "true");
    expect(expanded()).toHaveLength(1);
    const list = document.getElementById(heading(group).getAttribute("aria-controls")!)!;
    expect(within(list).getByRole("link", { name: label })).toHaveAttribute("href", path);
    expect(within(list).getByRole("link", { name: label })).toHaveClass("active");
    expect(currentPageLabel(path, true)).toBe(label);
  });

  it("marks the group of a nested page, such as a single invoice", () => {
    show("/operations/billing/abc");
    expect(heading("Finance")).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("link", { name: "Invoices & quotes" })).toHaveClass("active");
    expect(screen.getByRole("link", { name: "Billing settings" })).not.toHaveClass("active");
  });

  it("keeps one group open at a time, works from the keyboard and flags a collapsed group holding the current page", async () => {
    show("/operations/vehicles");
    heading("Finance").focus();
    await userEvent.keyboard("{Enter}");
    expect(expanded()).toEqual(["Finance"]);
    expect(heading("Operations")).toHaveAttribute("aria-expanded", "false");
    expect(heading("Operations")).toHaveClass("has-active");
    expect(heading("Operations")).toHaveAccessibleName(/current page inside/);
    await userEvent.keyboard(" ");
    expect(expanded()).toEqual([]);
  });

  it("reaches every page and tells the drawer to close after a choice, including the current page", async () => {
    const onNavigate = vi.fn();
    show("/operations", true, onNavigate);
    for (const [group, label, path] of pages) {
      if (heading(group).getAttribute("aria-expanded") === "false") await userEvent.click(heading(group));
      await userEvent.click(screen.getByRole("link", { name: label }));
      expect(screen.getByTestId("path")).toHaveTextContent(path);
    }
    await userEvent.click(screen.getByRole("link", { name: "Partners" }));
    expect(onNavigate).toHaveBeenCalledTimes(pages.length + 1);
  });

  it("hides admin-only pages and empty groups from staff", async () => {
    show("/operations", false);
    expect(screen.queryByRole("button", { name: /Website & Partners/ })).not.toBeInTheDocument();
    await userEvent.click(heading("Operations"));
    expect(screen.queryByRole("link", { name: "Drivers" })).not.toBeInTheDocument();
    await userEvent.click(heading("Sales & Customers"));
    expect(screen.queryByRole("link", { name: "Data exports" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Follow-up" })).toBeInTheDocument();
    await userEvent.click(heading("Tours & Experiences"));
    expect(screen.queryByRole("link", { name: "Explore activities" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Private-trip rates" })).toBeInTheDocument();
    expect(currentPageLabel("/operations/drivers", false)).toBeUndefined();
  });
});
