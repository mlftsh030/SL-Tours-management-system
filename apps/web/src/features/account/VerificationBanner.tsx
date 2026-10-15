import { useState } from "react";
import { MailWarning } from "lucide-react";
import { resendVerification, type EmailDelivery } from "../auth/authApi";

/** Shown until the address is confirmed. Guest bookings made with it are only added after confirmation. */
export function VerificationBanner({ email }: { email: string }) {
  const [pending, setPending] = useState(false);
  const [result, setResult] = useState<{ delivery?: EmailDelivery; error?: string } | null>(null);
  async function resend() {
    setPending(true); setResult(null);
    try { setResult({ delivery: (await resendVerification()).delivery }); }
    catch (err) { setResult({ error: (err as Error).message }); } finally { setPending(false); }
  }
  return <section className="account-verify" aria-labelledby="verify-title">
    <MailWarning aria-hidden="true" />
    <div>
      <h2 id="verify-title">Confirm your email address</h2>
      <p>Open the link we sent to <strong>{email}</strong>. Once it is confirmed, bookings and invoices you received as a guest with this address are
        added to your account.</p>
      <div aria-live="polite">
        {result?.delivery === "SENT" && <p className="account-verify-ok">A new link is on its way. It expires in 48 hours.</p>}
        {result?.delivery === "NOT_CONFIGURED" && <p className="auth-field-error">Email confirmation is not available on this site yet. Contact SL Tours for help.</p>}
        {result?.delivery === "FAILED" && <p className="auth-field-error">Our email service did not accept the message. Please try again in a few minutes.</p>}
        {result?.error && <p className="auth-field-error">{result.error}</p>}
      </div>
    </div>
    <button type="button" className="button button-secondary" onClick={resend} disabled={pending} aria-busy={pending}>{pending ? "Sending…" : "Send a new link"}</button>
  </section>;
}
