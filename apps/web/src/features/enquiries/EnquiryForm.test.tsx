import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { EnquiryForm } from "./EnquiryForm";

const csrf = () => new Response(JSON.stringify({ token: "t", headerName: "X-CSRF-TOKEN" }));
function renderForm() {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}><EnquiryForm /></QueryClientProvider>);
}
async function fillIn() {
  await userEvent.type(screen.getByLabelText("Name"), "Thandi Nkosi");
  await userEvent.type(screen.getByLabelText("Email"), "thandi@example.com");
  await userEvent.type(screen.getByLabelText("Message"), "Can you collect us from the airport?");
}

afterEach(() => vi.unstubAllGlobals());
describe("website enquiry form", () => {
  it("confirms only after the enquiry is saved, and retries reuse the same idempotency key", async () => {
    const bodies: Array<Record<string, unknown>> = [];
    let attempt = 0;
    vi.stubGlobal("fetch", vi.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.endsWith("/csrf")) return Promise.resolve(csrf());
      bodies.push(JSON.parse(String(options?.body)));
      attempt += 1;
      return Promise.resolve(attempt === 1
        ? new Response(JSON.stringify({ message: "Service unavailable" }), { status: 503 })
        : new Response(JSON.stringify({ reference: "SLE-2026-ABCDE", status: "NEW", message: "Thank you.", duplicate: false }), { status: 201 }));
    }));
    renderForm();
    await fillIn();
    await userEvent.click(screen.getByRole("button", { name: "Send enquiry" }));
    expect(await screen.findByText(/could not send your enquiry/)).toBeInTheDocument();
    expect(screen.queryByText("Enquiry received")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Name")).toHaveValue("Thandi Nkosi");
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("Enquiry received")).toBeInTheDocument();
    expect(screen.getByText("SLE-2026-ABCDE")).toBeInTheDocument();
    expect(bodies).toHaveLength(2);
    expect(bodies[0].idempotencyKey).toBe(bodies[1].idempotencyKey);
    expect(bodies[1]).toMatchObject({ customerName: "Thandi Nkosi", enquiryType: "GENERAL", message: "Can you collect us from the airport?" });
  });

  it("sends details edited after a failure as a new enquiry, so the edit is not answered with the earlier one", async () => {
    const bodies: Array<Record<string, unknown>> = [];
    vi.stubGlobal("fetch", vi.fn().mockImplementation((url: string, options?: RequestInit) => {
      if (url.endsWith("/csrf")) return Promise.resolve(csrf());
      bodies.push(JSON.parse(String(options?.body)));
      return Promise.resolve(bodies.length === 1
        ? new Response(JSON.stringify({ message: "Gateway timeout" }), { status: 504 })
        : new Response(JSON.stringify({ reference: "SLE-2026-FGHJK", status: "NEW", message: "Thank you.", duplicate: false }), { status: 201 }));
    }));
    renderForm();
    await fillIn();
    await userEvent.click(screen.getByRole("button", { name: "Send enquiry" }));
    expect(await screen.findByText(/could not send your enquiry/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Message"), " We are four adults.");
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("Enquiry received")).toBeInTheDocument();
    expect(bodies[1].idempotencyKey).not.toBe(bodies[0].idempotencyKey);
    expect(bodies[1].message).toBe("Can you collect us from the airport? We are four adults.");
  });

  it("disables the submit button while sending to prevent double submissions", async () => {
    let release: (response: Response) => void = () => undefined;
    const fetcher = vi.fn().mockImplementation((url: string) => url.endsWith("/csrf")
      ? Promise.resolve(csrf()) : new Promise<Response>(resolve => { release = resolve; }));
    vi.stubGlobal("fetch", fetcher);
    renderForm();
    await fillIn();
    await userEvent.click(screen.getByRole("button", { name: "Send enquiry" }));
    const sending = await screen.findByRole("button", { name: "Sending…" });
    expect(sending).toBeDisabled();
    await userEvent.click(sending);
    release(new Response(JSON.stringify({ reference: "SLE-2026-ZZZZZ", status: "NEW", message: "Thank you.", duplicate: false }), { status: 201 }));
    await waitFor(() => expect(screen.getByText("Enquiry received")).toBeInTheDocument());
    expect(fetcher.mock.calls.filter(([url]) => url === "/api/v1/enquiries")).toHaveLength(1);
  });

  it("opens with the tour chosen on a tour page", async () => {
    window.history.pushState({}, "", "/contact?tour=cape-peninsula-journey");
    vi.stubGlobal("fetch", vi.fn().mockImplementation((url: string) => url.includes("/api/v1/tours")
      ? Promise.resolve(new Response(JSON.stringify([{ slug: "cape-peninsula-journey", title: "Cape Peninsula Journey" }]), { headers: { "Content-Type": "application/json" } }))
      : Promise.reject(new Error(url))));
    renderForm();
    expect(await screen.findByRole("option", { name: "Cape Peninsula Journey" })).toBeInTheDocument();
    expect(screen.getByDisplayValue("One of your tours")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Cape Peninsula Journey")).toBeInTheDocument();
    window.history.pushState({}, "", "/");
  });
});
