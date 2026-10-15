import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CurrencyProvider } from "../lib/currency";
import { AppLayout } from "./AppLayout";

const NAV = ["Home", "About", "Our Tours", "Private Trips", "Gallery", "Things to Do in Cape Town", "Contact Us"];
const renderAt = (path: string) => render(<QueryClientProvider client={new QueryClient()}><CurrencyProvider>
  <MemoryRouter initialEntries={[path]}><Routes><Route element={<AppLayout />}><Route path="*" element={<p>Page</p>} /></Route></Routes></MemoryRouter>
</CurrencyProvider></QueryClientProvider>);
beforeEach(() => vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new Error("offline")))));
afterEach(() => vi.unstubAllGlobals());

describe("site navigation", () => {
  it("lists the seven sections in order with the right destinations", () => {
    renderAt("/");
    const nav = screen.getByRole("navigation", { name: "Primary navigation" });
    expect(within(nav).getAllByRole("link").map(l => l.textContent)).toEqual(NAV);
    expect(within(nav).getByRole("link", { name: "Private Trips" })).toHaveAttribute("href", "/book-private-trip");
    expect(within(nav).getByRole("link", { name: "Things to Do in Cape Town" })).toHaveAttribute("href", "/explore");
    expect(within(nav).getByRole("link", { name: "Home" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("contentinfo")).toContainElement(screen.getByRole("link", { name: "Partner with us" }));
  });

  it.each([
    ["/explore/cape-point", "Things to Do in Cape Town"], ["/explore/plan", "Things to Do in Cape Town"], ["/experiences/cape-point-tour", "Our Tours"],
    ["/book/cape-point-tour", "Our Tours"], ["/private-tours", "Private Trips"], ["/transfers", "Private Trips"], ["/about", "About"]
  ])("marks the section for %s as active", (path, label) => {
    renderAt(path);
    const nav = screen.getByRole("navigation", { name: "Primary navigation" });
    const current = within(nav).getAllByRole("link").filter(l => l.getAttribute("aria-current") === "page");
    expect(current.map(l => l.textContent)).toEqual([label]);
  });

  it("has an accessible mobile menu that traps focus, closes with Escape and returns focus", async () => {
    renderAt("/gallery");
    const open = screen.getByRole("button", { name: "Open menu" });
    await userEvent.click(open);
    const menu = screen.getByRole("dialog", { name: "Menu" });
    expect(within(menu).getAllByRole("link").map(l => l.textContent).slice(0, 7)).toEqual(NAV);
    expect(within(menu).getByRole("link", { name: "Gallery" })).toHaveAttribute("aria-current", "page");
    expect(within(menu).getByRole("button", { name: "Close menu" })).toHaveFocus();
    await userEvent.tab({ shift: true });
    expect(menu).toContainElement(document.activeElement as HTMLElement);
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(open).toHaveFocus();
  });

  it("offers Sign in and Create account to visitors, on desktop and in the mobile menu", async () => {
    renderAt("/experiences");
    const actions = document.querySelector(".nav-actions") as HTMLElement;
    expect(within(actions).getByRole("link", { name: "Create account" })).toHaveAttribute("href", "/register");
    expect(within(actions).getAllByRole("link", { name: "Sign in" }).map(l => l.getAttribute("href"))).toEqual(["/login", "/login"]);
    await userEvent.click(screen.getByRole("button", { name: "Open menu" }));
    const menu = screen.getByRole("dialog", { name: "Menu" });
    expect(within(menu).getByRole("link", { name: "Sign in" })).toHaveAttribute("href", "/login");
    expect(within(menu).getByRole("link", { name: "Create account" })).toHaveAttribute("href", "/register");
  });

  it("links a signed-in customer to their account instead", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response(JSON.stringify({ authenticated: true, email: "a@example.com", name: "A", role: "CUSTOMER" })))));
    renderAt("/");
    const actions = document.querySelector(".nav-actions") as HTMLElement;
    await waitFor(() => expect(within(actions).getAllByRole("link", { name: "My account" })).toHaveLength(2));
    expect(within(actions).queryByRole("link", { name: "Create account" })).not.toBeInTheDocument();
  });
});
