import { PrivacyNote } from "../../app/PrivacyPage";
import { ReferralNotice } from "../partners/ReferralNotice";
import { useMutation, useQuery } from "@tanstack/react-query";
import { CheckCircle2 } from "lucide-react";
import { useState, type FormEvent } from "react";
import { WhatsAppIcon } from "../../components/WhatsAppIcon";
import { ErrorState } from "../../components/StateBlocks";
import { listTours } from "../../lib/api";
import { contact } from "../../lib/contact";
import { useIdempotencyKey } from "../../lib/useIdempotencyKey";
import { localDate } from "../../lib/dates";
import { submitEnquiry, type EnquiryPayload } from "./enquiryApi";

const emptyForm = { name: "", email: "", phone: "", message: "", interest: "GENERAL", tourSlug: "", date: "", pickup: "", passengers: "" };

/** Contact form that stores the enquiry with SL Tours before confirming it to the customer. */
/** A link such as /contact?tour=cape-point-tour or ?topic=PRIVATE_TRIP opens the form with that selection. */
function initialForm() {
  const params = new URLSearchParams(typeof window === "undefined" ? "" : window.location.search);
  const tour = params.get("tour");
  const topic = params.get("topic");
  if (tour && /^[a-z0-9-]{1,160}$/.test(tour)) return { ...emptyForm, interest: "TOUR", tourSlug: tour };
  if (topic === "PRIVATE_TRIP" || topic === "TOUR") return { ...emptyForm, interest: topic };
  return emptyForm;
}

export function EnquiryForm() {
  const [form, setForm] = useState(initialForm);
  // An unchanged retry reuses the key, so the API stores the enquiry once; edited details are a new enquiry.
  const submission = useIdempotencyKey();
  const tours = useQuery({ queryKey: ["tours"], queryFn: () => listTours(), enabled: form.interest === "TOUR" });
  const send = useMutation({ mutationFn: (payload: EnquiryPayload) => submitEnquiry(payload) });
  const update = (field: keyof typeof form) => (event: { target: { value: string } }) =>
    setForm(current => ({ ...current, [field]: event.target.value }));

  function whatsappText() {
    return [
      "*New message from SL Tours website*",
      `Name: ${form.name}`,
      `Email: ${form.email}`,
      `Phone: ${form.phone}`,
      `Message: ${form.message}`
    ].join("\n");
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (send.isPending) return;
    const content: Omit<EnquiryPayload, "idempotencyKey"> = {
      customerName: form.name,
      customerEmail: form.email,
      customerPhone: form.phone || undefined,
      message: form.message,
      enquiryType: form.interest as EnquiryPayload["enquiryType"],
      tourSlug: form.interest === "TOUR" && form.tourSlug ? form.tourSlug : undefined,
      requestedDate: form.date || undefined,
      pickupLocation: form.pickup || undefined,
      passengers: form.passengers ? Number(form.passengers) : undefined
    };
    send.mutate({ ...content, idempotencyKey: submission.keyFor(content) });
  }

  function startAnother() {
    setForm(emptyForm);
    submission.reset();
    send.reset();
  }

  if (send.isSuccess) {
    return (
      <div className="form-surface contact-form enquiry-confirmation" role="status">
        <CheckCircle2 aria-hidden="true" />
        <p className="eyebrow">Enquiry received</p>
        <h2>Thank you, {form.name.split(" ")[0] || "traveller"}</h2>
        <p>{send.data.message}</p>
        <p>
          Your reference is <strong>{send.data.reference}</strong>. Please quote it if you contact us.
        </p>
        <div className="admin-actions">
          <a className="button button-secondary" href={`${contact.whatsapp}?text=${encodeURIComponent(`Reference ${send.data.reference}\n${whatsappText()}`)}`} target="_blank" rel="noopener noreferrer">
            <WhatsAppIcon aria-hidden="true" />
            Also message us on WhatsApp
          </a>
          <button className="button button-quiet" type="button" onClick={startAnother}>Send another enquiry</button>
        </div>
      </div>
    );
  }

  const today = localDate();
  return (
    <form className="form-surface contact-form" onSubmit={submit}>
      <h2>Leave us your info and we will get back to you.</h2>
      <div className="form-grid">
        <label>
          <span>Name</span>
          <input required maxLength={160} autoComplete="name" value={form.name} onChange={update("name")} />
        </label>
        <label>
          <span>Email</span>
          <input required type="email" maxLength={180} autoComplete="email" value={form.email} onChange={update("email")} />
        </label>
        <label className="full-field">
          <span>Phone</span>
          <input type="tel" maxLength={80} autoComplete="tel" value={form.phone} onChange={update("phone")} />
        </label>
        <label>
          <span>I'm interested in</span>
          <select value={form.interest} onChange={update("interest")}>
            <option value="GENERAL">A general question</option>
            <option value="TOUR">One of your tours</option>
            <option value="PRIVATE_TRIP">A private trip</option>
          </select>
        </label>
        {form.interest === "TOUR" ? (
          <label>
            <span>Tour</span>
            <select value={form.tourSlug} onChange={update("tourSlug")} disabled={tours.isLoading}>
              <option value="">{tours.isLoading ? "Loading tours…" : "Not sure yet"}</option>
              {tours.data?.map(tour => <option key={tour.slug} value={tour.slug}>{tour.title}</option>)}
            </select>
          </label>
        ) : (
          <label>
            <span>Preferred date <em className="optional">(optional)</em></span>
            <input type="date" min={today} value={form.date} onChange={update("date")} />
          </label>
        )}
        {form.interest === "TOUR" && (
          <label>
            <span>Preferred date <em className="optional">(optional)</em></span>
            <input type="date" min={today} value={form.date} onChange={update("date")} />
          </label>
        )}
        <label>
          <span>Passengers <em className="optional">(optional)</em></span>
          <input type="number" min={1} max={60} value={form.passengers} onChange={update("passengers")} />
        </label>
        <label className={form.interest === "TOUR" ? "full-field" : ""}>
          <span>Pickup location <em className="optional">(optional)</em></span>
          <input maxLength={240} value={form.pickup} onChange={update("pickup")} placeholder="Hotel, address or airport" />
        </label>
        <label className="full-field">
          <span>Message</span>
          <textarea required rows={5} maxLength={1600} value={form.message} onChange={update("message")} />
        </label>
      </div>
      {send.isError && (
        <div className="enquiry-error">
          <ErrorState message={`We could not send your enquiry (${send.error.message}). Your details are still here, so please try again.`} />
          <a href={`${contact.whatsapp}?text=${encodeURIComponent(whatsappText())}`} target="_blank" rel="noopener noreferrer">
            Or send it to us on WhatsApp instead
          </a>
        </div>
      )}
      <ReferralNotice />
      <PrivacyNote />
      <button className="button button-primary" type="submit" disabled={send.isPending}>
        {send.isPending ? "Sending…" : send.isError ? "Try again" : "Send enquiry"}
      </button>
      <p className="form-note">We save your enquiry securely and reply by email or phone. Prefer chatting? Use the WhatsApp button.</p>
    </form>
  );
}
