import { PrivacyNote } from "../../app/PrivacyPage";
import { useRef, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { signIn, register, homeFor } from "./authApi";
import { ErrorState } from "../../components/StateBlocks";
import { useDocumentTitle } from "../../lib/useDocumentTitle";
import { ApiRequestError } from "../../lib/api";
import { IDLE_NOTICE_KEY } from "./useIdleSignOut";
import { AuthFrame, EMAIL_PATTERN, Field, focusFirstInvalid, PasswordField, PHONE_PATTERN, passwordProblem } from "./AuthFields";

function takeIdleNotice() {
  try {
    const shown = window.sessionStorage.getItem(IDLE_NOTICE_KEY) === "1";
    window.sessionStorage.removeItem(IDLE_NOTICE_KEY);
    return shown;
  } catch { return false; }
}

/** Only same-site destinations that need an account are honoured after signing in. */
function safeNext(next: string | null) {
  return next && /^\/(?:book\/|book-private-trip(?:\/|$)|account(?:\/|$)|operations(?:\/|$)|driver(?:\/|$)|partner(?:\/|$))/.test(next) ? next : null;
}

type Errors = Partial<Record<"name" | "email" | "phone" | "password" | "passwordConfirmation", string>>;

export function AuthPage({ registration = false }: { registration?: boolean }) {
  useDocumentTitle(registration ? "Create account | SL Tours" : "Sign in | SL Tours");
  const [params] = useSearchParams();
  const admin = params.get("admin") === "1";
  const navigate = useNavigate();
  const client = useQueryClient();
  const form = useRef<HTMLFormElement>(null);
  const [error, setError] = useState<{ message: string; duplicate?: boolean } | null>(null);
  const [errors, setErrors] = useState<Errors>({});
  const [pending, setPending] = useState(false);
  const [idleNotice] = useState(takeIdleNotice);
  const query = params.toString() ? `?${params.toString()}` : "";

  function validate(data: FormData): Errors {
    const e: Errors = {};
    const email = String(data.get("email") ?? "").trim();
    if (!email) e.email = "Enter your email address";
    else if (!EMAIL_PATTERN.test(email)) e.email = "Enter an email address like name@example.com";
    if (!registration) { if (!String(data.get("password") ?? "")) e.password = "Enter your password"; return e; }
    if (!String(data.get("name") ?? "").trim()) e.name = "Enter your full name";
    const phone = String(data.get("phone") ?? "").trim();
    if (phone && !PHONE_PATTERN.test(phone)) e.phone = "Start with + and your country code, for example +27 82 555 1234";
    const password = String(data.get("password") ?? "");
    const problem = passwordProblem(password);
    if (problem) e.password = problem;
    if (String(data.get("passwordConfirmation") ?? "") !== password) e.passwordConfirmation = "The passwords do not match";
    return e;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const found = validate(data);
    setErrors(found); setError(null);
    if (Object.keys(found).length) { focusFirstInvalid(form.current, found); return; }
    setPending(true);
    const email = String(data.get("email")).trim(); const password = String(data.get("password"));
    try {
      let delivery: string | undefined;
      if (registration) {
        delivery = (await register({ name: String(data.get("name")).trim(), email, phone: String(data.get("phone") ?? "").trim(), password,
          passwordConfirmation: String(data.get("passwordConfirmation")) })).verificationEmail;
      }
      const session = await signIn(email, password);
      client.clear(); client.setQueryData(["session"], session);
      if (admin && session.role !== "ADMIN" && session.role !== "STAFF") { navigate(homeFor(session.role), { replace: true }); return; }
      const destination = safeNext(params.get("next")) ?? homeFor(session.role);
      if (destination.startsWith("/book/")) window.location.assign(destination);
      else navigate(destination, { replace: true, state: delivery ? { registered: delivery } : undefined });
    } catch (err) {
      const duplicate = err instanceof ApiRequestError && err.status === 409;
      setError({ message: (err as Error).message, duplicate });
    } finally { setPending(false); }
  }

  return <AuthFrame eyebrow={admin ? "SL Tours administration" : "Your SL Tours account"} title={registration ? "Create your account" : "Welcome back"}
    intro={registration ? "Keep your enquiries, bookings, quotations and invoices in one place." : "Sign in to see your bookings and documents."}>
    {idleNotice && <p className="form-note" role="status">You were signed out after 30 minutes of inactivity.</p>}
    <form ref={form} className="auth-form" onSubmit={submit} noValidate aria-label={registration ? "Create account" : "Sign in"}>
      {registration && <Field label="Full name" name="name" autoComplete="name" maxLength={160} error={errors.name} />}
      <Field label="Email address" name="email" type="email" autoComplete={registration ? "email" : "username"} maxLength={180} error={errors.email}
        inputMode="email" />
      {registration && <Field label="WhatsApp number (optional)" name="phone" type="tel" autoComplete="tel" maxLength={40} error={errors.phone}
        hint="Include your country code, for example +27 82 555 1234." placeholder="+27" />}
      <PasswordField label="Password" name="password" autoComplete={registration ? "new-password" : "current-password"} maxLength={64}
        error={errors.password} hint={registration ? "At least 12 characters. Passwords are case-sensitive." : undefined} />
      {registration && <PasswordField label="Confirm password" name="passwordConfirmation" autoComplete="new-password" maxLength={64}
        error={errors.passwordConfirmation} />}
      {!registration && !admin && <Link className="auth-inline-link" to="/forgot-password">Forgot your password?</Link>}
      {error && <div className="auth-error"><ErrorState message={error.message} />
        {error.duplicate && <p><Link to={`/login${query}`}>Sign in</Link> or <Link to="/forgot-password">reset your password</Link>.</p>}</div>}
      {registration && <PrivacyNote />}
      <button type="submit" className="button button-primary" disabled={pending} aria-busy={pending}>
        {pending ? (registration ? "Creating your account…" : "Signing in…") : registration ? "Create account" : "Sign in"}
      </button>
    </form>
    {registration && <p className="form-note">We will email you a link to confirm your address. Bookings you made before as a guest are added once
      you confirm it.</p>}
    {!admin && <p>{registration ? "Already have an account? " : "New to SL Tours? "}
      <Link to={`${registration ? "/login" : "/register"}${query}`}>{registration ? "Sign in" : "Create an account"}</Link></p>}
    {!admin && !registration && <p className="form-note">You can still <Link to="/experiences">browse tours</Link> and <Link to="/contact">send an enquiry</Link> without an account.</p>}
  </AuthFrame>;
}
