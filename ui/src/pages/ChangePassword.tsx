import { type FormEvent, useState } from "react";
import { useAuth } from "react-oidc-context";
import { Link } from "react-router-dom";
import { apiRequest, fieldErrorsOf, generalMessageOf } from "../api/client";
import { AuthCard, Field } from "./Register";

/** Authenticated password change. Verifies the current password server-side and alerts by email. */
export function ChangePassword() {
  const auth = useAuth();
  const [currentPassword, setCurrent] = useState("");
  const [newPassword, setNew] = useState("");
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
      await apiRequest("/api/v1/password/change", {
        method: "POST",
        token: auth.user?.access_token,
        body: { currentPassword, newPassword },
      });
      setDone(true);
    } catch (err) {
      setFieldErrors(fieldErrorsOf(err));
      setError(generalMessageOf(err, "Something went wrong."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthCard title="Change password">
      {done ? (
        <>
          <p className="lede">Your password has been changed. We've emailed you a confirmation.</p>
          <Link className="btn btn--ghost" to="/profile">Back to Account</Link>
        </>
      ) : (
        <form onSubmit={submit} className="form" noValidate>
          <Field label="Current password" type="password" value={currentPassword} onChange={setCurrent}
                 autoComplete="current-password" error={fieldErrors.currentPassword} />
          <Field label="New password" type="password" value={newPassword} onChange={setNew}
                 autoComplete="new-password" minLength={12} hint="At least 12 characters."
                 error={fieldErrors.newPassword} />
          <Field label="Confirm new password" type="password" value={confirm} onChange={setConfirm}
                 autoComplete="new-password" minLength={12} error={fieldErrors.confirm} />
          {error && <p className="notice notice--error">{error}</p>}
          <div className="form__actions">
            <button className="btn btn--primary" disabled={busy}>{busy ? "Saving…" : "Update Password"}</button>
            <Link className="btn btn--ghost" to="/profile">Cancel</Link>
          </div>
        </form>
      )}
    </AuthCard>
  );
}