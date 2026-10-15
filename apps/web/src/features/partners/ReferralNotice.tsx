import { useState, type FormEvent } from "react";
import { applyReferral, clearReferral, useReferral } from "../../lib/referral";

/**
 * Disclosure shown on booking and enquiry forms when a partner referred the visitor, with a way to remove it, or a
 * discreet field to enter a partner's referral code.
 */
export function ReferralNotice() {
  const referral = useReferral();
  const [open, setOpen] = useState(false);
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  async function apply(event: FormEvent) {
    event.preventDefault(); setError("");
    try { await applyReferral(code, "CODE"); setOpen(false); setCode(""); }
    catch { setError("That referral code is not active."); }
  }
  if (referral) return <p className="referral-notice" role="note">
    Referred by <strong>{referral.partnerName}</strong>. {referral.partnerName} may receive a commission from SL Tours if you book; your price is the same.
    <button type="button" className="referral-remove" onClick={clearReferral}>Remove referral</button>
  </p>;
  if (!open) return <p className="referral-entry"><button type="button" className="referral-remove" onClick={() => setOpen(true)}>Have a partner referral code?</button></p>;
  // A separate small form would nest inside the booking form, so the code is applied with a button instead of submit.
  return <div className="referral-entry">
    <label htmlFor="referral-code">Referral code</label>
    <input id="referral-code" value={code} maxLength={20} autoComplete="off" onChange={e => setCode(e.target.value)}
      onKeyDown={e => { if (e.key === "Enter") apply(e); }} />
    <button type="button" className="button button-secondary" disabled={!code.trim()} onClick={apply}>Apply</button>
    {error && <span className="referral-error" role="alert">{error}</span>}
  </div>;
}
