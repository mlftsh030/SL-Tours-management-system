import { useId, useState, type InputHTMLAttributes } from "react";
import { Eye, EyeOff } from "lucide-react";

export const PHONE_PATTERN = /^\+[1-9][0-9 ()-]{6,22}[0-9]$/;
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
/** Bcrypt reads at most 72 bytes; the server refuses longer passwords rather than silently truncating them. */
export const passwordProblem = (value: string) =>
  value.length < 12 ? "Use at least 12 characters" : new TextEncoder().encode(value).length > 72 ? "This password is too long" : "";

/** Moves focus to the first field with an error, in form order. Called right after validation, before React re-renders. */
export function focusFirstInvalid(form: HTMLFormElement | null, invalid: Record<string, string | undefined>) {
  const field = Array.from(form?.elements ?? []).find(e => e instanceof HTMLInputElement && invalid[e.name]);
  (field as HTMLInputElement | undefined)?.focus();
}

type FieldProps = { label: string; error?: string; hint?: string } & InputHTMLAttributes<HTMLInputElement>;

/** A labelled input whose hint and error are announced with it. */
export function Field({ label, error, hint, ...input }: FieldProps) {
  const id = useId();
  const described = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(" ") || undefined;
  return <div className="auth-field">
    <label htmlFor={id}>{label}</label>
    <input id={id} aria-invalid={error ? true : undefined} aria-describedby={described} {...input} />
    {hint && <small id={`${id}-hint`}>{hint}</small>}
    {error && <p className="auth-field-error" id={`${id}-error`}>{error}</p>}
  </div>;
}

/** A password input with a show/hide toggle. The toggle never submits the form. */
export function PasswordField({ label, error, hint, ...input }: FieldProps) {
  const id = useId();
  const [visible, setVisible] = useState(false);
  const described = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(" ") || undefined;
  return <div className="auth-field">
    <label htmlFor={id}>{label}</label>
    <div className="auth-password">
      <input id={id} type={visible ? "text" : "password"} aria-invalid={error ? true : undefined} aria-describedby={described}
        autoCapitalize="none" autoCorrect="off" spellCheck={false} {...input} />
      <button type="button" className="auth-password-toggle" onClick={() => setVisible(v => !v)} aria-pressed={visible}
        aria-label={visible ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`}>
        {visible ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
      </button>
    </div>
    {hint && <small id={`${id}-hint`}>{hint}</small>}
    {error && <p className="auth-field-error" id={`${id}-error`}>{error}</p>}
  </div>;
}

/** The shared frame of every sign-in, sign-up and recovery page. */
export function AuthFrame({ eyebrow, title, intro, children }: { eyebrow: string; title: string; intro?: React.ReactNode; children: React.ReactNode }) {
  return <main className="auth-page">
    <a href="/" className="auth-brand"><img src="/assets/logo.png" alt="SL Tours home" width="64" height="64" /></a>
    <div className="form-surface auth-card">
      <p className="eyebrow">{eyebrow}</p>
      <h1>{title}</h1>
      {intro && <p>{intro}</p>}
      {children}
    </div>
    <a className="auth-back" href="/">Back to website</a>
  </main>;
}
