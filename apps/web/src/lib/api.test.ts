import { afterEach, describe, expect, it, vi } from "vitest";
import { request } from "./api";
import { signIn } from "../features/auth/authApi";

afterEach(() => vi.unstubAllGlobals());
describe("session API client", () => {
  it("attaches a fresh CSRF token and cookies to writes", async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ token: "masked-token", headerName: "X-CSRF-TOKEN" })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ reference: "SLT-TEST" })));
    vi.stubGlobal("fetch", fetcher);
    await request("/api/v1/bookings", { method: "POST", body: "{}" });
    const [, options] = fetcher.mock.calls[1];
    expect(options.credentials).toBe("include");
    expect(options.headers.get("X-CSRF-TOKEN")).toBe("masked-token");
    expect(options.headers.get("Content-Type")).toBe("application/json");
  });
  it("keeps form encoding when signing in and fetches session after authentication", async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ token: "token", headerName: "X-CSRF-TOKEN" })))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true, role: "ADMIN" })));
    vi.stubGlobal("fetch", fetcher);
    expect((await signIn("admin@example.com", "p&ss=word")).role).toBe("ADMIN");
    const [, options] = fetcher.mock.calls[1];
    expect(options.headers.get("Content-Type")).toBe("application/x-www-form-urlencoded");
    expect(new URLSearchParams(options.body).get("password")).toBe("p&ss=word");
    expect(fetcher.mock.calls[2][0]).toBe("/api/v1/auth/me");
  });
  it("shows the server message when an error carries an empty field list", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ token: "t", headerName: "X-CSRF-TOKEN" })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 409, message: "An account with this email already exists.", errors: {} }), { status: 409 })));
    await expect(request("/api/v1/auth/register", { method: "POST", body: "{}" })).rejects.toThrow("An account with this email already exists.");
  });
  it("notifies account screens when a protected request loses its session", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "Sign in" }), { status: 401 })));
    const handler = vi.fn(); window.addEventListener("session-expired", handler);
    await expect(request("/api/v1/admin/tours")).rejects.toThrow("Sign in");
    expect(handler).toHaveBeenCalledOnce(); window.removeEventListener("session-expired", handler);
  });
});
