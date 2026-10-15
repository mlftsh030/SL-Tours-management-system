import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { Download, FileText, Pencil, Share2 } from "lucide-react";
import { newIdempotencyKey } from "../../lib/uuid";
import { localDate } from "../../lib/dates";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { dateTime } from "../operations/labels";
import {
  attachProof, bookingFromQuotation, confirmPayment, convertQuotation, decideQuotation, documentFileUrl, duplicateDocument, getDocument, issueDocument, receiptFileUrl,
  recordPayment, recordShareAction, rejectPayment, reversePayment, reviseDocument, revokeLink, voidDocument
} from "./billingApi";
import type { DocumentDetail, Payment, PaymentMethod } from "./billingTypes";
import { DocumentPreview } from "./DocumentPreview";
import { ShareDialog } from "./ShareDialog";
import { WorkflowSteps } from "./WorkflowSteps";
import { displayDate, documentStatusLabels, formatMoney, kindLabels, methodLabels, parseMoney, paymentStateLabels, paymentStatusLabels } from "./money";

export function DocumentPage({ id, isAdmin }: { id: string; isAdmin: boolean }) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const detail = useQuery({ queryKey: ["billing", "document", id], queryFn: () => getDocument(id), retry: false });
  const [share, setShare] = useState<{ paymentId?: string; filename: string; pdfUrl: string } | null>(null);
  const [confirming, setConfirming] = useState<"" | "issue" | "void" | "revise">("");
  const [reason, setReason] = useState("");
  const refresh = async (next: DocumentDetail) => {
    client.setQueryData(["billing", "document", id], next);
    setConfirming(""); setReason("");
    await client.invalidateQueries({ queryKey: ["billing", "documents"] });
  };
  const issue = useMutation({ mutationFn: (d: DocumentDetail) => issueDocument(d.summary.id, d.version), onSuccess: refresh });
  const voiding = useMutation({ mutationFn: (d: DocumentDetail) => voidDocument(d.summary.id, d.version, reason), onSuccess: refresh });
  const decide = useMutation({ mutationFn: ({ d, decision }: { d: DocumentDetail; decision: "ACCEPTED" | "DECLINED" }) => decideQuotation(d.summary.id, d.version, decision), onSuccess: refresh });
  const open = (other: DocumentDetail) => { client.setQueryData(["billing", "document", other.summary.id], other); navigate(`/operations/billing/${other.summary.id}`); };
  const revise = useMutation({ mutationFn: () => reviseDocument(id), onSuccess: open });
  const duplicate = useMutation({ mutationFn: () => duplicateDocument(id), onSuccess: open });
  const convert = useMutation({ mutationFn: () => convertQuotation(id), onSuccess: r => navigate(`/operations/billing/${r.invoiceId}`) });
  const [bookingAsk, setBookingAsk] = useState<{ travelDate: string; guests: string } | null>(null);
  const makeBooking = useMutation({
    mutationFn: () => bookingFromQuotation(id, bookingAsk ? { travelDate: bookingAsk.travelDate || undefined, guests: bookingAsk.guests ? Number(bookingAsk.guests) : undefined } : undefined),
    onSuccess: async () => { setBookingAsk(null); await client.invalidateQueries({ queryKey: ["billing", "document", id] }); await client.invalidateQueries({ queryKey: ["admin"] }); }
  });

  if (detail.isLoading) return <LoadingState label="Loading document" />;
  if (detail.isError) return <ErrorState message={detail.error.message} />;
  const d = detail.data!;
  const s = d.summary;
  const invoice = s.kind === "INVOICE";
  const draft = s.status === "DRAFT";
  const issued = s.status === "ISSUED" || s.status === "ACCEPTED" || s.status === "DECLINED";
  const step = draft ? 3 : invoice ? (s.paymentStatus === "PAID" ? 7 : 6) : s.status === "ACCEPTED" ? 7 : 5;
  const busy = issue.isPending || voiding.isPending || revise.isPending || duplicate.isPending || convert.isPending || decide.isPending || makeBooking.isPending;
  const error = [issue, voiding, revise, duplicate, convert, decide, makeBooking].find(m => m.isError)?.error as Error | undefined;
  // A quotation without a service date or passenger count needs them before it can become a booking.
  const needsBookingDetails = !d.content.serviceStart || !d.content.passengers;
  const customerProofs = d.attachments.filter(a => !a.paymentId);
  const pdfUrl = documentFileUrl(id, "pdf");

  return <section className="billing-document">
    <div className="admin-heading"><div><p className="eyebrow"><Link to="/operations/billing">Invoices and quotations</Link></p>
      <h1>{kindLabels[s.kind]} {draft ? "draft" : s.displayNumber}</h1>
      <p>{d.customerId ? <Link to={`/operations/customers/${d.customerId}`}>{s.customerName}</Link> : s.customerName}
        {d.linkedBookingReference ? <> · booking <strong>{d.linkedBookingReference}</strong></> : ""}
        {d.enquiryReference ? <> · answers enquiry <Link to={`/operations/enquiries?q=${encodeURIComponent(d.enquiryReference)}`}>{d.enquiryReference}</Link></> : ""}</p></div>
      <div className="billing-badges">
        <span className={`admin-badge billing-status-${s.status.toLowerCase()}`}>{documentStatusLabels[s.status]}</span>
        {invoice && !draft && s.status !== "VOID" && <span className={`admin-badge billing-pay-${s.paymentStatus.toLowerCase()}`}>{paymentStatusLabels[s.paymentStatus]}</span>}
        {s.overdue && <span className="admin-badge billing-overdue">Overdue</span>}
      </div></div>
    <WorkflowSteps current={step} kind={s.kind} />

    <dl className="billing-figures">
      <div><dt>Total</dt><dd>{formatMoney(s.totalCents, s.currency)}</dd></div>
      {invoice && <><div><dt>Paid (confirmed)</dt><dd>{formatMoney(s.paidCents, s.currency)}</dd></div>
        {d.pendingCents > 0 && <div><dt>Waiting for confirmation</dt><dd>{formatMoney(d.pendingCents, s.currency)}</dd></div>}
        <div className="billing-balance"><dt>Balance</dt><dd>{formatMoney(s.balanceCents, s.currency)}</dd></div></>}
      <div><dt>{invoice ? "Due" : "Valid until"}</dt><dd>{displayDate(s.dueDate)}</dd></div>
      <div><dt>{draft ? "Created" : "Issued"}</dt><dd>{draft ? `${dateTime(d.createdAt)} by ${d.createdBy}` : `${displayDate(s.issueDate)} by ${d.issuedBy}`}</dd></div>
    </dl>

    {s.status === "VOID" && <p className="admin-warning">Void{d.voidReason ? `: ${d.voidReason}` : ""}. {d.supersededBy && <Link to={`/operations/billing/${d.supersededBy.id}`}>Open the replacement {d.supersededBy.displayNumber}</Link>}</p>}
    {d.revisionOf && <p className="admin-callout">Revision of {kindLabels[d.revisionOf.kind].toLowerCase()} <Link to={`/operations/billing/${d.revisionOf.id}`}>{d.revisionOf.displayNumber}</Link>. Issuing it voids the original and moves its payments here.</p>}
    {d.sourceQuotation && <p className="form-note">Created from quotation <Link to={`/operations/billing/${d.sourceQuotation.id}`}>{d.sourceQuotation.displayNumber}</Link>.</p>}
    {d.convertedInvoice && <p className="form-note">Converted to invoice <Link to={`/operations/billing/${d.convertedInvoice.id}`}>{d.convertedInvoice.displayNumber}</Link>.</p>}
    {d.pendingRevision && <p className="form-note">A revision is being prepared: <Link to={`/operations/billing/${d.pendingRevision.id}`}>open it</Link>.</p>}

    <div className="admin-actions billing-actions">
      {draft && <Link className="button button-primary" to={`/operations/billing/${id}/edit`}><Pencil aria-hidden="true" />Edit draft</Link>}
      {draft && isAdmin && <button className="button button-primary" disabled={busy} onClick={() => setConfirming("issue")}>Issue {kindLabels[s.kind].toLowerCase()}</button>}
      {issued && <button className="button button-primary" onClick={() => setShare({ filename: d.pdfFilename, pdfUrl })}><Share2 aria-hidden="true" />Share</button>}
      <a className="button button-secondary" href={pdfUrl} target="_blank" rel="noopener" onClick={() => recordShareAction(id, "PDF_DOWNLOADED").catch(() => undefined)}><FileText aria-hidden="true" />{draft ? "Preview PDF" : "Open PDF"}</a>
      <a className="button button-secondary" href={documentFileUrl(id, "docx")} onClick={() => recordShareAction(id, "DOCX_DOWNLOADED").catch(() => undefined)}><Download aria-hidden="true" />Word (.docx)</a>
      {!invoice && s.status === "ISSUED" && isAdmin && <>
        <button className="button button-secondary" disabled={busy} onClick={() => decide.mutate({ d, decision: "ACCEPTED" })}>Mark accepted</button>
        <button className="button button-quiet" disabled={busy} onClick={() => decide.mutate({ d, decision: "DECLINED" })}>Mark declined</button></>}
      {!invoice && (s.status === "ISSUED" || s.status === "ACCEPTED") && isAdmin && !d.linkedBookingReference &&
        <button className="button button-primary" disabled={busy} onClick={() => needsBookingDetails ? setBookingAsk({ travelDate: d.content.serviceStart ?? "", guests: d.content.passengers ? String(d.content.passengers) : "" }) : makeBooking.mutate()}>
          {makeBooking.isPending ? "Creating booking…" : "Accept and create booking"}</button>}
      {!invoice && (s.status === "ISSUED" || s.status === "ACCEPTED") && isAdmin && !d.convertedInvoice &&
        <button className="button button-secondary" disabled={busy} onClick={() => convert.mutate()}>{convert.isPending ? "Converting…" : "Convert to invoice"}</button>}
      <button className="button button-quiet" disabled={busy} onClick={() => duplicate.mutate()}>Duplicate</button>
      {issued && isAdmin && !d.pendingRevision && <button className="button button-quiet" disabled={busy} onClick={() => setConfirming("revise")}>Revise</button>}
      {s.status !== "VOID" && isAdmin && <button className="button button-quiet billing-danger" disabled={busy} onClick={() => setConfirming("void")}>{draft ? "Discard draft" : "Void"}</button>}
    </div>
    <p className="form-note">The Word file is for your own edits. Changes made in Word stay in that file; they do not change the {kindLabels[s.kind].toLowerCase()} stored here.</p>
    {!isAdmin && draft && <p className="form-note">An administrator issues documents and confirms payments.</p>}

    {confirming === "issue" && <div className="admin-callout billing-confirm" role="alertdialog" aria-labelledby="issue-title">
      <h2 id="issue-title">Issue this {kindLabels[s.kind].toLowerCase()}?</h2>
      <p>It gets the next official number and is frozen as shown, including today's company and banking details. Afterwards it can only be corrected with a revision.</p>
      {!d.numberingConfirmed && <p className="admin-warning">The next {s.kind === "INVOICE" ? "invoice" : "quotation"} number has not been confirmed yet. <Link to="/operations/billing-settings">Confirm it in billing settings</Link> first.</p>}
      <div className="admin-actions"><button className="button button-primary" disabled={busy || !d.numberingConfirmed} onClick={() => issue.mutate(d)}>{issue.isPending ? "Issuing…" : `Issue for ${formatMoney(s.totalCents, s.currency)}`}</button>
        <button className="button button-secondary" onClick={() => setConfirming("")}>Not yet</button></div></div>}
    {confirming === "revise" && <div className="admin-callout billing-confirm" role="alertdialog" aria-labelledby="revise-title">
      <h2 id="revise-title">Correct this {kindLabels[s.kind].toLowerCase()} with a revision?</h2>
      <p>A new draft is created with the same details. When the revision is issued it gets a new number, this one becomes void, and confirmed payments move to the revision.</p>
      <div className="admin-actions"><button className="button button-primary" disabled={busy} onClick={() => revise.mutate()}>Create revision draft</button>
        <button className="button button-secondary" onClick={() => setConfirming("")}>Cancel</button></div></div>}
    {confirming === "void" && <form className="admin-callout billing-confirm" onSubmit={e => { e.preventDefault(); voiding.mutate(d); }} aria-labelledby="void-title">
      <h2 id="void-title">{draft ? "Discard this draft?" : `Void ${kindLabels[s.kind].toLowerCase()} ${s.displayNumber}?`}</h2>
      <p>{draft ? "It will no longer appear in the list." : "Its number is kept and never reused. Customer links stop working. Invoices with confirmed payments cannot be voided; revise them instead."}</p>
      <label>Reason<input required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} /></label>
      <div className="admin-actions"><button className="button button-primary billing-danger" disabled={busy || !reason.trim()} type="submit">{voiding.isPending ? "Saving…" : draft ? "Discard" : "Void"}</button>
        <button type="button" className="button button-secondary" onClick={() => setConfirming("")}>Cancel</button></div></form>}
    {bookingAsk && <form className="admin-callout billing-confirm" aria-labelledby="booking-title" onSubmit={e => { e.preventDefault(); makeBooking.mutate(); }}>
      <h2 id="booking-title">Create the booking</h2>
      <p>The booking takes the customer, services, stops and total ({formatMoney(s.totalCents, s.currency)}) from this quotation. It has no
        {!d.content.serviceStart && " service date"}{!d.content.serviceStart && !d.content.passengers && " or"}{!d.content.passengers && " passenger count"}, so enter it here.</p>
      <div className="form-grid admin-form-grid">
        {!d.content.serviceStart && <label>Travel date<input required type="date" min={localDate()} value={bookingAsk.travelDate} onChange={e => setBookingAsk({ ...bookingAsk, travelDate: e.target.value })} /></label>}
        {!d.content.passengers && <label>Passengers<input required type="number" min={1} max={60} value={bookingAsk.guests} onChange={e => setBookingAsk({ ...bookingAsk, guests: e.target.value })} /></label>}
      </div>
      <div className="admin-actions"><button className="button button-primary" type="submit" disabled={busy}>{makeBooking.isPending ? "Creating…" : "Create booking"}</button>
        <button type="button" className="button button-secondary" onClick={() => setBookingAsk(null)}>Cancel</button></div></form>}
    {makeBooking.data && <p className="admin-success" role="status">Booking {makeBooking.data.bookingReference} {makeBooking.data.created ? "created" : "already exists for this quotation"}.</p>}
    {error && <ErrorState message={error.message} />}

    {share && <ShareDialog document={d} paymentId={share.paymentId} filename={share.filename} pdfUrl={share.pdfUrl} onClose={async () => { setShare(null); await client.invalidateQueries({ queryKey: ["billing", "document", id] }); }} />}

    <div className="billing-document-grid">
      <div className="billing-document-main">
        {customerProofs.length > 0 && <section className="form-surface"><h2>Proof of payment from the customer</h2>
          <p className="form-note">Uploaded by the customer from their account. Check it against the bank statement, then record and confirm the payment.</p>
          <ul className="billing-attachments">{customerProofs.map(a => <li key={a.id}><a href={a.url} target="_blank" rel="noopener">{a.filename ?? "Proof of payment"}</a>
            <small> {a.contentType === "application/pdf" ? "PDF" : "Photo"}, {Math.ceil(a.sizeBytes / 1024)} KB · {a.uploadedBy} · {dateTime(a.uploadedAt)}</small></li>)}</ul></section>}
        {invoice && !draft && <Payments d={d} isAdmin={isAdmin} onChange={refresh} onShareReceipt={p => setShare({ paymentId: p.id, filename: p.receiptFilename!, pdfUrl: receiptFileUrl(p.id, "pdf") })} />}
        {d.internalNotes && <section className="form-surface billing-internal-notes"><h2>Internal notes</h2><p className="admin-message">{d.internalNotes}</p><p className="form-note">Never printed or shared.</p></section>}
        {d.links.length > 0 && <Links d={d} onRevoked={() => client.invalidateQueries({ queryKey: ["billing", "document", id] })} />}
        <section className="form-surface"><h2>History</h2>
          <ol className="billing-history">{d.events.map((e, i) => <li key={i}><time>{dateTime(e.createdAt)}</time><strong>{e.event.replace(/_/g, " ").toLowerCase()}</strong>
            <span>{e.detail}</span><small>{e.actor}</small></li>)}</ol></section>
      </div>
      <aside className="billing-preview">{d.view ? <DocumentPreview view={d.view} /> : <p className="admin-warning">This draft has errors. Edit it to see the preview.</p>}</aside>
    </div>
  </section>;
}

