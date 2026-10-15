import { useRef } from "react";
import { newIdempotencyKey } from "./uuid";

/**
 * Idempotency key for a form submission. Resending exactly the same content (a retry after a lost response) reuses
 * the key, so the API stores it once. Changing anything after a send makes it a new submission with a new key:
 * the server answers a reused key with the original record, which would silently drop the customer's edits.
 */
export function useIdempotencyKey() {
  const state = useRef<{ key: string; sent?: string }>({ key: newIdempotencyKey() });
  return {
    keyFor(content: unknown): string {
      const sent = JSON.stringify(content);
      if (state.current.sent !== undefined && state.current.sent !== sent) state.current = { key: newIdempotencyKey() };
      state.current.sent = sent;
      return state.current.key;
    },
    /** Start a new submission, e.g. "Send another enquiry" or a new search. */
    reset() { state.current = { key: newIdempotencyKey() }; }
  };
}
