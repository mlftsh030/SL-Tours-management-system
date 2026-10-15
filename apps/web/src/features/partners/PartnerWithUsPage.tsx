import { PrivacyNote } from "../../app/PrivacyPage";
import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { BedDouble, CheckCircle2, Compass, Handshake, ShieldCheck } from "lucide-react";
import { PageBanner } from "../../components/PageBanner";
import { ErrorState } from "../../components/StateBlocks";
import { useIdempotencyKey } from "../../lib/useIdempotencyKey";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { applyAsPartner } from "./partnerApi";
import { businessTypeLabels, type BusinessType, type Route } from "./partnerTypes";

const empty = { businessName: "", businessType: "HOTEL" as BusinessType, contactName: "", email: "", phone: "", website: "", area: "", registrationNumber: "",
  licences: "", description: "", servicesOffered: "", password: "", privacyConsent: false };

export function PartnerWithUsPage() {
  useDocumentTitle("Partner with SL Tours | Referral Partners and Experience Suppliers",
    "Hotels, guesthouses, concierges and travel agents can refer guests to SL Tours. Safari operators, activity providers and guides can propose experiences.");
  const [routes, setRoutes] = useState<Route[]>([]);
  const [form, setForm] = useState(empty);
  const submission = useIdempotencyKey();
  const [error, setError] = useState("");
  const send = useMutation({ mutationFn: applyAsPartner });
  const set = (field: keyof typeof empty) => (e: { target: { value: string } }) => setForm(f => ({ ...f, [field]: e.target.value }));
  const toggle = (route: Route, on: boolean) => setRoutes(list => on ? [...new Set([...list, route])] : list.filter(r => r !== route));

  function submit(event: FormEvent) {
    event.preventDefault(); setError("");
    if (routes.length === 0) { setError("Choose at least one way to work with us"); return; }
    const content = { ...form, routes, phone: form.phone || null, website: form.website || null, area: form.area || null,
      registrationNumber: form.registrationNumber || null, licences: form.licences || null, servicesOffered: form.servicesOffered || null };
    send.mutate({ ...content, idempotencyKey: submission.keyFor(content) });
  }

  if (send.isSuccess) return <section className="section confirmation-page">
    <CheckCircle2 aria-hidden="true" />
    <p className="eyebrow">Application received</p>
    <h1>{send.data.reference}</h1>
    <p>{send.data.message}</p>
    <p>You can sign in with your email and password once your application is approved.</p>
    <Link className="button button-secondary" to="/">Back to the home page</Link>
  </section>;

  return <>
    <PageBanner title="Partner with SL Tours" subtitle="Refer your guests to us, or offer your experiences through us" image="/assets/gallery/27.webp" />
    <section className="section partner-page">
      <div className="partner-routes">
        <article className="form-surface partner-route">
          <BedDouble aria-hidden="true" />
          <h2>Referral partners</h2>
          <p>For hotels, guesthouses, accommodation hosts, concierges and travel agents who recommend SL Tours to their guests.</p>
          <ul>
            <li>Your own referral link and code.</li>
            <li>A partner area showing your referrals' booking references and status, without your guests' personal details.</li>
            <li>Commission is earned only under a written agreement with SL Tours, on completed and fully paid bookings.</li>
          </ul>
        </article>
        <article className="form-surface partner-route">
          <Compass aria-hidden="true" />
          <h2>Experience suppliers</h2>
          <p>For safari operators, activity providers and local guides who would like SL Tours to offer their experiences.</p>
          <ul>
            <li>Propose experiences from your partner area.</li>
            <li>SL Tours checks every price, schedule and age or safety rule against an official source before publishing.</li>
            <li>Payments to suppliers are agreed and recorded separately from referral commission.</li>
          </ul>
        </article>
      </div>

      <div className="partner-facts">
        <div><Handshake aria-hidden="true" /><h3>Terms</h3>
          <p>Commission rates, payment timing and supplier terms are agreed individually in writing before anything is earned or paid. Applying is free
            and does not commit you or SL Tours to anything.</p></div>
        <div><ShieldCheck aria-hidden="true" /><h3>How referrals are credited</h3>
          <p>A booking or enquiry is credited to you when the guest arrives through your link or enters your code, and only while your partnership is
            active. Which bookings earn commission, and for how long after the referral, is set in your agreement. Guests see that you referred them and
            that their price is the same.</p></div>
      </div>

      <form className="form-surface wide-form partner-form" onSubmit={submit} aria-label="Partner application">
        <h2>Apply</h2>
        <fieldset><legend>How would you like to work with us?</legend>
          <div className="choice-grid">
            <label className="choice"><input type="checkbox" checked={routes.includes("REFERRAL")} onChange={e => toggle("REFERRAL", e.target.checked)} />
              <span>Refer guests (referral partner)</span></label>
            <label className="choice"><input type="checkbox" checked={routes.includes("SUPPLIER")} onChange={e => toggle("SUPPLIER", e.target.checked)} />
              <span>Offer experiences (supplier)</span></label>
          </div>
        </fieldset>
        <div className="form-grid">
          <label><span>Business name</span><input required maxLength={160} value={form.businessName} onChange={set("businessName")} /></label>
          <label><span>Type of business</span><select value={form.businessType} onChange={set("businessType")}>
            {Object.entries(businessTypeLabels).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
          <label><span>Contact person</span><input required maxLength={160} autoComplete="name" value={form.contactName} onChange={set("contactName")} /></label>
          <label><span>Business email</span><input required type="email" maxLength={180} autoComplete="email" value={form.email} onChange={set("email")} /></label>
          <label><span>Phone <em className="optional">(optional)</em></span><input maxLength={80} autoComplete="tel" value={form.phone} onChange={set("phone")} /></label>
          <label><span>Website <em className="optional">(optional)</em></span><input maxLength={300} placeholder="https://" value={form.website} onChange={set("website")} /></label>
          <label><span>Area <em className="optional">(optional)</em></span><input maxLength={160} value={form.area} onChange={set("area")} /></label>
          <label><span>Registration number <em className="optional">(optional)</em></span><input maxLength={80} value={form.registrationNumber} onChange={set("registrationNumber")} /></label>
          <label className="full-field"><span>Licences, gradings or memberships <em className="optional">(optional)</em></span>
            <input maxLength={600} value={form.licences} onChange={set("licences")} /></label>
          <label className="full-field"><span>About your business</span><textarea required maxLength={2000} rows={3} value={form.description} onChange={set("description")} /></label>
          {routes.includes("SUPPLIER") && <label className="full-field"><span>Experiences or services you would like to offer</span>
            <textarea required maxLength={2000} rows={3} value={form.servicesOffered} onChange={set("servicesOffered")} /></label>}
          <label><span>Choose a password for your partner area</span>
            <input required type="password" minLength={12} maxLength={72} autoComplete="new-password" value={form.password} onChange={set("password")} />
            <span className="form-note">At least 12 characters. You can sign in once approved.</span></label>
        </div>
        <label className="choice partner-consent"><input type="checkbox" required checked={form.privacyConsent}
          onChange={e => setForm(f => ({ ...f, privacyConsent: e.target.checked }))} />
          <span>SL Tours may use these details to assess my application and contact me about it.</span></label>
        {(error || send.isError) && <ErrorState message={error || send.error!.message} />}
        <PrivacyNote />
        <button className="button button-primary" type="submit" disabled={send.isPending}>{send.isPending ? "Sending…" : "Send application"}</button>
      </form>
    </section>
  </>;
}
