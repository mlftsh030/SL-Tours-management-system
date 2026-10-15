import { act, render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { IDLE_NOTICE_KEY, useIdleSignOut } from "./useIdleSignOut";
import { recordActivity } from "../../lib/activity";
import { request } from "../../lib/api";

function Probe() { useIdleSignOut(true); return null; }

describe("idle sign-out", () => {
  beforeEach(() => { vi.useFakeTimers({ toFake: ["setInterval", "clearInterval", "Date"] }); window.localStorage.clear(); });
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

  it("signs out after 30 minutes without interaction even though polling continues", async () => {
    const fetcher = vi.fn().mockImplementation((url: string) => Promise.resolve(url.endsWith("/csrf")
      ? new Response(JSON.stringify({ token: "t", headerName: "X-CSRF-TOKEN" }))
      : new Response(null, { status: 204 })));
    vi.stubGlobal("fetch", fetcher);
    const client = new QueryClient();
    render(<QueryClientProvider client={client}><Probe /></QueryClientProvider>);
    await act(async () => { vi.advanceTimersByTime(29 * 60_000); });
    expect(fetcher).not.toHaveBeenCalledWith("/api/v1/auth/logout", expect.anything());
    await act(async () => { vi.advanceTimersByTime(60_000); });
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    expect(fetcher).toHaveBeenCalledWith("/api/v1/auth/logout", expect.objectContaining({ method: "POST" }));
    expect(window.sessionStorage.getItem(IDLE_NOTICE_KEY)).toBe("1");
  });

  it("interaction postpones the sign-out", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ authenticated: true }))));
    render(<QueryClientProvider client={new QueryClient()}><Probe /></QueryClientProvider>);
    await act(async () => { vi.advanceTimersByTime(20 * 60_000); });
    act(() => { window.dispatchEvent(new KeyboardEvent("keydown")); });
    await act(async () => { vi.advanceTimersByTime(20 * 60_000); });
    expect(fetch).not.toHaveBeenCalledWith("/api/v1/auth/logout", expect.anything());
  });

  it("labels requests made without recent interaction as background refreshes", async () => {
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({}))));
    vi.stubGlobal("fetch", fetcher);
    recordActivity();
    await request("/api/v1/admin/dashboard");
    expect(fetcher.mock.calls[0][1].headers.has("X-SLTours-Background")).toBe(false);
    vi.advanceTimersByTime(2 * 60_000);
    await request("/api/v1/admin/dashboard");
    expect(fetcher.mock.calls[1][1].headers.get("X-SLTours-Background")).toBe("1");
  });
});
