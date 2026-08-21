import { type FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import { apiRequest } from "../api/client";
import { AuthCard, Field } from "./Register";

/** Requests a password-reset email. Always reports the same outcome (no account enumeration). */
export function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await apiRequest("/api/v1/password/forgot", { method: "POST", body: { email } });
    } finally {
      setBusy(false);
      setSent(true);
    }
  }

  return (
    <AuthCard title="Reset your password">
      {sent ? (
        <>
          <p className="lede">If that address has an account, we've sent a reset link. It's valid for one hour.</p>
          <Link className="btn btn--ghost" to="/">Back to sign in</Link>
        </>
      ) : (
        <form onSubmit={submit} className="form" noValidate>
          <Field label="Email" type="email" value={email} onChange={setEmail} autoComplete="email" />
          <button className="btn btn--primary" disabled={busy}>{busy ? "Sending…" : "Send reset link"}</button>
        </form>
      )}
    </AuthCard>
  );
}