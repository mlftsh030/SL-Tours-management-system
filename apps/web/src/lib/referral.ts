import { useEffect, useState } from "react";
import { request } from "./api";

/**
 * Partner referral kept in this browser. Opening a referral link (?ref=CODE) stores the code; a newer link replaces an
 * older one. Commission eligibility is decided on the server from the partner's agreement, not here; the browser keeps a
 * code for at most a year so stale codes do not linger.
 */
export interface StoredReferral { code: string; partnerName: string; capturedAt: string; source: "LINK" | "CODE" }
const KEY = "sltours.referral";
const MAX_AGE_MS = 365 * 24 * 60 * 60 * 1000;
const EVENT = "referral-changed";

export function readReferral(): StoredReferral | null {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const value = JSON.parse(raw) as StoredReferral;
    if (!value.code || Date.now() - Date.parse(value.capturedAt) > MAX_AGE_MS) { localStorage.removeItem(KEY); return null; }
    return value;
  } catch { return null; }
}
function store(value: StoredReferral | null) {
  try { if (value) localStorage.setItem(KEY, JSON.stringify(value)); else localStorage.removeItem(KEY); } catch { /* storage unavailable */ }
  window.dispatchEvent(new Event(EVENT));
}
export const clearReferral = () => store(null);

export const lookupReferral = (code: string) =>
  request<{ code: string; partnerName: string }>(`/api/v1/partners/referrals/${encodeURIComponent(code.trim().toUpperCase())}`);

/** Validates and stores a code from a link or typed by the visitor. Unknown or inactive codes are not stored. */
export async function applyReferral(code: string, source: "LINK" | "CODE") {
  const found = await lookupReferral(code);
  store({ code: found.code, partnerName: found.partnerName, capturedAt: new Date().toISOString(), source });
  return found;
}

/** Adds the stored referral (if any) to a public booking or enquiry submission. */
export function withReferral<T extends object>(body: T): T & { referral: { code: string; capturedAt: string; source: string } | null } {
  const r = readReferral();
  return { ...body, referral: r ? { code: r.code, capturedAt: r.capturedAt, source: r.source } : null };
}

export function useReferral() {
  const [value, setValue] = useState(readReferral);
  useEffect(() => {
    const update = () => setValue(readReferral());
    window.addEventListener(EVENT, update);
    window.addEventListener("storage", update);
    return () => { window.removeEventListener(EVENT, update); window.removeEventListener("storage", update); };
  }, []);
  return value;
}
