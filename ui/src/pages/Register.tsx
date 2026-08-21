import { type FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import { apiRequest, fieldErrorsOf, generalMessageOf } from "../api/client";

/** Open-an-account form. On success the API returns 202 and emails a verification link. */
export function Register() {
  const [email, setEmail] = useState("");
  const [fullName, setFullName] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    if (password !== confirm) {
      setFieldErrors({ confirm: "Those passwords don't match." });
      return;
    }
    setBusy(true);
    try {
      await apiRequest("/api/v1/register", { method: "POST", body: { email, fullName, password } });
      setDone(true);
    } catch (err) {
      setFieldErrors(fieldErrorsOf(err));
      setError(generalMessageOf(err, "Something went wrong."));
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <AuthCard title="Check your email">
        <p className="lede">
          We've sent you a verification link. Follow it to activate your account, then sign in.
        </p>
        <Link className="btn btn--ghost" to="/">Back to start</Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Open an account">
      <form onSubmit={submit} className="form" noValidate>
        <Field label="Full name" value={fullName} onChange={setFullName} autoComplete="name"
               error={fieldErrors.fullName} />
        <Field label="Email" type="email" value={email} onChange={setEmail} autoComplete="email"
               error={fieldErrors.email} />
        <Field label="Password" type="password" value={password} onChange={setPassword}
               autoComplete="new-password" minLength={12} hint="At least 12 characters."
               error={fieldErrors.password} />
        <Field label="Confirm password" type="password" value={confirm} onChange={setConfirm}
               autoComplete="new-password" minLength={12}
               error={fieldErrors.confirm} />
        {error && <p className="notice notice--error">{error}</p>}
        <button className="btn btn--primary" disabled={busy}>{busy ? "Creating…" : "Create account"}</button>
      </form>
      <p className="cardfoot">Already have an account? <Link to="/">Sign in</Link></p>
    </AuthCard>
  );
}

// Small local building blocks reused across auth pages.
export function AuthCard({ title, wide, children }:
    { title: string; wide?: boolean; children: React.ReactNode }) {
  return (
    <section className={wide ? "authcard authcard--wide" : "authcard"}>
      <h1 className="authcard__title">{title}</h1>
      {children}
    </section>
  );
}

export function Field(props: {
  label: string; value: string; onChange: (v: string) => void;
  type?: string; autoComplete?: string; hint?: string;
  minLength?: number; invalid?: boolean; error?: string;
}) {
  const isPassword = props.type === "password";
  const [revealed, setRevealed] = useState(false);
  const inputType = isPassword && revealed ? "text" : props.type ?? "text";
  const showError = !!props.error;

  return (
    <label className="field">
      <span className="field__label">{props.label}</span>
      <span className={isPassword ? "field__wrap" : undefined}>
        <input
          className="field__input"
          type={inputType}
          value={props.value}
          autoComplete={props.autoComplete}
          minLength={props.minLength}
          aria-invalid={showError || props.invalid || undefined}
          onChange={(e) => props.onChange(e.target.value)}
          required
        />
        {isPassword && (
          <button
            type="button"
            className="field__reveal"
            onClick={() => setRevealed((r) => !r)}
            aria-label={revealed ? "Hide password" : "Show password"}
            aria-pressed={revealed}
            title={revealed ? "Hide password" : "Show password"}
          >
            {revealed ? (
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
                   strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24" />
                <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68" />
                <path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61" />
                <line x1="2" y1="2" x2="22" y2="22" />
              </svg>
            ) : (
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
                   strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z" />
                <circle cx="12" cy="12" r="3" />
              </svg>
            )}
          </button>
        )}
      </span>
      {/* Field-level error takes the slot; the hint shows only when there's no error to show. */}
      {showError
        ? <span className="field__error" role="alert">{props.error}</span>
        : props.hint && <span className="field__hint">{props.hint}</span>}
    </label>
  );
}