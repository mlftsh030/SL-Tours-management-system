import { useRef, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Download } from "lucide-react";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { displayDate, formatMoney, kindLabels, paymentStatusLabels } from "../billing/money";
import type { CustomerDocument } from "../billing/billingTypes";
import { documentPdfUrl, formatDateTime, listDocuments, uploadProof } from "./accountApi";
import { EmptyAction } from "./EmptyAction";

const MAX_BYTES = 15 * 1024 * 1024;
const ACCEPT = "application/pdf,image/jpeg,image/png,image/webp";

export function AccountDocuments() {
  const documents = useQuery({ queryKey: ["account", "documents"], queryFn: listDocuments, retry: false });
  return <section aria-labelledby="documents-title">
    <h2 id="documents-title">Invoices and quotations</h2>
    {documents.isLoading && <LoadingState label="Loading your documents" />}
    {documents.isError && <ErrorState message={documents.error.message} />}
    {documents.data && !documents.data.length && <EmptyAction title="No invoices or quotations yet"
      text="When SL Tours sends you a quotation or invoice, you can download it here and send proof of payment."
      actions={[{ to: "/book-private-trip", label: "Request a quote", primary: true }, { to: "/experiences", label: "Explore tours" }]} />}
    <ul className="account-list">{documents.data?.map(d => <DocumentItem key={d.id} document={d} />)}</ul>
    {!!documents.data?.length && <p className="form-note">Balances show payments SL Tours has confirmed. Proof of payment is checked by our team before
      the balance changes.</p>}
  </section>;
}

function DocumentItem({ document: d }: { document: CustomerDocument }) {
  const invoice = d.kind === "INVOICE";
  return <li className="form-surface">
    <div className="account-item-head"><h3>{kindLabels[d.kind]} {d.displayNumber}</h3>
      {invoice ? <span className={`admin-badge account-pay-${d.paymentStatus.toLowerCase()}`}>{paymentStatusLabels[d.paymentStatus]}</span>
        : <span className="admin-badge">{d.status === "ACCEPTED" ? "Accepted" : d.status === "DECLINED" ? "Declined" : "Open"}</span>}</div>
    <dl className="account-facts">
      <div><dt>Issued</dt><dd>{displayDate(d.issueDate)}</dd></div>
      {d.dueDate && <div><dt>{invoice ? "Due" : "Valid until"}</dt><dd>{displayDate(d.dueDate)}</dd></div>}
      <div><dt>Total</dt><dd>{formatMoney(d.totalCents, d.currency)}</dd></div>
      {invoice && <div><dt>Balance</dt><dd>{formatMoney(d.balanceCents, d.currency)}</dd></div>}
      {d.bookingReference && <div><dt>Booking</dt><dd>{d.bookingReference}</dd></div>}
    </dl>
    <div className="account-doc-actions">
      <a className="button button-secondary" href={documentPdfUrl(d.id)} download={d.filename}><Download aria-hidden="true" />Download PDF</a>
      {d.proofsUploaded > 0 && <p className="account-verify-ok">Proof of payment sent {d.lastProofAt ? formatDateTime(d.lastProofAt) : ""}. We will confirm once the funds clear.</p>}
    </div>
    {d.canUploadProof && <ProofUpload id={d.id} label={`${kindLabels[d.kind]} ${d.displayNumber}`} />}
  </li>;
}

function ProofUpload({ id, label }: { id: string; label: string }) {
  const client = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [state, setState] = useState<{ pending?: boolean; error?: string; done?: boolean }>({});
  async function submit(event: FormEvent) {
    event.preventDefault();
    const file = input.current?.files?.[0];
    if (!file) { setState({ error: "Choose a PDF or photo of your proof of payment" }); input.current?.focus(); return; }
    if (file.size > MAX_BYTES) { setState({ error: "This file is larger than 15 MB. Send a smaller PDF or photo." }); return; }
    setState({ pending: true });
    try { await uploadProof(id, file); if (input.current) input.current.value = ""; setState({ done: true }); await client.invalidateQueries({ queryKey: ["account", "documents"] }); }
    catch (err) { setState({ error: (err as Error).message }); }
  }
  const inputId = `proof-${id}`;
  return <form className="account-proof" onSubmit={submit} aria-label={`Send proof of payment for ${label}`}>
    <label htmlFor={inputId}>Send proof of payment <small>PDF, JPG, PNG or WebP, up to 15 MB</small></label>
    <div className="account-proof-row">
      <input ref={input} id={inputId} type="file" accept={ACCEPT} aria-invalid={state.error ? true : undefined}
        aria-describedby={state.error ? `${inputId}-error` : undefined} onChange={() => setState({})} />
      <button type="submit" className="button button-primary" disabled={state.pending} aria-busy={state.pending}>{state.pending ? "Uploading…" : "Upload"}</button>
    </div>
    <div aria-live="polite">
      {state.error && <p className="auth-field-error" id={`${inputId}-error`}>{state.error}</p>}
      {state.done && <p className="account-verify-ok">Thank you. Your proof of payment has been sent to SL Tours.</p>}
    </div>
  </form>;
}
