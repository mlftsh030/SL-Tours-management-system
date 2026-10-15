import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthPage } from "./AuthPage";

const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
const show = (registration: boolean) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <MemoryRouter initialEntries={[registration ? "/register" : "/login"]}><Routes>
    <Route path="/register" element={<AuthPage registration />} /><Route path="/login" element={<AuthPage />} />
    <Route path="/account" element={<p>Account area</p>} />
  </Routes></MemoryRouter></QueryClientProvider>);
afterEach(() => vi.unstubAllGlobals());

async function fill(values: Record<string, string>) {
  for (const [label, value] of Object.entries(values)) await userEvent.type(screen.getByLabelText(label, { exact: true }), value);
}

describe("registration form (UI only; the real flow is verified against the backend)", () => {
  it("explains each problem next to its field and focuses the first", async () => {
    vi.stubGlobal("fetch", vi.fn());
    show(true);
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(screen.getByLabelText("Full name")).toHaveFocus();
    expect(screen.getByLabelText("Full name")).toHaveAccessibleDescription("Enter your full name");
    expect(screen.getByLabelText("Email address")).toHaveAttribute("aria-invalid", "true");
    await fill({ "Full name": "Thandi", "Email address": "thandi@example.com", "WhatsApp number (optional)": "082 555 1234", Password: "short", "Confirm password": "other" });
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(screen.getByLabelText("WhatsApp number (optional)")).toHaveFocus();
    expect(screen.getByText(/Start with \+ and your country code/)).toBeInTheDocument();
    expect(screen.getByText("Use at least 12 characters")).toBeInTheDocument();
    expect(screen.getByText("The passwords do not match")).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("shows and hides the password without submitting", async () => {
    vi.stubGlobal("fetch", vi.fn());
    show(true);
    const password = screen.getByLabelText("Password", { exact: true });
    const toggle = screen.getByRole("button", { name: "Show password" });
    await userEvent.click(toggle);
    expect(password).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Hide password" })).toHaveAttribute("aria-pressed", "true");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("sends only customer details, never a role, then opens the account", async () => {
    const bodies: string[] = [];
    vi.stubGlobal("fetch", vi.fn((url: string, options?: RequestInit) => {
      if (url.endsWith("/csrf")) return json({ token: "t", headerName: "X-CSRF-TOKEN" });
      if (url.endsWith("/register")) { bodies.push(String(options?.body)); return json({ email: "thandi@example.com", verificationEmail: "SENT" }, 201); }
      if (url.endsWith("/login")) return Promise.resolve(new Response(null, { status: 204 }));
      return json({ authenticated: true, email: "thandi@example.com", name: "Thandi", role: "CUSTOMER", emailVerified: false });
    }));
    show(true);
    await fill({ "Full name": "Thandi", "Email address": "thandi@example.com", "WhatsApp number (optional)": "+27 82 555 1234",
      Password: "long-enough-password", "Confirm password": "long-enough-password" });
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("Account area")).toBeInTheDocument();
    expect(Object.keys(JSON.parse(bodies[0])).sort()).toEqual(["email", "name", "password", "passwordConfirmation", "phone"]);
  });

  it("points to sign-in and password reset when the address already has an account", async () => {
    vi.stubGlobal("fetch", vi.fn((url: string) => url.endsWith("/csrf") ? json({ token: "t", headerName: "X-CSRF-TOKEN" })
      : json({ status: 409, message: "An account with this email already exists. Sign in, or reset your password if you have forgotten it.", errors: {} }, 409)));
    show(true);
    await fill({ "Full name": "Thandi", "Email address": "thandi@example.com", Password: "long-enough-password", "Confirm password": "long-enough-password" });
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText(/already exists/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "reset your password" })).toHaveAttribute("href", "/forgot-password");
  });
});

describe("sign-in form", () => {
  it("offers password recovery and browsing without an account", async () => {
    vi.stubGlobal("fetch", vi.fn((url: string) => url.endsWith("/csrf") ? json({ token: "t", headerName: "X-CSRF-TOKEN" })
      : json({ message: "Email or password is incorrect" }, 401)));
    show(false);
    expect(screen.getByRole("link", { name: "Forgot your password?" })).toHaveAttribute("href", "/forgot-password");
    expect(screen.getByRole("link", { name: "browse tours" })).toHaveAttribute("href", "/experiences");
    await fill({ "Email address": "thandi@example.com", Password: "wrong-password" });
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Email or password is incorrect"));
  });
});
