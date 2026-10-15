import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PrivateTripPage } from "./PrivateTripPage";
import { vehicleOption } from "../../test/fixtures";
import type { VehicleCard } from "../../types/api";

const quote = (total: number) => ({ hourlyRateCents: 65000, rateSource: "CATEGORY", sampleRate: true, requestedMinutes: 240, minimumMinutes: 180,
  billableMinutes: 240, incrementMinutes: 60, minimumApplied: false, roundedUp: false, baseCents: total, fees: [], feesCents: 0, totalCents: total });
const van = (total: number): VehicleCard => ({ id: "v-1", name: "Toyota Quantum", category: "VAN", capacity: 7, luggageCapacity: 6, amenities: ["Air conditioning"],
  description: null, imageUrl: null, imageAlt: "Toyota Quantum", hourlyRateCents: 65000, minimumMinutes: 180, sampleRate: true, quote: quote(total) as VehicleCard["quote"], quoteOnlyReason: null,
  optionSlug: "toyota-quantum", illustrativeImage: true, imageCredit: "Example credit" });
const disclosures = { billingIncrementMinutes: 60, includedKmPerHour: null, excessKmRateCents: null, overtimeHourlyRateCents: null, fees: [], sampleRatesShown: true };
const optionList = [vehicleOption("toyota-quantum", "Toyota Quantum", { typeLabel: "Minibus", category: "MINIBUS", passengerCapacity: 13 }),
  vehicleOption("hyundai-staria", "Hyundai Staria")];
const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));

afterEach(() => vi.unstubAllGlobals());