function Payments({ d, isAdmin, onChange, onShareReceipt }: { d: DocumentDetail; isAdmin: boolean; onChange: (d: DocumentDetail) => Promise<void>; onShareReceipt: (p: Payment) => void }) {
  const s = d.summary;
  const [form, setForm] = useState({ amount: "", paidOn: localDate(), method: "EFT" as PaymentMethod, reference: "", notes: "" });
  const [key, setKey] = useState(newIdempotencyKey);
  const [proof, setProof] = useState<File | null>(null);
  const [localError, setLocalError] = useState("");
  const record = useMutation({
    mutationFn: async () => {
      const amount = parseMoney(form.amount);
      if (!amount) throw new Error("Enter the amount received");
      let next = await recordPayment(s.id, { idempotencyKey: key, amountCents: amount, paidOn: form.paidOn, method: form.method, reference: form.reference, notes: form.notes });
      const payment = next.payments[next.payments.length - 1];
      if (proof && payment) next = await attachProof(payment.id, proof, "Proof of payment");
      return next;
    },
    onSuccess: async next => { setForm(f => ({ ...f, amount: "", reference: "", notes: "" })); setProof(null); setKey(newIdempotencyKey()); await onChange(next); }
  });
  const open = s.balanceCents - d.pendingCents;
  return <section className="form-surface billing-payments"><h2>Payments</h2>
    <p className="form-note">Proof of payment is evidence only. A payment counts towards the balance once an administrator confirms the money is in the account.</p>
    {d.payments.length === 0 && <p className="admin-empty">No payments recorded yet.</p>}
    <ul className="billing-payment-list">{d.payments.map(p => <PaymentRow key={p.id} p={p} d={d} isAdmin={isAdmin} onChange={onChange} onShareReceipt={onShareReceipt} />)}</ul>
    {s.status === "ISSUED" && open > 0 && <form className="billing-payment-form" onSubmit={(e: FormEvent) => { e.preventDefault(); setLocalError(""); if (!record.isPending) record.mutate(); }}>
      <h3>Record a payment</h3>
      <div className="form-grid admin-form-grid">
        <label>Amount (R) <small>Up to {formatMoney(open, s.currency)}</small><input required inputMode="decimal" value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} /></label>
        <label>Date paid<input required type="date" max={localDate()} value={form.paidOn} onChange={e => setForm({ ...form, paidOn: e.target.value })} /></label>
        <label>Method<select value={form.method} onChange={e => setForm({ ...form, method: e.target.value as PaymentMethod })}>{Object.entries(methodLabels).map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
        <label>Reference <small>As on the bank statement</small><input maxLength={120} value={form.reference} onChange={e => setForm({ ...form, reference: e.target.value })} /></label>
        <label className="full-field">Proof of payment <small>PDF or photo, optional</small><input type="file" accept="application/pdf,image/jpeg,image/png,image/webp" onChange={e => setProof(e.target.files?.[0] ?? null)} /></label>
        <label className="full-field">Notes <small>Staff only</small><input maxLength={500} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} /></label>
      </div>
      {(record.isError || localError) && <ErrorState message={localError || (record.error as Error).message} />}
      <button className="button button-primary" type="submit" disabled={record.isPending}>{record.isPending ? "Saving…" : "Record payment"}</button>
    </form>}
  </section>;
}

