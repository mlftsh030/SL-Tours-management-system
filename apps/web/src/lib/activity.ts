/**
 * Tracks real user interaction so background polling is not mistaken for activity.
 * The API enforces the idle timeout; this module only labels requests and signs out promptly.
 */
const STORAGE_KEY = "sltours.lastActivity";
export const IDLE_TIMEOUT_MS = 30 * 60_000;
/** Requests within this window of an interaction are treated as user-initiated. */
const USER_INITIATED_WINDOW_MS = 60_000;
export const ACTIVITY_EVENTS = ["pointerdown", "keydown", "touchstart", "wheel"] as const;

let lastLocalActivity = Date.now();

export function recordActivity(now = Date.now()) {
  lastLocalActivity = now;
  try { window.localStorage.setItem(STORAGE_KEY, String(now)); } catch { /* Storage can be unavailable in private modes. */ }
}

/** Most recent interaction in this tab or any other open SL Tours tab. */
export function lastActivity() {
  let shared = 0;
  try { shared = Number(window.localStorage.getItem(STORAGE_KEY)) || 0; } catch { /* Fall back to this tab only. */ }
  return Math.max(lastLocalActivity, shared);
}

export function isBackgroundRequest(now = Date.now()) {
  return now - lastActivity() >= USER_INITIATED_WINDOW_MS;
}
