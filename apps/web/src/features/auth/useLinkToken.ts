import { useState } from "react";

/** Reads ?token= once and removes it from the address bar, so it is not kept in history or shared by accident. */
export function useLinkToken() {
  const [token] = useState(() => {
    const params = new URLSearchParams(window.location.search);
    const value = params.get("token") ?? "";
    if (value) {
      params.delete("token");
      const rest = params.toString();
      window.history.replaceState(window.history.state, "", window.location.pathname + (rest ? `?${rest}` : ""));
    }
    return value;
  });
  return token;
}