function PaymentRow({ p, d, isAdmin, onChange, onShareReceipt }: { p: Payment; d: DocumentDetail; isAdmin: boolean; onChange: (d: DocumentDetail) => Promise<void>; onShareReceipt: (p: Payment) => void }) {
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  const [proof, setProof] = useState<File | null>(null);
  const confirm = useMutation({ mutationFn: () => confirmPayment(p.id, p.version), onSuccess: onChange });
  const reject = useMutation({ mutationFn: () => rejectPayment(p.id, p.version, reason), onSuccess: onChange });
  const upload = useMutation({ mutationFn: () => attachProof(p.id, proof!, "Proof of payment"), onSuccess: async next => { setProof(null); await onChange(next); } });
  const [reversing, setReversing] = useState(false);
  const reverse = useMutation({ mutationFn: () => reversePayment(p.id, p.version, reason), onSuccess: async next => { setReversing(false); setReason(""); await onChange(next); } });
  const currency = d.summary.currency;
  return <li className={`billing-payment billing-payment-${p.status.toLowerCase()}`}>
    <div className="billing-payment-head"><strong>{formatMoney(p.amountCents, currency)}</strong>
      <span className={`admin-badge billing-state-${p.status.toLowerCase()}`}>{paymentStateLabels[p.status]}</span></div>
    <p>{methodLabels[p.method]} · paid {displayDate(p.paidOn)}{p.reference ? ` · ref ${p.reference}` : ""}</p>
    <small>Recorded by {p.recordedBy} {dateTime(p.recordedAt)}{p.decidedBy ? ` · ${p.status === "CONFIRMED" ? "confirmed" : "rejected"} by ${p.decidedBy}` : ""}</small>
    {p.notes && <small>Note: {p.notes}</small>}
    {p.rejectReason && <small>Reason: {p.rejectReason}</small>}
    {p.status === "REVERSED" && <small>Reversed by {p.reversedBy} {p.reversedAt ? dateTime(p.reversedAt) : ""}: {p.reverseReason}. The receipt stays on file; the balance and booking were updated.</small>}
    {p.attachments.length > 0 && <ul className="billing-attachments">{p.attachments.map(a => <li key={a.id}>
      <a href={a.url} target="_blank" rel="noopener">{a.filename ?? "Proof of payment"}</a> <small>{a.contentType === "application/pdf" ? "PDF" : "Photo"}, {Math.ceil(a.sizeBytes / 1024)} KB</small></li>)}</ul>}
    <div className="admin-actions">
      {p.status !== "REJECTED" && <label className="billing-proof-upload">Add proof<input type="file" accept="application/pdf,image/jpeg,image/png,image/webp" onChange={e => setProof(e.target.files?.[0] ?? null)} /></label>}
      {proof && <button className="button button-secondary" disabled={upload.isPending} onClick={() => upload.mutate()}>{upload.isPending ? "Uploading…" : `Attach ${proof.name}`}</button>}
      {p.status === "PENDING" && isAdmin && <button className="button button-primary" disabled={confirm.isPending} onClick={() => confirm.mutate()}>{confirm.isPending ? "Confirming…" : "Funds received: confirm"}</button>}
      {p.status === "PENDING" && isAdmin && !rejecting && <button className="button button-quiet" onClick={() => setRejecting(true)}>Reject</button>}
      {p.status === "CONFIRMED" && isAdmin && !reversing && d.summary.status === "ISSUED" && <button className="button button-quiet billing-danger" onClick={() => setReversing(true)}>Reverse</button>}
      {p.status === "REVERSED" && <a className="button button-quiet" href={receiptFileUrl(p.id, "pdf")} target="_blank" rel="noopener">Receipt {p.receiptNumber} (reversed)</a>}
      {p.status === "CONFIRMED" && <>
        <a className="button button-secondary" href={receiptFileUrl(p.id, "pdf")} target="_blank" rel="noopener">Receipt {p.receiptNumber} (PDF)</a>
        <a className="button button-quiet" href={receiptFileUrl(p.id, "docx")}>Receipt (Word)</a>
        <button className="button button-quiet" onClick={() => onShareReceipt(p)}>Share receipt</button></>}
    </div>
    {rejecting && <form className="admin-inline-form" onSubmit={e => { e.preventDefault(); reject.mutate(); }}>
      <label>Why is it rejected?<input required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} placeholder="e.g. Not in the bank account" /></label>
      <button className="button button-secondary" disabled={!reason.trim() || reject.isPending} type="submit">Reject payment</button>
      <button className="button button-quiet" type="button" onClick={() => setRejecting(false)}>Cancel</button></form>}
    {reversing && <form className="admin-inline-form" onSubmit={e => { e.preventDefault(); reverse.mutate(); }}>
      <label>Why is this confirmed payment reversed?<input required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} placeholder="e.g. EFT bounced, recorded twice" /></label>
      <button className="button button-secondary billing-danger" disabled={!reason.trim() || reverse.isPending} type="submit">{reverse.isPending ? "Reversing…" : "Reverse payment"}</button>
      <button className="button button-quiet" type="button" onClick={() => setReversing(false)}>Cancel</button></form>}
    {[confirm, reject, upload, reverse].map((m, i) => m.isError ? <ErrorState key={i} message={(m.error as Error).message} /> : null)}
  </li>;
}

