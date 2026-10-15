import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Plus, Trash2 } from "lucide-react";
import { listTours } from "../../lib/api";
import { newIdempotencyKey } from "../../lib/uuid";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { createDocument, getBillingSettings, getDocument, prefillFromBooking, prefillFromEnquiry, previewDocument, searchCustomers, updateDocument } from "./billingApi";
import type { DocumentDetail, DocumentKind, PreviewResult } from "./billingTypes";
import { DocumentPreview } from "./DocumentPreview";
import { WorkflowSteps } from "./WorkflowSteps";
import { clearLocalDraft, emptyForm, emptyLeg, emptyLine, fromContent, localDraftKey, readLocalDraft, toContent, writeLocalDraft, type EditorForm, type LineForm } from "./editorForm";
import { formatMoney, kindLabels } from "./money";

/** New or existing draft. Saving keeps it a draft; issuing happens from the document page. */
export function DocumentEditor({ id }: { id?: string }) {
  const existing = useQuery({ queryKey: ["billing", "document", id], queryFn: () => getDocument(id!), enabled: !!id, retry: false });
  if (id && existing.isLoading) return <LoadingState label="Loading draft" />;
  if (id && existing.isError) return <ErrorState message={existing.error.message} />;
  if (existing.data && existing.data.summary.status !== "DRAFT")
    return <div className="form-surface"><p>This document has been issued and can no longer be edited. Use <strong>Revise</strong> on the document to correct it.</p>
      <Link className="button button-secondary" to={`/operations/billing/${id}`}>Open the document</Link></div>;
  return <Editor key={id ?? "new"} doc={existing.data} />;
}

