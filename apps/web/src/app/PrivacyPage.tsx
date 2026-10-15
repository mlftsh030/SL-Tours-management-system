import { Link } from "react-router-dom";
import { PageBanner } from "../components/PageBanner";
import { address, contact } from "../lib/contact";
import { useDocumentTitle } from "../lib/useDocumentTitle";

/** Last reviewed date shown to visitors. Update it whenever the notice changes (see docs/operations/OWNER_DECISIONS.md). */
export const PRIVACY_UPDATED = "8 October 2026";

/**
 * Privacy notice. Every statement here describes what the website actually does; keep it in step with the code
 * (forms, third-party scripts, browser storage) when those change.
 */
export function PrivacyPage() {
  useDocumentTitle("Privacy Notice | SL Tours", "How SL Tours uses the personal information you share through this website, and your choices.");
  return <>
    <PageBanner title="Privacy Notice" subtitle="How we use the information you share with us" image="/assets/gallery/16.webp" />
    <article className="section legal-page">
      <p className="legal-updated">Last updated: {PRIVACY_UPDATED}</p>

      <h2>Who we are</h2>
      <p>SL Tours is a Cape Town tour and transport company at {address}. We are responsible for the personal information you
        share through this website. Questions about your information: email <a href={`mailto:${contact.email}`}>{contact.email}</a> or
        call <a href={contact.phoneHref}>{contact.phoneDisplay}</a>.</p>

      <h2>What we collect</h2>
      <ul>
        <li><strong>Enquiries, quotes and trip requests:</strong> your name, email address and phone number; travel dates, times and
          number of guests; pickup and destination; flight details for airport transfers; and anything you tell us in special
          requests, such as accessibility needs, dietary requirements or travelling with children.</li>
        <li><strong>Your account, if you create one:</strong> your name and email address. Your password is stored only in a
          scrambled form that cannot be turned back into the password.</li>
        <li><strong>Partner applications:</strong> your business and contact details, and when you agreed to us assessing them.</li>
        <li><strong>Partner referrals:</strong> if you arrive through a partner's link, the partner's referral code. You can remove it
          before you send a request.</li>
        <li><strong>Technical information:</strong> our servers keep standard logs (your IP address, the pages requested and your
          browser type) to keep the website secure and fix problems.</li>
      </ul>

      <h2>Why we use it</h2>
      <ul>
        <li>To answer your enquiry, prepare quotes and invoices, and arrange and run your trip.</li>
        <li>To keep records of bookings and payments.</li>
        <li>To run your account: signing in, confirming your email address and resetting your password.</li>
        <li>To protect the website and your account, for example by limiting repeated sign-in attempts.</li>
      </ul>
      <p>We use your details only for the request you made. We do not send marketing messages and do not sell your information.
        If we ever offer news or offers, we will ask for your permission separately, and saying no will not affect your trip.</p>

      <h2>Who sees it</h2>
      <ul>
        <li><strong>SL Tours staff</strong> who handle enquiries, bookings and payments.</li>
        <li><strong>Your driver</strong> sees your name and phone number for their own trips only.</li>
        <li><strong>Referral partners</strong> see only a booking reference, the type of trip, the date and its status. They never see
          your name, contact details or travel details.</li>
        <li><strong>Service providers</strong> who run the website for us: our servers are hosted by DigitalOcean in Frankfurt,
          Germany, so your information is stored outside South Africa; and our email provider sends account emails, such as
          email confirmations and password resets.</li>
      </ul>

      <h2>Services that load on our pages</h2>
      <p>When you open our pages, your browser also connects to these services, which receive your IP address and browser details:</p>
      <ul>
        <li><strong>Google Fonts</strong> for the website's lettering.</li>
        <li><strong>Google Translate</strong> for the language selector.</li>
        <li><strong>Elfsight</strong>, which shows our customer reviews on the homepage.</li>
      </ul>
      <p>If you choose to contact us on <strong>WhatsApp</strong>, your message is sent through WhatsApp under its own terms; we
        receive only what you send. This website does not use advertising or analytics trackers.</p>

      <h2>Cookies and browser storage</h2>
      <ul>
        <li>A sign-in cookie and a security token, needed to keep you signed in and to protect forms.</li>
        <li>Your browser remembers your chosen currency, a partner referral code if you have one, and when you were last active
          so that you are signed out after a period of inactivity.</li>
        <li>Google Translate sets a cookie to remember the language you choose.</li>
      </ul>

      <h2>How long we keep it</h2>
      <p>We keep your information only as long as we need it for the reasons above, including financial records we are required
        to keep by law. You can ask us to delete your information; we will, unless we must keep it.</p>

      <h2>Your rights</h2>
      <p>You may ask to see the information we hold about you, ask us to correct or delete it, or object to how we use it. Email{" "}
        <a href={`mailto:${contact.email}`}>{contact.email}</a>. If you are not satisfied with our answer, you may complain to the
        Information Regulator of South Africa (<a href="https://inforegulator.org.za" target="_blank" rel="noopener noreferrer">inforegulator.org.za</a>).</p>

      <p className="legal-updated"><Link to="/contact">Contact us</Link> with any question about this notice.</p>
    </article>
  </>;
}

/** Short notice shown beside every form that collects personal information. Opens in a new tab so that reading it
 *  does not lose what the visitor has typed. */
export function PrivacyNote() {
  return <p className="privacy-note">We use your details only to handle this request. See our{" "}
    <a href="/privacy" target="_blank" rel="noopener">privacy notice<span className="sr-only"> (opens in a new tab)</span></a>.</p>;
}