function Links({ d, onRevoked }: { d: DocumentDetail; onRevoked: () => void }) {
  const revoke = useMutation({ mutationFn: (linkId: string) => revokeLink(d.summary.id, linkId), onSuccess: onRevoked });
  return <section className="form-surface"><h2>Customer links</h2>
    <p className="form-note">Each link opens only this document. Opened counts show when the link was used, not who used it.</p>
    <div className="admin-table-wrap"><table><thead><tr><th>Created</th><th>For</th><th>Expires</th><th>Opened</th><th></th></tr></thead><tbody>
      {d.links.map(l => <tr key={l.id}><td>{dateTime(l.createdAt)}<small>{l.createdBy}</small></td>
        <td>{l.paymentId ? "Receipt" : kindLabels[d.summary.kind]} · {l.channel.toLowerCase()}</td>
        <td>{l.revokedAt ? `Withdrawn ${dateTime(l.revokedAt)}` : dateTime(l.expiresAt)}</td>
        <td>{l.openCount ? `${l.openCount}× (last ${dateTime(l.lastOpenedAt)})` : "Not yet"}</td>
        <td>{l.active && <button className="button button-quiet" disabled={revoke.isPending} onClick={() => revoke.mutate(l.id)}>Withdraw</button>}</td></tr>)}
    </tbody></table></div>
    {revoke.isError && <ErrorState message={revoke.error.message} />}
  </section>;
}
