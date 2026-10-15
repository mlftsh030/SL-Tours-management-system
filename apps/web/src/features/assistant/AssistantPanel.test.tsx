import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CurrencyProvider } from "../../lib/currency";
import { AssistantLauncher } from "./AssistantLauncher";

const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <CurrencyProvider><MemoryRouter><AssistantLauncher /></MemoryRouter></CurrencyProvider></QueryClientProvider>);
afterEach(() => vi.unstubAllGlobals());

const itinerary = {
  date: "2026-10-06", travellers: "2 adults", transport: [], food: [], budgetCents: 300000, verdict: "CANNOT_CONFIRM",
  verdictMessage: "We can't confirm this fits your budget.", assumptions: ["Travel times are planning estimates"], exclusions: ["food and drinks"],
  warnings: [], skipped: [], totals: { verifiedCents: 56000, assumptionCents: 0, estimatedCents: 0, knownTotalCents: 56000, unknownItems: 1 },
  confidence: { pricesConfirmed: false, scheduleConfirmed: true, suitabilityConfirmed: true, reasons: [] },
  stops: [{ order: 1, type: "ACTIVITY", slug: "aquarium", title: "Two Oceans Aquarium", area: "WATERFRONT", areaLabel: "V&A Waterfront", start: "09:30:00",
    end: "11:30:00", travelMinutes: 10, notes: [], cautions: [], costs: [
      { label: "Adult", quantity: 2, unitAmountCents: 28000, amountCents: 56000, kind: "VERIFIED", detail: null, sourceUrl: "https://shop.aquarium.co.za/", verifiedOn: "2026-10-03" },
      { label: "Admission", quantity: 1, unitAmountCents: null, amountCents: null, kind: "UNKNOWN", detail: "Prices are not published online", sourceUrl: null, verifiedOn: null }] }]
};
const reply = (extra = {}) => ({ conversationId: "c1", status: "OK", mode: "MOCK", text: "Hello! I can plan a day.", options: [], details: {}, missing: ["date"],
  enquiryOptionId: null, enquiryReference: null, plannerLink: "/explore/plan?adults=2", checkedFallback: false, ...extra });

function server(routes: Record<string, (body: Record<string, unknown> | null) => Promise<Response>>) {
  const calls: Array<{ url: string; method: string; body: Record<string, unknown> | null }> = [];
  vi.stubGlobal("fetch", vi.fn((url: string, options?: RequestInit) => {
    const method = options?.method ?? "GET";
    const body = options?.body ? JSON.parse(String(options.body)) : null;
    calls.push({ url, method, body });
    if (url.endsWith("/csrf")) return json({ token: "t", headerName: "X-CSRF-TOKEN" });
    const key = Object.keys(routes).find(k => `${method} ${url}`.endsWith(k));
    return key ? routes[key](body) : Promise.reject(new Error(`${method} ${url}`));
  }));
  return calls;
}

