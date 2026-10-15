import { useEffect, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { dateTime } from "../operations/labels";
import { getBillingSettings, saveBillingSettings, saveNumbering, uploadLogo, restoreOriginalLogo } from "./billingApi";
import type { BillingSettings, Sequence } from "./billingTypes";

type Editable = Omit<BillingSettings, "logoUrl" | "customLogo" | "numbering" | "updatedBy" | "updatedAt">;
const seriesLabels: Record<string, string> = { INVOICE: "Invoices", QUOTATION: "Quotations", RECEIPT: "Receipts" };

/** Company, banking, terms, tax and numbering for new documents. Issued documents keep the details they were issued with. */
export function BillingSettingsPanel({ isAdmin }: { isAdmin: boolean }) {
  const client = useQueryClient();
  const data = useQuery({ queryKey: ["billing", "settings"], queryFn: getBillingSettings, retry: false });
  const [form, setForm] = useState<Editable | null>(null);
  const [taxPercent, setTaxPercent] = useState("");
  useEffect(() => { if (data.data) { const { logoUrl: _l, customLogo: _c, numbering: _n, updatedBy: _u, updatedAt: _a, ...rest } = data.data; setForm(rest);
    setTaxPercent(rest.taxRateBasisPoints == null ? "" : String(rest.taxRateBasisPoints / 100)); } }, [data.data]);
  const done = (next: BillingSettings) => client.setQueryData(["billing", "settings"], next);
  const save = useMutation({ mutationFn: (body: Editable) => saveBillingSettings(body), onSuccess: done });
  const logo = useMutation({ mutationFn: (file: File) => uploadLogo(file), onSuccess: done });
  const original = useMutation({ mutationFn: restoreOriginalLogo, onSuccess: done });
  if (data.isLoading || !form) return data.isError ? <ErrorState message={data.error.message} /> : <LoadingState label="Loading billing settings" />;
  const s = data.data!;
  const set = <K extends keyof Editable>(key: K, value: Editable[K]) => setForm(f => f && ({ ...f, [key]: value }));
  const field = (key: keyof Editable, label: string, extra: { hint?: string; type?: string; full?: boolean; max?: number } = {}) =>
    <label className={extra.full ? "full-field" : undefined}>{label}{extra.hint && <small>{extra.hint}</small>}
      <input type={extra.type ?? "text"} disabled={!isAdmin} maxLength={extra.max} value={(form[key] as string | null) ?? ""} onChange={e => set(key, e.target.value as never)} /></label>;
  function submit(e: FormEvent) {
    e.preventDefault();
    const rate = taxPercent.trim() ? Math.round(Number(taxPercent.replace(",", ".")) * 100) : null;
    save.mutate({ ...form!, taxRateBasisPoints: rate });
  }
  return <section>
    <div className="admin-heading"><div><p className="eyebrow">Billing</p><h1>Billing settings</h1>
      <p>Used for new quotations, invoices and receipts. Changing them never alters documents already issued.</p></div></div>
    {!isAdmin && <p className="admin-callout">Only an administrator can change these settings.</p>}
    <Numbering sequences={s.numbering} isAdmin={isAdmin} onSaved={done} />
    <form className="form-surface billing-settings" onSubmit={submit}>
      <h2>Company</h2>
      <div className="billing-logo"><img src={s.logoUrl} alt="Logo printed on documents" />
        {isAdmin && <div><label>Replace logo <small>PNG or JPEG, at least 100 × 100 pixels</small><input type="file" accept="image/png,image/jpeg" onChange={e => e.target.files?.[0] && logo.mutate(e.target.files[0])} /></label>
          {s.customLogo && <button type="button" className="button button-quiet" onClick={() => original.mutate()}>Use the original SL Tours logo</button>}</div>}</div>
      {(logo.isError || original.isError) && <ErrorState message={((logo.error ?? original.error) as Error).message} />}
      <div className="form-grid admin-form-grid">
        {field("companyName", "Company name", { max: 160 })}
        {field("tagline", "Tagline", { full: true, max: 240 })}
        {field("phone", "Mobile", { max: 60 })}{field("whatsapp", "WhatsApp", { max: 60 })}
        {field("email", "Email", { type: "email", max: 180 })}{field("website", "Website", { hint: "Optional", max: 180 })}
        <label className="full-field">Address as printed<textarea rows={2} disabled={!isAdmin} maxLength={400} value={form.address} onChange={e => set("address", e.target.value)} /></label>
        <label className="admin-check full-field"><input type="checkbox" disabled={!isAdmin} checked={!form.addressNeedsReview} onChange={e => set("addressNeedsReview", !e.target.checked)} />
          I have checked the address spelling</label>
        {form.addressNeedsReview && <p className="admin-warning full-field">The address comes from the existing invoice and reads "Example Street". Please confirm or correct the spelling.</p>}
      </div>
      <h2>Banking details</h2>
      <p className="form-note">Changes are recorded in the audit log with the account number masked. Leave fields you do not know empty; nothing is filled in for you.</p>
      <div className="form-grid admin-form-grid">
        {field("bankAccountName", "Account name", { max: 160 })}{field("bankName", "Bank", { max: 120 })}
        {field("bankAccountNumber", "Account number", { max: 40 })}{field("bankAccountType", "Account type", { hint: "Optional", max: 60 })}
        {field("bankBranchCode", "Branch code", { hint: "Optional", max: 20 })}{field("bankSwiftCode", "SWIFT code", { hint: "Optional", max: 20 })}
        {field("paymentInstructions", "Payment request in messages", { full: true, max: 500, hint: "Used in WhatsApp and email messages" })}
      </div>
      <h2>Terms</h2>
      <div className="form-grid admin-form-grid">
        <label className="full-field">Invoice terms <small>One per line</small><textarea rows={3} disabled={!isAdmin} value={form.invoiceTerms} onChange={e => set("invoiceTerms", e.target.value)} /></label>
        <label className="full-field">Quotation terms <small>One per line</small><textarea rows={3} disabled={!isAdmin} value={form.quotationTerms} onChange={e => set("quotationTerms", e.target.value)} /></label>
        <label>Invoice due after (days) <small>Blank = no due date</small><input type="number" min={0} max={365} disabled={!isAdmin} value={form.defaultDueDays ?? ""} onChange={e => set("defaultDueDays", e.target.value ? Number(e.target.value) : null)} /></label>
        <label>Quotations valid for (days) <small>Blank = not printed</small><input type="number" min={1} max={365} disabled={!isAdmin} value={form.quotationValidDays ?? ""} onChange={e => set("quotationValidDays", e.target.value ? Number(e.target.value) : null)} /></label>
      </div>
      <h2>Currency and tax</h2>
      <div className="form-grid admin-form-grid">
        <label>Currency<select disabled={!isAdmin} value={form.currency} onChange={e => set("currency", e.target.value)}>{["ZAR", "USD", "EUR", "GBP"].map(c => <option key={c}>{c}</option>)}</select></label>
        <label className="admin-check full-field"><input type="checkbox" disabled={!isAdmin} checked={form.taxEnabled} onChange={e => set("taxEnabled", e.target.checked)} />
          Charge tax on documents (only if SL Tours is registered for it)</label>
        {form.taxEnabled && <>{field("taxLabel", "Tax name", { hint: "e.g. VAT", max: 20 })}
          <label>Rate (%)<input inputMode="decimal" disabled={!isAdmin} value={taxPercent} onChange={e => setTaxPercent(e.target.value)} /></label>
          {field("taxNumber", "Registration number", { hint: "Only if issued to SL Tours", max: 40 })}</>}
      </div>
      <p className="form-note">Documents are titled INVOICE or QUOTATION. This system never prints "Tax Invoice".</p>
      {save.isError && <ErrorState message={save.error.message} />}
      {save.isSuccess && <p className="admin-ok" role="status">Settings saved.</p>}
      {isAdmin && <button className="button button-primary" type="submit" disabled={save.isPending}>{save.isPending ? "Saving…" : "Save settings"}</button>}
      <p className="form-note">Last changed {dateTime(s.updatedAt)} by {s.updatedBy}.</p>
    </form>
  </section>;
}

function Numbering({ sequences, isAdmin, onSaved }: { sequences: Sequence[]; isAdmin: boolean; onSaved: (s: BillingSettings) => void }) {
  return <section className="form-surface billing-numbering"><h2>Numbering</h2>
    <p className="form-note">The next number is used when a document is issued. It can never go back to a number already used, and voided numbers are not reused.
      Before the first invoice, check the last invoice number SL Tours sent outside this system (for example 100099) and confirm the next one.</p>
    <div className="billing-sequences">{sequences.map(s => <SequenceRow key={s.kind} s={s} isAdmin={isAdmin} onSaved={onSaved} />)}</div>
  </section>;
}

function SequenceRow({ s, isAdmin, onSaved }: { s: Sequence; isAdmin: boolean; onSaved: (x: BillingSettings) => void }) {
  const [value, setValue] = useState(String(s.nextNumber));
  const save = useMutation({ mutationFn: () => saveNumbering(s.kind, Number(value), true), onSuccess: onSaved });
  return <form className="admin-inline-form" onSubmit={e => { e.preventDefault(); save.mutate(); }}>
    <label>{seriesLabels[s.kind]}: next number<input type="number" min={s.highestUsed + 1} disabled={!isAdmin} value={value} onChange={e => setValue(e.target.value)} /></label>
    <span className={`admin-badge ${s.confirmed ? "status-active" : "status-draft"}`}>{s.confirmed ? "Confirmed" : "Not confirmed: issuing is blocked"}</span>
    <small>{s.highestUsed ? `Highest issued here: ${s.highestUsed}` : "None issued here yet"}</small>
    {isAdmin && <button className="button button-secondary" type="submit" disabled={save.isPending || !value}>{s.confirmed ? "Save" : "Confirm next number"}</button>}
    {save.isError && <ErrorState message={save.error.message} />}
  </form>;
}
