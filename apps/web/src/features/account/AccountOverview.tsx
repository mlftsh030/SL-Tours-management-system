import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { formatMoney } from "../billing/money";
import { describeLinked } from "../auth/RecoveryPages";
import { linkGuestRecords, listBookings, listDocuments, listEnquiries } from "./accountApi";

const OPEN = new Set(["NEW", "IN_PROGRESS", "AWAITING_CUSTOMER"]);
const FINISHED = new Set(["COMPLETED", "CANCELLED", "REFUNDED"]);

export function AccountOverview({ verified }: { verified: boolean }) {
  const enquiries = useQuery({ queryKey: ["account", "enquiries"], queryFn: listEnquiries, retry: false });
  const bookings = useQuery({ queryKey: ["account", "bookings", 0], queryFn: () => listBookings(0), retry: false });
  const documents = useQuery({ queryKey: ["account", "documents"], queryFn: listDocuments, retry: false });
  if (enquiries.isLoading || bookings.isLoading || documents.isLoading) return <LoadingState label="Loading your account" />;
  const failed = enquiries.error ?? bookings.error ?? documents.error;
  if (failed) return <ErrorState message={failed.message} />;
  const openEnquiries = enquiries.data?.filter(e => OPEN.has(e.status)).length ?? 0;
  const activeBookings = bookings.data?.content.filter(b => !FINISHED.has(b.status)).length ?? 0;
  const unpaid = documents.data?.filter(d => d.kind === "INVOICE" && d.status === "ISSUED" && d.balanceCents > 0) ?? [];
  const owing = unpaid.reduce((sum, d) => sum + (d.currency === "ZAR" ? d.balanceCents : 0), 0);
  const empty = !enquiries.data?.length && !bookings.data?.content.length && !documents.data?.length;
  return <section aria-labelledby="overview-title">
    <h2 id="overview-title" className="sr-only">Overview</h2>
    <div className="account-summary">
      <Link to="/account/enquiries" className="account-tile"><strong>{openEnquiries}</strong><span>Open {openEnquiries === 1 ? "enquiry" : "enquiries"}</span></Link>
      <Link to="/account/bookings" className="account-tile"><strong>{activeBookings}</strong><span>Active {activeBookings === 1 ? "booking" : "bookings"}</span></Link>
      <Link to="/account/documents" className="account-tile"><strong>{unpaid.length ? formatMoney(owing) : "Nothing due"}</strong>
        <span>{unpaid.length ? `Balance on ${unpaid.length} ${unpaid.length === 1 ? "invoice" : "invoices"}` : "Invoices"}</span></Link>
    </div>
    {empty && <div className="form-surface account-empty">
      <h3>Your next adventure starts here</h3>
      <p>Enquiries and bookings you make while signed in appear here, with their quotations and invoices.</p>
      <div className="account-empty-actions"><Link className="button button-primary" to="/experiences">Explore tours</Link>
        <Link className="button button-secondary" to="/book-private-trip">Request a quote</Link></div>
    </div>}
    {verified && <GuestRecords />}
  </section>;
}

/** After confirming the address, guest records made with it later can be added on request. */
function GuestRecords() {
  const client = useQueryClient();
  const [state, setState] = useState<{ pending?: boolean; message?: string; error?: string }>({});
  async function run() {
    setState({ pending: true });
    try {
      const linked = await linkGuestRecords();
      const text = describeLinked(linked);
      setState({ message: text ? `Added ${text}.` : "There was nothing new to add." });
      if (text) await client.invalidateQueries({ queryKey: ["account"] });
    } catch (err) { setState({ error: (err as Error).message }); }
  }
  return <div className="account-guest form-surface">
    <div><h3>Booked as a guest?</h3><p>Add bookings, enquiries and invoices made with your confirmed email address while you were not signed in.</p>
      <p aria-live="polite" className={state.error ? "auth-field-error" : "account-verify-ok"}>{state.error ?? state.message}</p></div>
    <button type="button" className="button button-secondary" onClick={run} disabled={state.pending} aria-busy={state.pending}>{state.pending ? "Looking…" : "Find earlier bookings"}</button>
  </div>;
}
