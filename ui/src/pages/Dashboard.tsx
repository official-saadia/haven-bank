import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "react-oidc-context";
import { ApiError, apiRequest, idempotencyKey, type AccountResponse } from "../api/client";
import { accountLabel, formatMoney } from "../lib/money";
import { Splash } from "../components/Splash";

/** Account overview: total balance, account cards, and a route into money movement. */
export function Dashboard() {
  const auth = useAuth();
  const navigate = useNavigate();
  const token = auth.user?.access_token;
  const [accounts, setAccounts] = useState<AccountResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  // The open-account confirmation: whether it's showing, the chosen type, and its own busy/error.
  const [opening, setOpening] = useState(false);
  const [newType, setNewType] = useState("CHECKING");
  const [openBusy, setOpenBusy] = useState(false);
  const [openError, setOpenError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!token) return;
    apiRequest<AccountResponse[]>("/api/v1/accounts", { token })
      .then(setAccounts)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load your accounts"));
  }, [token]);

  useEffect(load, [load]);

  function startOpen() {
    setNewType("CHECKING");
    setOpenError(null);
    setOpening(true);
  }

  function cancelOpen() {
    if (openBusy) return;
    setOpening(false);
  }

  useEffect(() => {
    if (!opening) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setOpening(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [opening]);

  async function confirmOpen() {
    if (!token) return;
    setOpenBusy(true);
    setOpenError(null);
    try {
      await apiRequest("/api/v1/accounts", {
        method: "POST", token, body: { type: newType, currency: "GBP" },
        // Guards against a double-click or a retried request creating two accounts.
        headers: { "Idempotency-Key": idempotencyKey() },
      });
      setOpening(false);
      load();
    } catch (e) {
      setOpenError(e instanceof ApiError ? e.userMessage : "Could not open an account.");
    } finally {
      setOpenBusy(false);
    }
  }

  if (error) return <p className="notice notice--error">{error}</p>;
  if (!accounts) return <Splash label="Loading your accounts" />;

  const currency = accounts[0]?.currency ?? "GBP";
  const total = accounts.reduce((sum, a) => sum + a.balance, 0);

  if (accounts.length === 0) {
    return (
      <section className="authcard">
        <h1 className="authcard__title">Welcome</h1>
        <p className="lede">You don't have any accounts yet. Open your first one to get started.</p>
        <button className="btn btn--primary" onClick={startOpen}>Open a Current Account</button>
      </section>
    );
  }

  return (
    <section className="dash">
      <header className="dash__head">
        <div>
          <h1 className="dash__heading">Accounts</h1>
          <p className="dash__total">{formatMoney(total, currency)}</p>
          <p className="dash__totalnote">Total across {accounts.length} account{accounts.length > 1 ? "s" : ""}</p>
        </div>
        <div className="dash__headactions">
          <button className="btn btn--ghost" onClick={startOpen}>Open Another Account</button>
          <button className="btn btn--primary" onClick={() => navigate("/transfer")}>Move Money</button>
        </div>
      </header>

      <div className="accounts">
        {accounts.map((a) => (
          <Link key={a.id} to={`/accounts/${a.id}`} className="acct acct--link">
            <div className="acct__top">
              <span className="acct__name">{accountLabel(a.type)}</span>
              <span className="acct__kind">{a.status.toLowerCase()}</span>
            </div>
            <span className="acct__num mono">••{a.accountNumber.slice(-4)}</span>
            <span className="acct__bal mono">{formatMoney(a.balance, a.currency)}</span>
          </Link>
        ))}
      </div>

      {opening && createPortal(
        <div className="modal" role="presentation" onClick={cancelOpen}>
          <div className="modal__panel" role="dialog" aria-modal="true"
               aria-labelledby="open-title" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal__title" id="open-title">Open an Account</h2>
            <p className="modal__sub">
              This opens a new account in your name straight away. You can move money into it once
              it's open.
            </p>
            <label className="field">
              <span className="field__label">Account type</span>
              <select className="field__input" value={newType}
                      onChange={(e) => setNewType(e.target.value)}>
                <option value="CHECKING">Current account</option>
                <option value="SAVINGS">Savings account</option>
              </select>
            </label>
            {openError && <p className="notice notice--error">{openError}</p>}
            <div className="modal__actions">
              <button className="btn btn--primary" onClick={() => void confirmOpen()} disabled={openBusy}>
                {openBusy ? "Opening…" : "Open Account"}
              </button>
              <button className="btn btn--ghost" onClick={cancelOpen} disabled={openBusy}>Cancel</button>
            </div>
          </div>
        </div>,
        document.body,
      )}
    </section>
  );
}