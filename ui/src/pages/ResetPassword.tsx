import { type FormEvent, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { apiRequest, fieldErrorsOf, generalMessageOf } from "../api/client";
import { AuthCard, Field } from "./Register";

/** Completes a reset using the ?token from the reset email. */
export function ResetPassword() {
  const [params] = useSearchParams();
  const token = params.get("token") ?? "";
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    if (newPassword !== confirm) {
      setFieldErrors({ confirm: "Those passwords don't match." });
      return;
    }
    setBusy(true);
    try {
      await apiRequest("/api/v1/password/reset", { method: "POST", body: { token, newPassword } });
      setDone(true);
    } catch (err) {
      setFieldErrors(fieldErrorsOf(err));
      setError(generalMessageOf(err, "Something went wrong."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthCard title="Choose a new password">
      {done ? (
        <>
          <p className="lede">Your password has been updated. You can sign in with it now.</p>
          <Link className="btn btn--primary" to="/">Sign In</Link>
        </>
      ) : (
        <form onSubmit={submit} className="form" noValidate>
          <Field label="New password" type="password" value={newPassword} onChange={setNewPassword}
                 autoComplete="new-password" minLength={12} hint="At least 12 characters."
                 error={fieldErrors.newPassword} />
          <Field label="Confirm new password" type="password" value={confirm} onChange={setConfirm}
                 autoComplete="new-password" minLength={12} error={fieldErrors.confirm} />
          {error && <p className="notice notice--error">{error}</p>}
          <button className="btn btn--primary" disabled={busy || !token}>{busy ? "Saving…" : "Update Password"}</button>
        </form>
      )}
    </AuthCard>
  );
}