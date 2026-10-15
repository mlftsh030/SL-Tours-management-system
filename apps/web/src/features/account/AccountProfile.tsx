import { useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { BadgeCheck } from "lucide-react";
import { ErrorState, LoadingState } from "../../components/StateBlocks";
import { Field, focusFirstInvalid, PHONE_PATTERN } from "../auth/AuthFields";
import { formatDateTime, getProfile, updateProfile } from "./accountApi";

export function AccountProfile() {
  const client = useQueryClient();
  const profile = useQuery({ queryKey: ["account", "profile"], queryFn: getProfile, retry: false });
  const [errors, setErrors] = useState<{ name?: string; phone?: string }>({});
  const [state, setState] = useState<{ pending?: boolean; error?: string; saved?: boolean }>({});
  if (profile.isLoading) return <LoadingState label="Loading your profile" />;
  if (profile.isError) return <ErrorState message={profile.error.message} />;
  const p = profile.data!;
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const name = String(data.get("name") ?? "").trim(), phone = String(data.get("phone") ?? "").trim();
    const found = { name: name ? undefined : "Enter your full name", phone: phone && !PHONE_PATTERN.test(phone) ? "Start with + and your country code, for example +27 82 555 1234" : undefined };
    setErrors(found);
    if (found.name || found.phone) { focusFirstInvalid(event.currentTarget, found); return; }
    setState({ pending: true });
    try {
      client.setQueryData(["account", "profile"], await updateProfile({ name, phone: phone || null }));
      await client.invalidateQueries({ queryKey: ["session"] });
      setState({ saved: true });
    } catch (err) { setState({ error: (err as Error).message }); }
  }
  return <section aria-labelledby="profile-title">
    <h2 id="profile-title">Profile and contact details</h2>
    <div className="account-profile">
      <form className="form-surface auth-form" onSubmit={submit} noValidate aria-label="Profile">
        <Field label="Full name" name="name" autoComplete="name" maxLength={160} defaultValue={p.name} error={errors.name} />
        <Field label="WhatsApp number (optional)" name="phone" type="tel" autoComplete="tel" maxLength={40} defaultValue={p.phone ?? ""} error={errors.phone}
          hint="Include your country code, for example +27 82 555 1234." />
        <div aria-live="polite">{state.saved && <p className="account-verify-ok">Your details have been saved.</p>}
          {state.error && <ErrorState message={state.error} />}</div>
        <button type="submit" className="button button-primary" disabled={state.pending} aria-busy={state.pending}>{state.pending ? "Saving…" : "Save changes"}</button>
      </form>
      <dl className="form-surface account-facts account-facts-stacked">
        <div><dt>Email address</dt><dd>{p.email} {p.emailVerified
          ? <span className="account-verified"><BadgeCheck aria-hidden="true" />Confirmed</span> : <span className="admin-badge status-draft">Not confirmed</span>}</dd></div>
        <div><dt>Member since</dt><dd>{formatDateTime(p.memberSince)}</dd></div>
        <div><dt>Password</dt><dd><a href="/forgot-password">Change your password</a> by email.</dd></div>
      </dl>
    </div>
    <p className="form-note">To change your email address, contact SL Tours so we can keep your bookings linked to you.</p>
  </section>;
}