describe("Cape Town planning assistant", () => {
  it("is hidden when live chat is disabled, leaving the planner as the fallback", async () => {
    const calls = server({ "GET /api/v1/assistant/status": () => json({ available: false, mode: "DISABLED", message: "Use the day planner" }) });
    wrap();
    await waitFor(() => expect(calls.some(c => c.url.endsWith("/status"))).toBe(true));
    expect(screen.queryByRole("button", { name: /Cape Town assistant/ })).not.toBeInTheDocument();
  });

  it("opens an accessible dialog, labels the development mock and renders plans from planner results", async () => {
    server({
      "GET /api/v1/assistant/status": () => json({ available: true, mode: "MOCK", message: "Development mock" }),
      "POST /api/v1/assistant/conversations": () => json(reply()),
      "POST /api/v1/assistant/conversations/c1/messages": () => json(reply({ text: "Here are your options, described from the planner's own figures.",
        options: [{ id: "A", label: "Best match", itinerary }], missing: [], checkedFallback: true })),
      "DELETE /api/v1/assistant/conversations/c1": () => Promise.resolve(new Response(null, { status: 204 }))
    });
    wrap();
    const launcher = await screen.findByRole("button", { name: "Ask our Cape Town assistant" });
    await userEvent.click(launcher);
    const dialog = await screen.findByRole("dialog", { name: "Cape Town planning assistant" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(screen.getByText(/Development mock: scripted replies/)).toBeInTheDocument();
    expect(await screen.findByText("Hello! I can plan a day.")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText("Your message")).toHaveFocus());

    await userEvent.type(screen.getByLabelText("Your message"), "2 adults on Tuesday{Enter}");
    expect(await screen.findByText(/described from the planner's own figures/)).toBeInTheDocument();
    expect(screen.getByText("This answer was written from the planner's own figures.")).toBeInTheDocument();
    await userEvent.click(screen.getByText("Option A: Best match"));
    expect(screen.getByText("Not verified")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Source" })).toHaveAttribute("href", "https://shop.aquarium.co.za/");

    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(launcher).toHaveFocus();
  });

  it("sends a plan to SL Tours only after the visitor confirms", async () => {
    const calls = server({
      "GET /api/v1/assistant/status": () => json({ available: true, mode: "LIVE", message: null }),
      "POST /api/v1/assistant/conversations": () => json(reply({ mode: "LIVE" })),
      "POST /api/v1/assistant/conversations/c1/messages": () => json(reply({ mode: "LIVE", text: "I've opened a form for Option A.", enquiryOptionId: "A",
        options: [{ id: "A", label: "Best match", itinerary }] })),
      "POST /api/v1/assistant/conversations/c1/enquiry": () => json(reply({ mode: "LIVE", text: "Sent. SL Tours has received option A with reference SLE12345.",
        enquiryReference: "SLE12345", options: [{ id: "A", label: "Best match", itinerary }] }))
    });
    wrap();
    await userEvent.click(await screen.findByRole("button", { name: "Ask our Cape Town assistant" }));
    expect(screen.queryByText(/Development mock/)).not.toBeInTheDocument();
    await screen.findByText("Hello! I can plan a day.");
    await userEvent.type(screen.getByLabelText("Your message"), "Send option A please{Enter}");
    const form = await screen.findByRole("form", { name: "Send option A" });
    expect(calls.some(c => c.url.endsWith("/enquiry"))).toBe(false);
    await userEvent.type(screen.getByLabelText("Name"), "Thandi");
    await userEvent.type(screen.getByLabelText("Email"), "thandi@example.com");
    const sendButton = screen.getByRole("button", { name: "Send to SL Tours" });
    expect(sendButton).toBeDisabled();
    await userEvent.click(screen.getByRole("checkbox", { name: "I want to send option A to SL Tours" }));
    await userEvent.click(sendButton);
    expect(await screen.findByText(/reference SLE12345/)).toBeInTheDocument();
    expect(form).not.toBeInTheDocument();
    const sent = calls.find(c => c.url.endsWith("/enquiry"))!;
    expect(sent.body).toMatchObject({ optionId: "A", name: "Thandi", email: "thandi@example.com", confirmed: true });
    expect(screen.queryByRole("button", { name: "Send option A to SL Tours" })).not.toBeInTheDocument();
  });

  it("falls back to the planner, pre-filled, when the assistant is unavailable", async () => {
    server({
      "GET /api/v1/assistant/status": () => json({ available: true, mode: "LIVE", message: null }),
      "POST /api/v1/assistant/conversations": () => json(reply({ mode: "LIVE" })),
      "POST /api/v1/assistant/conversations/c1/messages": () => json(reply({ mode: "LIVE", status: "UNAVAILABLE",
        text: "The assistant can't answer right now. You can use the day planner with the details you've given." }))
    });
    wrap();
    await userEvent.click(await screen.findByRole("button", { name: "Ask our Cape Town assistant" }));
    await screen.findByText("Hello! I can plan a day.");
    await userEvent.type(screen.getByLabelText("Your message"), "Plan a day{Enter}");
    expect(await screen.findByRole("link", { name: "Open the day planner with your details" })).toHaveAttribute("href", "/explore/plan?adults=2");
  });
});
