import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ACTIVITY_EVENTS, IDLE_TIMEOUT_MS, lastActivity, recordActivity } from "../../lib/activity";
import { getSession, signOut } from "./authApi";

export const IDLE_NOTICE_KEY = "sltours.idleSignOut";
const KEEP_ALIVE_MS = 5 * 60_000;

/** Signs the user out after 30 minutes without interaction and keeps active users' sessions alive. */
export function useIdleSignOut(enabled: boolean) {
  const client = useQueryClient();
  useEffect(() => {
    if (!enabled) return;
    recordActivity();
    let lastKeepAlive = Date.now();
    const onActivity = () => {
      recordActivity();
      // A user filling in a long form makes no requests; tell the API they are still here.
      if (Date.now() - lastKeepAlive >= KEEP_ALIVE_MS) { lastKeepAlive = Date.now(); void getSession().catch(() => undefined); }
    };
    ACTIVITY_EVENTS.forEach(name => window.addEventListener(name, onActivity, { passive: true }));
    const timer = window.setInterval(() => {
      if (Date.now() - lastActivity() < IDLE_TIMEOUT_MS) return;
      window.clearInterval(timer);
      try { window.sessionStorage.setItem(IDLE_NOTICE_KEY, "1"); } catch { /* Notice is optional. */ }
      void signOut().catch(() => undefined).finally(() => {
        client.clear();
        client.setQueryData(["session"], { authenticated: false, email: null, name: null, role: null });
      });
    }, 15_000);
    return () => { window.clearInterval(timer); ACTIVITY_EVENTS.forEach(name => window.removeEventListener(name, onActivity)); };
  }, [enabled, client]);
}
