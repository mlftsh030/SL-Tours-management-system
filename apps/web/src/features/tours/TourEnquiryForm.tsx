import { PrivacyNote } from "../../app/PrivacyPage";
import { useMutation } from "@tanstack/react-query";
import { CheckCircle2 } from "lucide-react";
import { useState, type FormEvent } from "react";
import { WhatsAppIcon } from "../../components/WhatsAppIcon";
import { ErrorState } from "../../components/StateBlocks";
import { tourWhatsappUrl } from "../../lib/contact";
import { localDate } from "../../lib/dates";
import { useIdempotencyKey } from "../../lib/useIdempotencyKey";
import { ReferralNotice } from "../partners/ReferralNotice";
import { submitEnquiry, type EnquiryPayload } from "../enquiries/enquiryApi";
import type { TourDetail } from "../../types/api";

const empty = { name: "", email: "", whatsapp: "", date: "", passengers: "", pickup: "", requests: "",
  direction: "ARRIVAL" as "ARRIVAL" | "DEPARTURE", flightNumber: "", flightDate: "", flightTime: "", destination: "", luggage: "",
  startDate: "", endDate: "", interests: "" };

/** Enquiry form for one tour. The tour is sent automatically; airport and custom tours ask for their own details. Nothing is booked. */
export function TourEnquiryForm({ tour }: { tour: TourDetail }) {
  const [form, setForm] = useState(empty);
  const submission = useIdempotencyKey();
  const send = useMutation({ mutationFn: (payload: EnquiryPayload) => submitEnquiry(payload) });
  const set = (field: keyof typeof empty) => (e: { target: { value: string } }) => setForm(f => ({ ...f, [field]: e.target.value }));
  const kind = tour.enquiryForm;
  const today = localDate();

  const details: Array<[string, string]> = kind === "AIRPORT"
    ? [["Transfer", form.direction === "ARRIVAL" ? "Airport to accommodation" : "Accommodation to airport"], ["Flight", form.flightNumber],
       ["Flight date and time", [form.flightDate, form.flightTime].filter(Boolean).join(" ")],
       [form.direction === "ARRIVAL" ? "Destination" : "Pickup address", form.destination], ["Passengers", form.passengers], ["Luggage", form.luggage],
       ["Requests", form.requests]]
    : kind === "CUSTOM"
    ? [["Dates", [form.startDate, form.endDate].filter(Boolean).join(" to ")], ["Group size", form.passengers], ["Interests", form.interests],
       ["Pickup", form.pickup], ["Requests", form.requests]]
    : [["Preferred date", form.date], ["Group size", form.passengers], ["Pickup", form.pickup], ["Requests", form.requests]];
  const whatsapp = tourWhatsappUrl(tour.title, [["Name", form.name], ...details]);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (send.isPending) return;
    const message = form.requests.trim() || `Enquiry about ${tour.title}.`;
    const content: Omit<EnquiryPayload, "idempotencyKey"> = {
      customerName: form.name, customerEmail: form.email, customerPhone: form.whatsapp, message, enquiryType: "TOUR",
      tourSlug: tour.slug, passengers: form.passengers ? Number(form.passengers) : undefined,
      requestedDate: kind === "STANDARD" ? form.date || undefined : undefined,
      pickupLocation: kind === "AIRPORT" ? undefined : form.pickup || undefined,
      transfer: kind === "AIRPORT" ? { direction: form.direction, flightNumber: form.flightNumber || undefined, flightDate: form.flightDate || undefined,
        flightTime: form.flightTime || undefined, destination: form.destination, luggage: form.luggage || undefined } : undefined,
      custom: kind === "CUSTOM" ? { startDate: form.startDate || undefined, endDate: form.endDate || undefined, interests: form.interests || undefined } : undefined
    };
    send.mutate({ ...content, idempotencyKey: submission.keyFor(content) });
  }

  if (send.isSuccess) return (
    <div className="form-surface contact-form enquiry-confirmation" role="status">
      <CheckCircle2 aria-hidden="true" />
      <p className="eyebrow">Enquiry received</p>
      <h2>Thank you, {form.name.split(" ")[0] || "traveller"}</h2>
      <p>{send.data.message} This is an enquiry, not a confirmed booking.</p>
      <p>Your reference is <strong>{send.data.reference}</strong>.</p>
      <div className="admin-actions">
        <a className="button button-secondary" href={tourWhatsappUrl(tour.title, [["Reference", send.data.reference], ...details])} target="_blank" rel="noopener noreferrer">
          <WhatsAppIcon aria-hidden="true" />Also send on WhatsApp</a>
        <button className="button button-quiet" type="button" onClick={() => { setForm(empty); submission.reset(); send.reset(); }}>Send another enquiry</button>
      </div>
    </div>
  );

  return (
    <form className="form-surface contact-form" onSubmit={submit} aria-label={`Enquire about ${tour.title}`}>
      <h2>Enquire about {tour.title}</h2>
      <div className="form-grid">
        <label><span>Your name</span><input required maxLength={160} autoComplete="name" value={form.name} onChange={set("name")} /></label>
        <label><span>Email address</span><input required type="email" maxLength={180} autoComplete="email" value={form.email} onChange={set("email")} /></label>
        <label><span>WhatsApp number</span><input required type="tel" maxLength={80} autoComplete="tel" value={form.whatsapp} onChange={set("whatsapp")} placeholder="+27…" /></label>
        <label><span>{kind === "AIRPORT" ? "Passengers" : "Group size"}</span>
          <input required type="number" min={1} max={60} value={form.passengers} onChange={set("passengers")} /></label>
        {kind === "STANDARD" && <>
          <label><span>Preferred date</span><input required type="date" min={today} value={form.date} onChange={set("date")} /></label>
          <label><span>Pickup location</span><input maxLength={240} value={form.pickup} onChange={set("pickup")} placeholder={tour.pickupSummary ?? "Hotel or address"} /></label>
        </>}
        {kind === "AIRPORT" && <>
          <label><span>Direction</span><select value={form.direction} onChange={set("direction")}>
            <option value="ARRIVAL">Arrival: airport to accommodation</option><option value="DEPARTURE">Departure: accommodation to airport</option></select></label>
          <label><span>Flight number</span><input required maxLength={20} value={form.flightNumber} onChange={set("flightNumber")} placeholder="e.g. BA59" /></label>
          <label><span>Flight date</span><input required type="date" min={today} value={form.flightDate} onChange={set("flightDate")} /></label>
          <label><span>{form.direction === "ARRIVAL" ? "Landing time" : "Departure time"}</span><input required type="time" value={form.flightTime} onChange={set("flightTime")} /></label>
          <label className="full-field"><span>{form.direction === "ARRIVAL" ? "Destination (accommodation address)" : "Pickup address"}</span>
            <input required maxLength={240} value={form.destination} onChange={set("destination")} /></label>
          <label className="full-field"><span>Luggage</span><input required maxLength={240} value={form.luggage} onChange={set("luggage")} placeholder="e.g. 2 large suitcases, 1 golf bag" /></label>
        </>}
        {kind === "CUSTOM" && <>
          <label><span>Start date</span><input required type="date" min={today} value={form.startDate} onChange={set("startDate")} /></label>
          <label><span>End date <em className="optional">(optional)</em></span><input type="date" min={form.startDate || today} value={form.endDate} onChange={set("endDate")} /></label>
          <label className="full-field"><span>Interests and places you would like to see</span><textarea required rows={3} maxLength={600} value={form.interests} onChange={set("interests")}
            placeholder="e.g. Cape Point, wine farms, a safari day" /></label>
          <label className="full-field"><span>Pickup location</span><input maxLength={240} value={form.pickup} onChange={set("pickup")} placeholder="Hotel or address" /></label>
        </>}
        <label className="full-field"><span>Special requests <em className="optional">(optional)</em></span><textarea rows={4} maxLength={1200} value={form.requests} onChange={set("requests")}
          placeholder={kind === "AIRPORT" ? "Child seats, extra stops, accessibility needs" : "Children, dietary needs, accessibility, anything we should know"} /></label>
      </div>
      {send.isError && <ErrorState message={`We could not send your enquiry (${send.error.message}). Your details are still here; please try again or use WhatsApp.`} />}
      <ReferralNotice />
      <div className="admin-actions">
        <PrivacyNote />
        <button className="button button-primary" type="submit" disabled={send.isPending}>{send.isPending ? "Sending…" : "Send Enquiry"}</button>
        <a className="button button-secondary" href={whatsapp} target="_blank" rel="noopener noreferrer"><WhatsAppIcon aria-hidden="true" />Send on WhatsApp</a>
      </div>
      <p className="form-note">This sends an enquiry. SL Tours confirms availability, final inclusions and payment details before anything is booked.</p>
    </form>
  );
}