describe("Book a Private Trip", () => {
  it("prices on the server, refreshes after a price change and only confirms a stored request", async () => {
    let searchTotal = 260000;
    const submitted: Array<Record<string, unknown>> = [];
    vi.stubGlobal("fetch", vi.fn((url: string, options?: RequestInit) => {
      if (url.endsWith("/csrf")) return json({ token: "t", headerName: "X-CSRF-TOKEN" });
      if (url.includes("/api/v1/auth/me")) return json({ authenticated: false, email: null, name: null, role: null });
      if (url.includes("/private-trips/vehicle-options")) return json(optionList);
      if (url.includes("/private-trips/vehicles")) return json({ vehicles: [van(0)], disclosures });
      if (url.includes("/private-trips/availability")) return json({ startsAt: "", endsAt: "", durationMinutes: 240, passengers: 2, vehicles: [van(searchTotal)], disclosures });
      if (url.includes("/private-trips/requests")) {
        const body = JSON.parse(String(options?.body)); submitted.push(body);
        if (body.expectedTotalCents !== 280000) { searchTotal = 280000; return json({ message: "The price has changed since you started." }, 409); }
        return json({ reference: "SLT-2026-PRIV01", status: "ENQUIRY", message: "Request received. This is not yet a confirmed booking.", quote: quote(280000),
          startsAt: "2026-11-01T07:00:00Z", endsAt: "2026-11-01T11:00:00Z", vehicleName: "Toyota Quantum", duplicate: false }, 201);
      }
      return Promise.reject(new Error(`Unexpected ${url}`));
    }));
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><PrivateTripPage /></MemoryRouter></QueryClientProvider>);

    const choices = await screen.findByRole("group", { name: "Vehicles you can request" });
    expect(await within(choices).findAllByRole("img")).toHaveLength(2);
    await userEvent.type(screen.getByLabelText("Pickup location"), "Camps Bay");
    await userEvent.type(screen.getByLabelText("Destination or planned itinerary"), "Cape Point");
    await userEvent.click(screen.getByRole("button", { name: "Show available vehicles" }));
    expect(await screen.findByText("Sample rate · development only")).toBeInTheDocument();
    await userEvent.click(await screen.findByRole("button", { name: "Choose this vehicle" }));
    expect(screen.getByText(/This is a booking request/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Name"), "Lerato");
    await userEvent.type(screen.getByLabelText("Email"), "lerato@example.com");
    await userEvent.type(screen.getByLabelText("Mobile number"), "+27 82 111 2222");
    await userEvent.click(screen.getByRole("button", { name: "Request this trip" }));
    expect(await screen.findByText(/price has changed/)).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByText("R 2 800,00").length).toBeGreaterThan(0));
    await userEvent.click(screen.getByRole("button", { name: "Request this trip" }));
    expect(await screen.findByText("SLT-2026-PRIV01")).toBeInTheDocument();
    expect(screen.getByText(/not yet a confirmed booking/)).toBeInTheDocument();
    expect(submitted).toHaveLength(2);
    expect(submitted[0].idempotencyKey).toBe(submitted[1].idempotencyKey);
    expect(submitted[1]).toMatchObject({ vehicleId: "v-1", durationMinutes: 240, pickupLocation: "Camps Bay", expectedTotalCents: 280000 });
  });

  it("does not submit an earlier search after the date, time, duration or passengers change", async () => {
    const searches: string[] = [];
    const submitted: Array<Record<string, unknown>> = [];
    vi.stubGlobal("fetch", vi.fn((url: string, options?: RequestInit) => {
      if (url.endsWith("/csrf")) return json({ token: "t", headerName: "X-CSRF-TOKEN" });
      if (url.includes("/api/v1/auth/me")) return json({ authenticated: false, email: null, name: null, role: null });
      if (url.includes("/private-trips/vehicle-options")) return json(optionList);
      if (url.includes("/private-trips/vehicles")) return json({ vehicles: [van(0)], disclosures });
      if (url.includes("/private-trips/availability")) {
        searches.push(url);
        const total = url.includes("durationMinutes=300") ? 325000 : 260000;
        return json({ startsAt: "", endsAt: "", durationMinutes: 240, passengers: 2, vehicles: [van(total)], disclosures });
      }
      if (url.includes("/private-trips/requests")) {
        submitted.push(JSON.parse(String(options?.body)));
        return json({ reference: "SLT-2026-PRIV02", status: "ENQUIRY", message: "Request received.", quote: quote(325000),
          startsAt: "2026-11-02T08:00:00Z", endsAt: "2026-11-02T13:00:00Z", vehicleName: "Toyota Quantum", duplicate: false }, 201);
      }
      return Promise.reject(new Error(`Unexpected ${url}`));
    }));
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><PrivateTripPage /></MemoryRouter></QueryClientProvider>);

    await userEvent.type(await screen.findByLabelText("Pickup location"), "Camps Bay");
    await userEvent.type(screen.getByLabelText("Destination or planned itinerary"), "Cape Point");
    await userEvent.click(screen.getByRole("button", { name: "Show available vehicles" }));
    await userEvent.click(await screen.findByRole("button", { name: "Choose this vehicle" }));
    expect(screen.getByRole("button", { name: "Request this trip" })).toBeInTheDocument();

    // Change the trip after choosing: the earlier price and vehicle must no longer be submittable.
    await userEvent.selectOptions(screen.getByLabelText("How long?"), "300");
    expect(screen.queryByRole("button", { name: "Request this trip" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Choose this vehicle" })).not.toBeInTheDocument();
    expect(screen.getByText(/You changed your trip details/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Update available vehicles" }));
    await userEvent.click(await screen.findByRole("button", { name: "Choose this vehicle" }));
    await userEvent.type(screen.getByLabelText("Name"), "Lerato");
    await userEvent.type(screen.getByLabelText("Email"), "lerato@example.com");
    await userEvent.type(screen.getByLabelText("Mobile number"), "+27 82 111 2222");
    await userEvent.click(screen.getByRole("button", { name: "Request this trip" }));
    await screen.findByText("SLT-2026-PRIV02");
    expect(searches[searches.length - 1]).toContain("durationMinutes=300");
    expect(submitted).toHaveLength(1);
    expect(submitted[0]).toMatchObject({ durationMinutes: 300, expectedTotalCents: 325000, pickupLocation: "Camps Bay" });
  });

  it("keeps a vehicle chosen on the homepage, and falls back to a quote that still names it, with the referral", async () => {
    localStorage.setItem("sltours.referral", JSON.stringify({ code: "HARBOUR2", partnerName: "Harbour Hotel", capturedAt: new Date().toISOString(), source: "LINK" }));
    const quotes: Array<Record<string, unknown>> = [];
    const sedan = { ...van(260000), id: "v-s", name: "Sedan", category: "SEDAN" as const, capacity: 3 };
    vi.stubGlobal("fetch", vi.fn((url: string, options_?: RequestInit) => {
      if (url.endsWith("/csrf")) return json({ token: "t", headerName: "X-CSRF-TOKEN" });
      if (url.includes("/api/v1/auth/me")) return json({ authenticated: false, email: null, name: null, role: null });
      if (url.includes("/private-trips/vehicle-options")) return json(optionList);
      if (url.includes("/private-trips/vehicles")) return json({ vehicles: [van(0)], disclosures });
      if (url.includes("/private-trips/availability")) return json({ startsAt: "", endsAt: "", durationMinutes: 240, passengers: 2, vehicles: [sedan], disclosures });
      if (url.includes("/private-trips/quote-requests")) { quotes.push(JSON.parse(String(options_?.body)));
        return json({ reference: "SLE-2026-QUOTE1", status: "NEW", message: "Thank you.", duplicate: false }, 201); }
      return Promise.reject(new Error(`Unexpected ${url}`));
    }));
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={["/book-private-trip?vehicle=hyundai-staria&destination=Cango%20Caves"]}><PrivateTripPage /></MemoryRouter></QueryClientProvider>);
    expect(await screen.findByText(/You chose the/)).toHaveTextContent("You chose the Hyundai Staria (up to 8 passengers)");
    expect(screen.getByRole("button", { name: "Hyundai Staria selected" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByLabelText("Destination or planned itinerary")).toHaveValue("Cango Caves");
    await userEvent.type(screen.getByLabelText("Pickup location"), "Sea Point");
    await userEvent.click(screen.getByRole("button", { name: "Show available vehicles" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("No Hyundai Staria or similar vehicle is listed as free");
    await userEvent.click(screen.getByRole("button", { name: "Request a quote" }));
    expect(screen.getByLabelText(/Special requests/)).toHaveValue("Preferred vehicle: Hyundai Staria (up to 8 passengers)");
    expect(screen.getByRole("note")).toHaveTextContent("Referred by Harbour Hotel");
    await userEvent.type(screen.getByLabelText("Name"), "Thandi");
    await userEvent.type(screen.getByLabelText("Email"), "thandi@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Send quote request" }));
    await screen.findByText("SLE-2026-QUOTE1");
    expect(quotes[0]).toMatchObject({ preferredCategory: "VAN", preferredVehicle: "hyundai-staria", destination: "Cango Caves",
      specialRequests: "Preferred vehicle: Hyundai Staria (up to 8 passengers)", referral: { code: "HARBOUR2", source: "LINK" } });
    localStorage.clear();
  });

  it("pre-selects only the chosen model and names a same-category vehicle as an alternative the visitor must pick", async () => {
    const h1 = { ...van(240000), id: "v-h1", name: "Hyundai H1", category: "VAN" as const, capacity: 8, optionSlug: null, illustrativeImage: false, imageCredit: null };
    vi.stubGlobal("fetch", vi.fn((url: string) => {
      if (url.includes("/api/v1/auth/me")) return json({ authenticated: false, email: null, name: null, role: null });
      if (url.includes("/private-trips/vehicle-options")) return json(optionList);
      if (url.includes("/private-trips/vehicles")) return json({ vehicles: [], disclosures });
      if (url.includes("/private-trips/availability")) return json({ startsAt: "", endsAt: "", durationMinutes: 240, passengers: 2, vehicles: [h1], disclosures });
      return Promise.reject(new Error(`Unexpected ${url}`));
    }));
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={["/book-private-trip?vehicle=hyundai-staria"]}><PrivateTripPage /></MemoryRouter></QueryClientProvider>);
    await userEvent.type(await screen.findByLabelText("Pickup location"), "Sea Point");
    await userEvent.type(screen.getByLabelText("Destination or planned itinerary"), "Winelands");
    await userEvent.click(screen.getByRole("button", { name: "Show available vehicles" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("No Hyundai Staria is listed as free for that time and group size. A similar vehicle is free: Hyundai H1.");
    expect(screen.getByText("Alternative to the Hyundai Staria you chose")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Request this trip" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Choose this vehicle" }));
    expect(screen.getByRole("button", { name: "Request this trip" })).toBeInTheDocument();
  });

  it("shows every vehicle option with a labelled picture and lets the visitor choose one", async () => {
    vi.stubGlobal("fetch", vi.fn((url: string) => {
      if (url.includes("/api/v1/auth/me")) return json({ authenticated: false, email: null, name: null, role: null });
      if (url.includes("/private-trips/vehicle-options")) return json([...optionList, vehicleOption("coach-42-seater", "Coach", { category: "COACH",
        passengerCapacity: 42, capacityConfirmed: false, capacityNote: "No specific 42-seat model has been confirmed yet." })]);
      if (url.includes("/private-trips/vehicles")) return json({ vehicles: [], disclosures });
      return Promise.reject(new Error(`Unexpected ${url}`));
    }));
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={["/book-private-trip"]}><PrivateTripPage /></MemoryRouter></QueryClientProvider>);
    const choices = await screen.findByRole("group", { name: "Vehicles you can request" });
    expect(await within(choices).findAllByRole("img")).toHaveLength(3);
    expect(screen.getAllByText("Illustrative image—actual vehicle may differ")).toHaveLength(3);
    expect(screen.getByText("Up to 42 passengers (capacity to be confirmed)")).toBeInTheDocument();
    expect(screen.getByText("No specific 42-seat model has been confirmed yet.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Choose the Coach" }));
    expect(await screen.findByText(/You chose the/)).toHaveTextContent("You chose the Coach (up to 42 passengers (capacity to be confirmed))");
  });
});
