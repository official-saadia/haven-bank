import { type FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Link } from "react-router-dom";
import { useAuth } from "react-oidc-context";
import { ApiError, apiRequest, fieldErrorsOf, generalMessageOf, type BeneficiaryResponse } from "../api/client";

/**
 * Saved payees. Adding one is an address-book write — the API deliberately does not confirm the
 * account exists, because that would turn this form into an account-enumeration oracle. The
 * account is resolved at transfer time instead.
 */
export function Beneficiaries() {
  const auth = useAuth();
  const token = auth.user?.access_token;

  const [payees, setPayees] = useState<BeneficiaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // The add dialog: open state, its fields, and its own busy/error so a failure shows in the modal.
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [nickname, setNickname] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [addError, setAddError] = useState<string | null>(null);
  const [addFieldErrors, setAddFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  // The remove confirmation: which payee is pending deletion, plus its own busy/error state.
  const [pendingRemove, setPendingRemove] = useState<BeneficiaryResponse | null>(null);
  const [removeBusy, setRemoveBusy] = useState(false);
  const [removeError, setRemoveError] = useState<string | null>(null);
  const firstFieldRef = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    if (!token) return;
    try {
      setPayees(await apiRequest<BeneficiaryResponse[]>("/api/v1/beneficiaries", { token }));
    } catch {
      setError("Couldn't load your payees.");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { void load(); }, [load]);

  function openAdd() {
    setName(""); setNickname(""); setAccountNumber("");
    setAddError(null);
    setAddFieldErrors({});
    setOpen(true);
  }

  function closeAdd() {
    if (busy) return;
    setOpen(false);
  }

  // Focus the first field when the dialog opens, and close it on Escape.
  useEffect(() => {
    if (!open) return;
    firstFieldRef.current?.focus();
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  useEffect(() => {
    if (!pendingRemove) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setPendingRemove(null); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [pendingRemove]);

  async function add(e: FormEvent) {
    e.preventDefault();
    setAddError(null);
    setAddFieldErrors({});
    setBusy(true);
    try {
      await apiRequest("/api/v1/beneficiaries", {
        method: "POST", token,
        body: { name, nickname: nickname || undefined, accountNumber },
      });
      setOpen(false);
      await load();
    } catch (err) {
      setAddFieldErrors(fieldErrorsOf(err));
      setAddError(generalMessageOf(err, "Couldn't save that payee."));
    } finally {
      setBusy(false);
    }
  }

  function askRemove(payee: BeneficiaryResponse) {
    setRemoveError(null);
    setPendingRemove(payee);
  }

  function cancelRemove() {
    if (removeBusy) return;
    setPendingRemove(null);
  }

  async function confirmRemove() {
    if (!pendingRemove) return;
    setRemoveBusy(true);
    setRemoveError(null);
    try {
      await apiRequest(`/api/v1/beneficiaries/${pendingRemove.id}`, { method: "DELETE", token });
      setPayees((current) => current.filter((p) => p.id !== pendingRemove.id));
      setPendingRemove(null);
    } catch (err) {
      setRemoveError(err instanceof ApiError ? err.message : "Couldn't remove that payee.");
    } finally {
      setRemoveBusy(false);
    }
  }

  return (
    <div className="page">
      <header className="dash__head">
        <div>
          <h1 className="dash__heading">Payees</h1>
        </div>
        <div className="dash__headactions">
          <button className="btn btn--primary" onClick={openAdd}>Add Payee</button>
        </div>
      </header>

      <section className="table">
        <div className="table__bar">
          <h2>Saved Payees</h2>
          <span className="muted">{payees.length}</span>
        </div>
        {error && <p className="notice notice--error">{error}</p>}
        {loading ? (
          <p className="table__empty">Loading…</p>
        ) : payees.length === 0 ? (
          <p className="table__empty">No saved payees yet. Add your first one with the button above.</p>
        ) : (
          <ul className="payees">
            {payees.map((p) => (
              <li key={p.id} className="payee">
                <div>
                  <span className="payee__name">{p.nickname || p.name}</span>
                  {p.nickname && <span className="payee__legal">{p.name}</span>}
                  <span className="payee__num mono">{p.accountNumber}</span>
                </div>
                <div className="rowactions">
                  <Link className="iconbtn" to={`/transfer?payee=${p.id}`}
                        title="Send money" aria-label={`Send money to ${p.nickname || p.name}`}>
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
                         strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M22 2 11 13" />
                      <path d="M22 2 15 22l-4-9-9-4 20-7z" />
                    </svg>
                  </Link>
                  <button className="iconbtn iconbtn--danger" onClick={() => askRemove(p)}
                          title="Remove payee" aria-label={`Remove ${p.nickname || p.name}`}>
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
                         strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M3 6h18" />
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                      <path d="M10 11v6M14 11v6" />
                    </svg>
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {open && createPortal(
        <div className="modal" role="presentation" onClick={closeAdd}>
          <div className="modal__panel" role="dialog" aria-modal="true"
               aria-labelledby="add-payee-title" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal__title" id="add-payee-title">Add a Payee</h2>
            <p className="modal__sub">
              We don't check the account here. Make sure it's a real Haven Bank account and the name
              matches — a transfer will only go through if both are correct when you send money.
            </p>
            <form onSubmit={add} className="form" noValidate>
              <label className="field">
                <span className="field__label">Beneficiary name</span>
                <input ref={firstFieldRef} className="field__input" value={name}
                       aria-invalid={!!addFieldErrors.name || undefined}
                       onChange={(e) => setName(e.target.value)} required />
                {addFieldErrors.name
                  ? <span className="field__error" role="alert">{addFieldErrors.name}</span>
                  : <span className="field__hint">As it appears on their account.</span>}
              </label>
              <label className="field">
                <span className="field__label">Account number</span>
                <input className="field__input" value={accountNumber}
                       aria-invalid={!!addFieldErrors.accountNumber || undefined}
                       onChange={(e) => setAccountNumber(e.target.value)} required />
                {addFieldErrors.accountNumber &&
                  <span className="field__error" role="alert">{addFieldErrors.accountNumber}</span>}
              </label>
              <label className="field">
                <span className="field__label">Nickname (optional)</span>
                <input className="field__input" value={nickname}
                       aria-invalid={!!addFieldErrors.nickname || undefined}
                       onChange={(e) => setNickname(e.target.value)} />
                {addFieldErrors.nickname
                  ? <span className="field__error" role="alert">{addFieldErrors.nickname}</span>
                  : <span className="field__hint">What you'll see in your own lists, e.g. "Landlord".</span>}
              </label>
              {addError && <p className="notice notice--error">{addError}</p>}
              <div className="form__actions">
                <button className="btn btn--primary" disabled={busy}>{busy ? "Saving…" : "Save Payee"}</button>
                <button type="button" className="btn btn--ghost" onClick={closeAdd} disabled={busy}>Cancel</button>
              </div>
            </form>
          </div>
        </div>,
        document.body,
      )}

      {pendingRemove && createPortal(
        <div className="modal" role="presentation" onClick={cancelRemove}>
          <div className="modal__panel" role="dialog" aria-modal="true"
               aria-labelledby="remove-title" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal__title" id="remove-title">Remove Payee</h2>
            <p className="modal__sub">
              Are you sure you want to remove {pendingRemove.nickname || pendingRemove.name}
              ({pendingRemove.accountNumber})? This doesn't affect any past transfers.
            </p>
            {removeError && <p className="notice notice--error">{removeError}</p>}
            <div className="modal__actions">
              <button className="btn btn--danger" onClick={() => void confirmRemove()} disabled={removeBusy}>
                {removeBusy ? "Removing…" : "Remove"}
              </button>
              <button className="btn btn--ghost" onClick={cancelRemove} disabled={removeBusy}>Cancel</button>
            </div>
          </div>
        </div>,
        document.body,
      )}
    </div>
  );
}