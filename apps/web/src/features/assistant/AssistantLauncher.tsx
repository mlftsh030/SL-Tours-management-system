import { lazy, Suspense, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { MessageCircle } from "lucide-react";
import { assistantStatus } from "./assistantApi";

const AssistantPanel = lazy(() => import("./AssistantPanel").then(m => ({ default: m.AssistantPanel })));

/** Discreet entry point. Hidden when chat is disabled: the day planner on the same page is the fallback. */
export function AssistantLauncher() {
  const status = useQuery({ queryKey: ["assistant-status"], queryFn: assistantStatus, retry: false, staleTime: 5 * 60_000 });
  const [open, setOpen] = useState(false);
  const button = useRef<HTMLButtonElement>(null);
  if (!status.data?.available) return null;
  return <>
    <button ref={button} type="button" className="assistant-launcher" aria-haspopup="dialog" aria-expanded={open} onClick={() => setOpen(true)}>
      <MessageCircle aria-hidden="true" />Ask our Cape Town assistant
    </button>
    {open && <Suspense fallback={null}>
      <AssistantPanel status={status.data} onClose={() => { setOpen(false); button.current?.focus(); }} />
    </Suspense>}
  </>;
}