function Editor({ doc }: { doc?: DocumentDetail }) {
  const [params] = useSearchParams();
  const kind: DocumentKind = doc?.summary.kind ?? (params.get("kind") === "QUOTATION" ? "QUOTATION" : "INVOICE");
  const bookingParam = params.get("booking") ?? "";
  // Opened from an enquiry: the new document answers it (kept with the document and its booking).
  const enquiryParam = doc ? "" : params.get("enquiry") ?? "";
  const storageKey = localDraftKey(doc?.summary.id, kind, bookingParam || (enquiryParam && `enquiry-${enquiryParam}`));
  const navigate = useNavigate();
  const client = useQueryClient();
  const settings = useQuery({ queryKey: ["billing", "settings"], queryFn: getBillingSettings, retry: false });
  const tours = useQuery({ queryKey: ["tours"], queryFn: () => listTours() });
  const [idempotencyKey] = useState(newIdempotencyKey);
  const [restored, setRestored] = useState(false);
  const [form, setForm] = useState<EditorForm>(() => {
    const server = doc ? fromContent(doc.content, doc.internalNotes) : null;
    const local = readLocalDraft(storageKey);
    if (local && (!doc || local.savedAt > doc.summary.updatedAt)) return local.form;
    return server ?? { ...emptyForm(), bookingReference: bookingParam };
  });
  useEffect(() => { if (readLocalDraft(storageKey) && (!doc || readLocalDraft(storageKey)!.savedAt > doc.summary.updatedAt)) setRestored(true); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const dirty = useRef(false);
  const update = (change: (f: EditorForm) => EditorForm) => { dirty.current = true; setForm(change); };

  // Keep unsaved work in this browser.
  useEffect(() => { if (dirty.current) writeLocalDraft(storageKey, form); }, [form, storageKey]);

  // Prefill from a booking when opened from Bookings.
  const [prefillNotes, setPrefillNotes] = useState<string[]>([]);
  const prefill = useMutation({ mutationFn: (reference: string) => prefillFromBooking(reference), onSuccess: p => {
    dirty.current = true;
    setForm(f => ({ ...fromContent(p.content, f.internalNotes), template: f.template }));
    setPrefillNotes(p.notes);
  } });
  useEffect(() => { if (!doc && bookingParam && !readLocalDraft(storageKey)) prefill.mutate(bookingParam); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const enquiryPrefill = useMutation({ mutationFn: (reference: string) => prefillFromEnquiry(reference), onSuccess: p => {
    dirty.current = true;
    setForm(f => ({ ...fromContent(p.content, f.internalNotes), template: f.template }));
    setPrefillNotes(p.notes);
  } });
  useEffect(() => { if (enquiryParam && !bookingParam && !readLocalDraft(storageKey)) enquiryPrefill.mutate(enquiryParam); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Live preview from the server, so totals and layout match the PDF exactly.
  const [preview, setPreview] = useState<PreviewResult | null>(null);
  const [previewError, setPreviewError] = useState("");
  const [previewPending, setPreviewPending] = useState(false);
  useEffect(() => {
    if (!form.customer.name.trim()) { setPreviewError("Enter the customer to see the preview."); return; }
    let cancelled = false;
    setPreviewPending(true);
    const timer = setTimeout(async () => {
      try {
        const result = await previewDocument(kind, toContent(form), doc?.summary.id);
        if (!cancelled) { setPreview(result); setPreviewError(""); }
      } catch (error) { if (!cancelled) setPreviewError((error as Error).message); }
      finally { if (!cancelled) setPreviewPending(false); }
    }, 450);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [form, kind, doc?.summary.id]);

  const save = useMutation({
    mutationFn: async () => {
      const content = toContent(form);
      const booking = form.bookingReference.trim() || null;
      const notes = form.internalNotes.trim() || null;
      return doc ? updateDocument(doc.summary.id, doc.version, content, booking, notes)
        : createDocument(kind, idempotencyKey, content, booking, notes, enquiryParam || null);
    },
    onSuccess: async saved => {
      clearLocalDraft(storageKey);
      dirty.current = false;
      client.setQueryData(["billing", "document", saved.summary.id], saved);
      await client.invalidateQueries({ queryKey: ["billing", "documents"] });
      navigate(`/operations/billing/${saved.summary.id}`);
    }
  });
  function submit(event: FormEvent) { event.preventDefault(); if (!save.isPending) save.mutate(); }

  const taxAvailable = settings.data?.taxEnabled ?? false;
  const title = doc ? `Edit ${kindLabels[kind].toLowerCase()} draft` : `New ${kindLabels[kind].toLowerCase()}`;
  return <section className="billing-editor-page">
    <div className="admin-heading"><div><p className="eyebrow">{kindLabels[kind]}s</p><h1>{title}</h1>
      <p>Drafts can be changed freely. The official number is given when an administrator issues the document.</p></div>
      <div className="admin-actions"><Link className="button button-secondary" to={doc ? `/operations/billing/${doc.summary.id}` : "/operations/billing"}>Cancel</Link></div></div>
    <WorkflowSteps current={2} kind={kind} />
    {restored && <div className="admin-callout" role="status">Unsaved changes from this browser were restored.
      <button type="button" className="button button-quiet" onClick={() => { clearLocalDraft(storageKey); setRestored(false);
        setForm(doc ? fromContent(doc.content, doc.internalNotes) : { ...emptyForm(), bookingReference: bookingParam }); }}>Discard them</button></div>}
    <div className="billing-editor">
      <form className="billing-form" onSubmit={submit} noValidate>
        <fieldset className="form-surface billing-step">
          <legend>1. Customer and booking</legend>
          <div className="form-grid admin-form-grid">
            <CustomerField value={form.customer.name} onChange={name => update(f => ({ ...f, customer: { ...f.customer, name } }))}
              onPick={s => update(f => ({ ...f, customer: { name: s.name, contactPerson: s.contactPerson ?? f.customer.contactPerson, email: s.email ?? "", whatsapp: s.whatsapp ?? "" } }))} />
            <label>Contact person<input value={form.customer.contactPerson} maxLength={160} onChange={e => { const v = e.target.value; update(f => ({ ...f, customer: { ...f.customer, contactPerson: v } })); }} /></label>
            <label>Email<input type="email" value={form.customer.email} maxLength={180} onChange={e => { const v = e.target.value; update(f => ({ ...f, customer: { ...f.customer, email: v } })); }} /></label>
            <label>WhatsApp number<input type="tel" value={form.customer.whatsapp} maxLength={40} placeholder="+27 82 123 4567" onChange={e => { const v = e.target.value; update(f => ({ ...f, customer: { ...f.customer, whatsapp: v } })); }} /></label>
            <label>Booking reference <small>Optional</small>
              <span className="billing-inline"><input value={form.bookingReference} maxLength={40} placeholder="SLT-2026-…" onChange={e => { const v = e.target.value; update(f => ({ ...f, bookingReference: v })); }} />
                <button type="button" className="button button-secondary" disabled={!form.bookingReference.trim() || prefill.isPending} onClick={() => prefill.mutate(form.bookingReference.trim())}>
                  {prefill.isPending ? "Loading…" : "Fill from booking"}</button></span></label>
          </div>
          {enquiryParam && <p className="form-note">This {kindLabels[kind].toLowerCase()} answers enquiry <strong>{enquiryParam}</strong>. When the
            quotation is accepted and becomes a booking, the enquiry is marked as booked.</p>}
          {prefill.isError && <ErrorState message={prefill.error.message} />}
          {enquiryPrefill.isError && <ErrorState message={enquiryPrefill.error.message} />}
          {prefillNotes.map(n => <p key={n} className="form-note">{n}</p>)}
        </fieldset>

        <fieldset className="form-surface billing-step">
          <legend>2. Services and pricing</legend>
          {form.lines.map((line, index) => <LineEditor key={line.key} line={line} index={index} count={form.lines.length} tours={tours.data ?? []}
            onChange={next => update(f => ({ ...f, lines: f.lines.map(l => l.key === line.key ? next : l) }))}
            onRemove={() => update(f => ({ ...f, lines: f.lines.filter(l => l.key !== line.key) }))}
            onMove={delta => update(f => { const ls = [...f.lines]; const [l] = ls.splice(index, 1); ls.splice(index + delta, 0, l); return { ...f, lines: ls }; })} />)}
          <div className="admin-actions">
            <button type="button" className="button button-secondary" onClick={() => update(f => ({ ...f, lines: [...f.lines, emptyLine()] }))}><Plus aria-hidden="true" />Add service</button>
            <button type="button" className="button button-quiet" onClick={() => update(f => ({ ...f, lines: [...f.lines, emptyLine("ADJUSTMENT")] }))}><Plus aria-hidden="true" />Add deduction (e.g. deposit)</button>
          </div>
          <div className="form-grid admin-form-grid billing-adjustments">
            <label>Discount<select value={form.discountType} onChange={e => { const v = e.target.value as EditorForm["discountType"]; update(f => ({ ...f, discountType: v })); }}>
              <option value="NONE">No discount</option><option value="AMOUNT">Amount in rand</option><option value="PERCENT">Percentage</option></select></label>
            {form.discountType !== "NONE" && <>
              <label>{form.discountType === "AMOUNT" ? "Discount (R)" : "Discount (%)"}<input inputMode="decimal" value={form.discountValue} onChange={e => { const v = e.target.value; update(f => ({ ...f, discountValue: v })); }} /></label>
              <label>Discount wording<input value={form.discountLabel} maxLength={80} placeholder="Discount" onChange={e => { const v = e.target.value; update(f => ({ ...f, discountLabel: v })); }} /></label>
            </>}
            <label className="admin-check"><input type="checkbox" checked={form.applyTax} disabled={!taxAvailable && !form.applyTax} onChange={e => { const v = e.target.checked; update(f => ({ ...f, applyTax: v })); }} />
              {taxAvailable ? `Add ${settings.data?.taxLabel} at ${(settings.data!.taxRateBasisPoints ?? 0) / 100}%` : "No tax is added. An administrator can switch tax on in billing settings if SL Tours is registered for VAT."}</label>
          </div>
        </fieldset>

        <fieldset className="form-surface billing-step">
          <legend>Dates and trip details</legend>
          <div className="form-grid admin-form-grid">
            <label>Issue date <small>Blank = the day it is issued</small><input type="date" value={form.issueDate} onChange={e => { const v = e.target.value; update(f => ({ ...f, issueDate: v })); }} /></label>
            <label>{kind === "INVOICE" ? "Due date" : "Valid until"} <small>Optional</small><input type="date" value={form.dueDate} onChange={e => { const v = e.target.value; update(f => ({ ...f, dueDate: v })); }} /></label>
            <label>Service date<input type="date" value={form.serviceStart} onChange={e => { const v = e.target.value; update(f => ({ ...f, serviceStart: v })); }} /></label>
            <label>Service end date <small>For several days</small><input type="date" value={form.serviceEnd} min={form.serviceStart || undefined} onChange={e => { const v = e.target.value; update(f => ({ ...f, serviceEnd: v })); }} /></label>
            <label>Passengers<input type="number" min={1} max={1000} value={form.passengers} onChange={e => { const v = e.target.value; update(f => ({ ...f, passengers: v })); }} /></label>
            <label>Vehicle<input value={form.vehicle} maxLength={160} placeholder="e.g. Toyota Quantum" onChange={e => { const v = e.target.value; update(f => ({ ...f, vehicle: v })); }} /></label>
            <label className="full-field">Itinerary notes <small>Printed under NOTES</small><textarea rows={2} maxLength={2000} value={form.itineraryNotes} onChange={e => { const v = e.target.value; update(f => ({ ...f, itineraryNotes: v })); }} /></label>
          </div>
        </fieldset>

        <fieldset className="form-surface billing-step">
          <legend>Notes and terms</legend>
          <div className="form-grid admin-form-grid">
            <label className="full-field">Note to the customer <small>Printed on the document</small><textarea rows={2} maxLength={2000} value={form.customerNotes} onChange={e => { const v = e.target.value; update(f => ({ ...f, customerNotes: v })); }} /></label>
            <label className="admin-check full-field"><input type="checkbox" checked={form.customTerms} onChange={e => { const v = e.target.checked;
              update(f => ({ ...f, customTerms: v, terms: v && !f.terms ? (kind === "INVOICE" ? settings.data?.invoiceTerms : settings.data?.quotationTerms) ?? "" : f.terms })); }} />
              Use different terms for this document</label>
            {form.customTerms && <label className="full-field">Terms <small>One per line</small><textarea rows={3} value={form.terms} onChange={e => { const v = e.target.value; update(f => ({ ...f, terms: v })); }} /></label>}
            <label>Layout<select value={form.template} onChange={e => { const v = e.target.value as EditorForm["template"]; update(f => ({ ...f, template: v })); }}>
              <option value="STANDARD">Standard (as the SL Tours template)</option><option value="DETAILED">Detailed (quantity and unit price on every line)</option></select></label>
            <label className="full-field billing-internal">Internal notes <small>Staff only. Never printed or shared.</small><textarea rows={2} maxLength={4000} value={form.internalNotes} onChange={e => { const v = e.target.value; update(f => ({ ...f, internalNotes: v })); }} /></label>
          </div>
        </fieldset>

        {save.isError && <ErrorState message={save.error.message} />}
        <div className="billing-savebar">
          <span>{preview ? <>Total <strong>{formatMoney(preview.totals.total, form.currency)}</strong></> : "Total appears once the details are valid"}</span>
          <button className="button button-primary" type="submit" disabled={save.isPending}>{save.isPending ? "Saving…" : "Save draft and continue"}</button>
        </div>
      </form>

      <aside className="billing-preview" aria-label="Live preview">
        <div className="billing-preview-head"><h2>3. Preview</h2><span aria-live="polite">{previewPending ? "Updating…" : previewError ? "Not updated" : "Up to date"}</span></div>
        {previewError && <p className="admin-warning" role="alert">{previewError}</p>}
        {preview?.warnings.map(w => <p key={w} className="form-note">{w}</p>)}
        {preview ? <DocumentPreview view={preview.view} stale={!!previewError} /> : !previewError && <LoadingState label="Preparing preview" />}
      </aside>
    </div>
  </section>;
}

function CustomerField({ value, onChange, onPick }: { value: string; onChange: (v: string) => void; onPick: (s: { name: string; contactPerson: string | null; email: string | null; whatsapp: string | null }) => void }) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  useEffect(() => { const t = setTimeout(() => setQuery(value.trim()), 300); return () => clearTimeout(t); }, [value]);
  const suggestions = useQuery({ queryKey: ["billing", "customers", query], queryFn: () => searchCustomers(query), enabled: open && query.length >= 2 });
  return <label className="billing-customer">Customer or organisation
    <input required maxLength={200} value={value} autoComplete="off" aria-autocomplete="list" aria-controls="billing-customer-list"
      onFocus={() => setOpen(true)} onBlur={() => setTimeout(() => setOpen(false), 150)} onChange={e => { onChange(e.target.value); setOpen(true); }} />
    {open && !!suggestions.data?.length && <ul id="billing-customer-list" className="billing-suggestions" role="listbox">
      {suggestions.data.map((s, i) => <li key={i} role="option" aria-selected="false"><button type="button" onMouseDown={e => e.preventDefault()} onClick={() => { onPick(s); setOpen(false); }}>
        <strong>{s.name}</strong><small>{[s.email, s.whatsapp, s.source + (s.bookingReference ? ` ${s.bookingReference}` : "")].filter(Boolean).join(" · ")}</small></button></li>)}
    </ul>}
  </label>;
}

function LineEditor({ line, index, count, tours, onChange, onRemove, onMove }: {
  line: LineForm; index: number; count: number; tours: Array<{ slug: string; title: string; basePriceCents: number; pricingType: string }>;
  onChange: (l: LineForm) => void; onRemove: () => void; onMove: (delta: number) => void;
}) {
  const set = (patch: Partial<LineForm>) => onChange({ ...line, ...patch });
  const tour = useMemo(() => tours.find(t => t.slug === line.tourSlug), [tours, line.tourSlug]);
  const differs = tour && line.price.trim() !== "" && Math.round(Number(line.price.replace(",", ".")) * 100) !== tour.basePriceCents;
  const adjustment = line.kind === "ADJUSTMENT";
  return <div className={`billing-line${adjustment ? " is-adjustment" : ""}`}>
    <div className="billing-line-head"><h3>{adjustment ? `Deduction ${index + 1}` : `Service ${index + 1}`}</h3>
      <div className="admin-row-actions">
        <button type="button" className="button button-quiet" disabled={index === 0} onClick={() => onMove(-1)} aria-label={`Move service ${index + 1} up`}>↑</button>
        <button type="button" className="button button-quiet" disabled={index === count - 1} onClick={() => onMove(1)} aria-label={`Move service ${index + 1} down`}>↓</button>
        <button type="button" className="button button-quiet" disabled={count === 1} onClick={onRemove} aria-label={`Remove service ${index + 1}`}><Trash2 aria-hidden="true" /></button>
      </div></div>
    <div className="form-grid admin-form-grid">
      {!adjustment && <label>From the tour catalogue <small>Optional</small><select value={line.tourSlug} onChange={e => {
        const t = tours.find(x => x.slug === e.target.value);
        set(t ? { tourSlug: t.slug, title: line.title || t.title, price: String(t.basePriceCents / 100), unit: t.pricingType === "PER_PERSON" ? "person" : t.pricingType === "PER_TRIP" ? "trip" : line.unit, overrideReason: "" } : { tourSlug: "" });
      }}><option value="">Not from the catalogue</option>{tours.map(t => <option key={t.slug} value={t.slug}>{t.title}</option>)}</select></label>}
      <label className={adjustment ? "full-field" : undefined}>{adjustment ? "Wording" : "Service title"} <small>{adjustment ? "e.g. Less: 50% deposit" : "Printed in bold, e.g. Transfer"}</small>
        <input value={line.title} maxLength={160} onChange={e => set({ title: e.target.value })} /></label>
    </div>
    {!adjustment && <div className="billing-legs">
      {line.legs.map((leg, li) => <div className="billing-leg" key={li}>
        <label>Pick up {line.legs.length > 1 ? li + 1 : ""}<input value={leg.pickup} maxLength={240} onChange={e => set({ legs: line.legs.map((g, i) => i === li ? { ...g, pickup: e.target.value } : g) })} /></label>
        <label>Drop off <small>One place per line; several become a list</small><textarea rows={Math.min(6, Math.max(2, leg.dropOffs.split("\n").length))} value={leg.dropOffs}
          onChange={e => set({ legs: line.legs.map((g, i) => i === li ? { ...g, dropOffs: e.target.value } : g) })} /></label>
        {line.legs.length > 1 && <button type="button" className="button button-quiet" onClick={() => set({ legs: line.legs.filter((_, i) => i !== li) })}>Remove this leg</button>}
      </div>)}
      <button type="button" className="button button-quiet" onClick={() => set({ legs: [...line.legs, emptyLeg()] })}><Plus aria-hidden="true" />Add transfer leg</button>
    </div>}
    <div className="form-grid admin-form-grid">
      {!adjustment && <label className="full-field">More details <small>Each line prints on its own line; start with "- " for a bullet</small>
        <textarea rows={2} maxLength={3000} value={line.details} onChange={e => set({ details: e.target.value })} /></label>}
      {!adjustment && <label>Quantity<input inputMode="decimal" value={line.quantity} onChange={e => set({ quantity: e.target.value })} /></label>}
      {!adjustment && <label>Unit <small>e.g. person, trip, vehicle</small><input value={line.unit} maxLength={30} onChange={e => set({ unit: e.target.value })} /></label>}
      <label>{adjustment ? "Amount to deduct (R)" : "Price per unit (R)"}<input inputMode="decimal" value={adjustment ? line.price.replace("-", "") : line.price}
        onChange={e => set({ price: adjustment ? (e.target.value ? `-${e.target.value.replace("-", "")}` : "") : e.target.value })} /></label>
      {differs && <label className="full-field billing-override">The catalogue price is {formatMoney(tour!.basePriceCents)}. Reason for the agreed price
        <input required value={line.overrideReason} maxLength={300} placeholder="e.g. Group rate agreed by phone" onChange={e => set({ overrideReason: e.target.value })} /></label>}
    </div>
  </div>;
}
