import { useEffect } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getSession } from "./authApi";
import { useIdleSignOut } from "./useIdleSignOut";
export function useSession() {
  const client = useQueryClient();
  useEffect(() => {
    const expired = () => {
      client.clear();
      client.setQueryData(["session"], { authenticated: false, email: null, name: null, role: null });
    };
    window.addEventListener("session-expired", expired);
    return () => window.removeEventListener("session-expired", expired);
  }, [client]);
  const session = useQuery({ queryKey: ["session"], queryFn: getSession, retry: false, staleTime: 0, refetchInterval: 60_000 });
  useIdleSignOut(session.data?.authenticated === true);
  return session;
}
