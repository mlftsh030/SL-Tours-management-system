import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { requestPasswordReset, resetPassword, verifyEmail, type EmailDelivery, type LinkedRecords } from "./authApi";
import { AuthFrame, EMAIL_PATTERN, Field, focusFirstInvalid, PasswordField, passwordProblem } from "./AuthFields";
import { useLinkToken } from "./useLinkToken";

export function ForgotPasswordPage() {
  useDocumentTitle("Reset your password | SL Tours");
  const [error, setError] = useState(""); const [fieldError, setFieldError] = useState("");
  const [pending, setPending] = useState(false);
  const [result, setResult] = useState<{ email: string; delivery: EmailDelivery } | null>(null);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const email = String(new FormData(event.currentTarget).get("email") ?? "").trim();
    if (!EMAIL_PATTERN.test(email)) { setFieldError("Enter the email address you signed up with"); event.currentTarget.querySelector("input")?.focus(); return; }
    setFieldError(""); setError(""); setPending(true);
    try { setResult({ email, delivery: (await requestPasswordReset(email)).delivery }); }
    catch (err) { setError((err as Error).message); } finally { setPending(false); }
  }
  if (result) return <AuthFrame eyebrow="Your SL Tours account" title={result.delivery === "SENT" ? "Check your email" : "We could not send the email"}>
    {result.delivery === "SENT" && <p role="status">If <strong>{result.email}</strong> has an SL Tours account, we have sent it a link to choose a new
      password. The link works once and expires in one hour. Check your spam folder if it has not arrived in a few minutes.</p>}
    {result.delivery === "NOT_CONFIGURED" && <ErrorState message="Password reset by email is not available on this site yet. Please contact SL Tours by WhatsApp or email and we will help you." />}
    {result.delivery === "FAILED" && <ErrorState message="Our email service did not accept the message. Please try again in a few minutes, or contact SL Tours." />}
    <p><Link to="/login">Back to sign in</Link></p>
  </AuthFrame>;
  return <AuthFrame eyebrow="Your SL Tours account" title="Forgot your password?" intro="Enter your email address and we will send you a link to choose a new one.">
    <form className="auth-form" onSubmit={submit} noValidate aria-label="Request a password reset">
      <Field label="Email address" name="email" type="email" autoComplete="username" inputMode="email" maxLength={180} error={fieldError} />
      {error && <ErrorState message={error} />}
      <button type="submit" className="button button-primary" disabled={pending} aria-busy={pending}>{pending ? "Sending…" : "Send reset link"}</button>
    </form>
    <p><Link to="/login">Back to sign in</Link></p>
  </AuthFrame>;
}

export function ResetPasswordPage() {
  useDocumentTitle("Choose a new password | SL Tours");
  const token = useLinkToken();
  const [errors, setErrors] = useState<{ password?: string; confirmation?: string }>({});
  const [error, setError] = useState(""); const [pending, setPending] = useState(false); const [done, setDone] = useState(false);
  const client = useQueryClient();
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const password = String(data.get("password") ?? ""), confirmation = String(data.get("passwordConfirmation") ?? "");
    const found = { password: passwordProblem(password) || undefined, confirmation: confirmation !== password ? "The passwords do not match" : undefined };
    setErrors(found); setError("");
    if (found.password || found.confirmation) { focusFirstInvalid(event.currentTarget, { password: found.password, passwordConfirmation: found.confirmation }); return; }
    setPending(true);
    try { await resetPassword(token, password, confirmation); client.clear(); setDone(true); }
    catch (err) { setError((err as Error).message); } finally { setPending(false); }
  }
  if (!token) return <AuthFrame eyebrow="Your SL Tours account" title="This link is incomplete">
    <ErrorState message="Open the reset link from your email again, or ask for a new one." /><p><Link to="/forgot-password">Ask for a new link</Link></p></AuthFrame>;
  if (done) return <AuthFrame eyebrow="Your SL Tours account" title="Your password has been changed">
    <p role="status">For your security, you have been signed out everywhere. Sign in with your new password.</p>
    <Link className="button button-primary" to="/login">Sign in</Link></AuthFrame>;
  return <AuthFrame eyebrow="Your SL Tours account" title="Choose a new password">
    <form className="auth-form" onSubmit={submit} noValidate aria-label="Choose a new password">
      <PasswordField label="New password" name="password" autoComplete="new-password" maxLength={64} error={errors.password}
        hint="At least 12 characters. Passwords are case-sensitive." />
      <PasswordField label="Confirm new password" name="passwordConfirmation" autoComplete="new-password" maxLength={64} error={errors.confirmation} />
      {error && <><ErrorState message={error} /><p><Link to="/forgot-password">Ask for a new link</Link></p></>}
      <button type="submit" className="button button-primary" disabled={pending} aria-busy={pending}>{pending ? "Saving…" : "Save new password"}</button>
    </form>
  </AuthFrame>;
}

const count = (n: number, one: string, many: string) => n ? `${n} ${n === 1 ? one : many}` : "";
export function describeLinked(l: LinkedRecords) {
  return [count(l.bookings, "booking", "bookings"), count(l.enquiries, "enquiry", "enquiries"),
    count(l.documents, "invoice or quotation", "invoices and quotations")].filter(Boolean).join(", ");
}

export function VerifyEmailPage() {
  useDocumentTitle("Confirm your email | SL Tours");
  const token = useLinkToken();
  const client = useQueryClient();
  const started = useRef(false);
  const [state, setState] = useState<{ status: "working" } | { status: "done"; email: string; linked: LinkedRecords } | { status: "error"; message: string }>(
    token ? { status: "working" } : { status: "error", message: "This link is incomplete. Open it from your email again." });
  useEffect(() => {
    if (!token || started.current) return;
    started.current = true;
    verifyEmail(token).then(r => { client.invalidateQueries({ queryKey: ["session"] }); client.invalidateQueries({ queryKey: ["account"] }); setState({ status: "done", ...r }); })
      .catch(err => setState({ status: "error", message: (err as Error).message }));
  }, [token, client]);
  if (state.status === "working") return <AuthFrame eyebrow="Your SL Tours account" title="Confirming your email"><LoadingState label="Confirming your email address" /></AuthFrame>;
  if (state.status === "error") return <AuthFrame eyebrow="Your SL Tours account" title="We could not confirm your email">
    <ErrorState message={state.message} /><p>Signed in? You can send a new link from <Link to="/account">your account</Link>.</p></AuthFrame>;
  const linked = describeLinked(state.linked);
  return <AuthFrame eyebrow="Your SL Tours account" title="Your email is confirmed">
    <p role="status"><strong>{state.email}</strong> is confirmed.{linked ? ` We found and added ${linked} you made with this address.` : ""}</p>
    <Link className="button button-primary" to="/account">Go to my account</Link>
  </AuthFrame>;
}
