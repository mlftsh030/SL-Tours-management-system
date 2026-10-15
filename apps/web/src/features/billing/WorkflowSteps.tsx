import type { DocumentKind } from "./billingTypes";

/** The order work happens in: the numbers are real steps, and the current one is highlighted. */
export function WorkflowSteps({ current, kind }: { current: number; kind: DocumentKind }) {
  const steps = ["Customer and booking", "Services and pricing", "Preview", "Issue", "Download or share", ...(kind === "INVOICE" ? ["Record payment"] : ["Accept or convert"])];
  return <ol className="billing-steps" aria-label="Steps">
    {steps.map((s, i) => <li key={s} className={i + 1 < current ? "is-done" : i + 1 === current ? "is-current" : undefined} aria-current={i + 1 === current ? "step" : undefined}>
      <span>{i + 1}</span>{s}</li>)}
  </ol>;
}
