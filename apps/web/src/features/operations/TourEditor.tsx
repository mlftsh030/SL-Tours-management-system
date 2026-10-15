import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { saveAdminTour } from "./operationsApi";
import { ErrorState } from "../../components/StateBlocks";
import type { AdminTour, SaveTour, TourCategory, TourStatus, PricingType } from "../../types/api";
const categories: TourCategory[] = ["PENINSULA", "WINELANDS", "CITY", "CULTURE", "GARDEN_ROUTE", "TRANSFER", "SAFARI", "PRIVATE", "NATURE"];
const collections = ["locations", "tags", "highlights", "included", "excluded", "itinerary", "faqs"] as const;
export function TourEditor({ tour, onSaved, onCancel }: { tour?: AdminTour; onSaved: () => Promise<void>; onCancel: () => void }) {
  const detail = tour?.detail;
  const [localError, setLocalError] = useState("");
  const save = useMutation({ mutationFn: (payload: SaveTour) => saveAdminTour(payload, tour?.id), onSuccess: onSaved });
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setLocalError("");
    const form = new FormData(event.currentTarget);
    const text = (name: string) => String(form.get(name) ?? "").trim();
    const minimumGuests = Number(text("minimumGuests")); const maximumGuests = Number(text("maximumGuests"));
    if (maximumGuests < minimumGuests) { setLocalError("Maximum guests must be at least minimum guests."); return; }
    const price = text("price");
    if (!/^\d+(?:\.\d{1,2})?$/.test(price)) { setLocalError("Enter a price with up to two decimal places."); return; }
    const [rands, cents = ""] = price.split(".");
    const basePriceCents = Number(rands) * 100 + Number(cents.padEnd(2, "0"));
    const listFields = Object.fromEntries(collections.map(name => [name, text(name).split("\n").map(item => item.trim()).filter(Boolean)])) as Pick<SaveTour, typeof collections[number]>;
    save.mutate({ ...listFields, version: tour?.version ?? 0, slug: text("slug"), title: text("title"), shortDescription: text("shortDescription"),
      fullDescription: text("fullDescription"), duration: text("duration"), basePriceCents, pricingType: text("pricingType") as PricingType,
      minimumGuests, maximumGuests, category: text("category") as TourCategory, status: text("status") as TourStatus,
      featured: form.has("featured"), pickupAvailable: form.has("pickupAvailable"), heroImage: text("heroImage"), bannerImage: text("bannerImage") || null,
      seoTitle: text("seoTitle") || text("title"), seoDescription: text("seoDescription") || text("shortDescription"), cardNote: text("cardNote") || null,
      displayOrder: Number(text("displayOrder")), extras: tour?.extras ?? null
    });
  }
  return <form className="form-surface admin-editor" onSubmit={submit}>
    <div className="admin-heading"><h2>{tour ? `Edit ${detail?.title}` : "Add a new tour"}</h2><button type="button" className="button button-secondary" onClick={onCancel} disabled={save.isPending}>Close editor</button></div>
    {tour?.extras?.reviewNotes && <aside className="form-note" role="note"><strong>Open questions for the operator (not shown publicly)</strong>
      <p>{tour.extras.reviewNotes}</p>{tour.extras.sourceUrl && <a href={tour.extras.sourceUrl} target="_blank" rel="noopener noreferrer">Published page</a>}</aside>}
    <div className="form-grid">
      <label>Tour name<input name="title" required maxLength={180} defaultValue={detail?.title} onBlur={event => {
        const slug = event.currentTarget.form?.elements.namedItem("slug") as HTMLInputElement;
        if (!tour && slug && !slug.value) slug.value = event.currentTarget.value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
      }} /></label>
      <label>URL slug<input name="slug" required maxLength={160} pattern="[a-z0-9]+(-[a-z0-9]+)*" defaultValue={detail?.slug} placeholder="cape-town-city-tour" /></label>
      <label className="full-field">Short description<textarea name="shortDescription" required maxLength={500} rows={2} defaultValue={detail?.shortDescription} /></label>
      <label className="full-field">Full description<textarea name="fullDescription" required maxLength={2500} rows={5} defaultValue={detail?.fullDescription} /></label>
      <label>Duration<input name="duration" required maxLength={80} defaultValue={detail?.duration} placeholder="Full day" /></label>
      <label>Category<select name="category" defaultValue={detail?.category ?? "CITY"}>{categories.map(category => <option key={category} value={category}>{category.replace(/_/g, " ")}</option>)}</select></label>
      <label>Price in ZAR<input name="price" type="number" required min="0" max="300000" step="0.01" defaultValue={((detail?.basePriceCents ?? 0) / 100).toFixed(2)} /></label>
      <label>Pricing<select name="pricingType" defaultValue={detail?.pricingType ?? "PER_PERSON"}><option value="PER_PERSON">Per person</option><option value="PER_TRIP">Per trip</option><option value="CUSTOM">Custom quote</option></select></label>
      <label>Minimum guests<input name="minimumGuests" type="number" required min={1} max={60} defaultValue={detail?.minimumGuests ?? 1} /></label>
      <label>Maximum guests<input name="maximumGuests" type="number" required min={1} max={60} defaultValue={detail?.maximumGuests ?? 18} /></label>
      <label>Visibility<select name="status" defaultValue={tour?.status ?? "DRAFT"}><option value="DRAFT">Draft — staff only</option><option value="ACTIVE">Active — public and bookable</option><option value="ARCHIVED">Archived — hidden from public</option></select></label>
      <label>Display order<input name="displayOrder" type="number" required min={0} max={100000} defaultValue={tour?.displayOrder ?? 100} /></label>
      <label>Card image path<input name="heroImage" required maxLength={220} defaultValue={detail?.heroImage ?? "/assets/cape-point-tour.jpg"} placeholder="/assets/cape-point-tour.jpg" /></label>
      <label>Banner image path<input name="bannerImage" maxLength={220} defaultValue={detail?.bannerImage ?? ""} placeholder="Optional /assets/ image path" /></label>
      <p className="form-note full-field">Use existing PNG, JPEG, WebP or AVIF images under /assets/. Uploads are not enabled.</p>
      <label className="admin-check"><input type="checkbox" name="featured" defaultChecked={detail?.featured ?? false} />Featured on the homepage</label>
      <label className="admin-check"><input type="checkbox" name="pickupAvailable" defaultChecked={detail?.pickupAvailable ?? true} />Pickup available</label>
      <label className="full-field">Card note<input name="cardNote" maxLength={160} defaultValue={tour?.cardNote ?? ""} /></label>
      {collections.map(name => <label className="full-field" key={name}>{name.charAt(0).toUpperCase() + name.slice(1)} <small>One item per line, up to 30 items</small><textarea name={name} rows={3} defaultValue={detail?.[name]?.join("\n") ?? ""} /></label>)}
      <label>SEO title<input name="seoTitle" maxLength={220} defaultValue={detail?.seoTitle} placeholder="Defaults to tour name" /></label>
      <label>SEO description<textarea name="seoDescription" maxLength={500} rows={2} defaultValue={detail?.seoDescription} placeholder="Defaults to short description" /></label>
    </div>
    <p className="form-note">Prices apply to new booking requests. Existing booking totals remain unchanged.</p>
    {(localError || save.isError) && <ErrorState message={localError || save.error?.message || "Unable to save tour"} />}
    <div className="admin-actions"><button className="button button-primary" type="submit" disabled={save.isPending}>{save.isPending ? "Saving…" : "Save tour"}</button><button className="button button-secondary" type="button" onClick={onCancel} disabled={save.isPending}>Cancel</button></div>
  </form>;
}
