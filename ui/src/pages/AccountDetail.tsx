import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "react-oidc-context";
import {
  ApiError, apiRequest, idempotencyKey,
  type AccountResponse, type Page, type TransactionResponse,
} from "../api/client";
import { accountLabel, formatMoney } from "../lib/money";
import { formatDate } from "../lib/date";
import { Splash } from "../components/Splash";

const API_BASE = import.meta.env.VITE_API_BASE;
const TYPES = ["", "DEPOSIT", "WITHDRAWAL", "TRANSFER"];

/** A single account: balance, quick deposit/withdraw, filterable history, and CSV statement export. */
export function AccountDetail() {
  const { id } = useParams<{ id: string }>();
  const auth = useAuth();
  const token = auth.user?.access_token;
  const [account, setAccount] = useState<AccountResponse | null>(null);
  const [history, setHistory] = useState<TransactionResponse[]>([]);
  const [type, setType] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  // The deposit/withdraw overlay: which action is open, the entered amount, and its own in-flight
  // and error state so a failure shows inside the dialog rather than behind it.
  const [moveKind, setMoveKind] = useState<null | "deposit" | "withdraw">(null);
  const [amount, setAmount] = useState("");
  const [moveBusy, setMoveBusy] = useState(false);
  const [moveError, setMoveError] = useState<string | null>(null);
  const [moveFieldError, setMoveFieldError] = useState<string | undefined>(undefined);
  const amountRef = useRef<HTMLInputElement>(null);

  const load = useCallback(() => {
    if (!token || !id) return;
    apiRequest<AccountResponse>(`/api/v1/accounts/${id}`, { token }).then(setAccount).catch(() => {});
    const q = new URLSearchParams({ size: "25" });
    if (type) q.set("type", type);
    if (from) q.set("from", `${from}T00:00:00Z`);
    if (to) q.set("to", `${to}T23:59:59Z`);
    apiRequest<Page<TransactionResponse>>(`/api/v1/accounts/${id}/transactions?${q}`, { token })
      .then((p) => setHistory(p.content))
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load history"));
  }, [token, id, type, from, to]);

  useEffect(load, [load]);

  function openMove(kind: "deposit" | "withdraw") {
    setMoveKind(kind);
    setAmount("");
    setMoveError(null);
    setMoveFieldError(undefined);
  }

  function closeMove() {
    if (moveBusy) return;
    setMoveKind(null);
  }

  // Focus the amount field when the dialog opens, and close it on Escape.
  useEffect(() => {
    if (!moveKind) return;
    amountRef.current?.focus();
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setMoveKind(null); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [moveKind]);

  async function submitMove() {
    if (!moveKind || !token || !id) return;
    const value = Number(amount);
    if (!Number.isFinite(value) || value <= 0) {
      setMoveError("Enter an amount greater than zero.");
      return;
    }
    setMoveBusy(true);
    setMoveError(null);
    setMoveFieldError(undefined);
    try {
      await apiRequest(`/api/v1/accounts/${id}/${moveKind}`, {
        method: "POST", token, body: { amount: value },
        headers: { "Idempotency-Key": idempotencyKey() },
      });
      setMoveKind(null);
      load();
    } catch (e) {
      setMoveError(generalMessageOf(e, "Could not complete that."));
      setMoveFieldError(fieldErrorsOf(e).amount);
    } finally {
      setMoveBusy(false);
    }
  }

  async function downloadStatement() {
    if (!token || !id) return;
    const f = from ? `${from}T00:00:00Z` : new Date(Date.now() - 90 * 864e5).toISOString();
    const t = to ? `${to}T23:59:59Z` : new Date().toISOString();
    const res = await fetch(`${API_BASE}/api/v1/accounts/${id}/statement?from=${f}&to=${t}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const text = await res.text();
    const url = URL.createObjectURL(new Blob([text], { type: "text/csv" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = "statement.csv";
    a.click();
    URL.revokeObjectURL(url);
  }

  if (!account) return <Splash label="Loading account" />;

  return (
    <section className="dash">
      {/* The page is named before the account it describes: the title and the way out sit above
          the summary, so the balance reads as content rather than as the heading. */}
      <div className="dash__bar">
        <h1 className="dash__heading">Transaction History</h1>
        <Link to="/dashboard">Back</Link>
      </div>

      <header className="dash__head">
        <div>
          <p className="acctmeta">
            <span className="acctmeta__label">Account Type:</span>
            <span>{accountLabel(account.type)}</span>
          </p>
          {/* Masking exists to protect the number in logs and from counterparties. The owner
              reading their own account needs the real thing — it is how they get paid. */}
          <p className="acctmeta">
            <span className="acctmeta__label">Account Number:</span>
            <span className="mono" title="Account number"
                  aria-label={`Account number ${account.accountNumber}`}>
              {account.accountNumber}
            </span>
            <button type="button" className="iconbtn"
                    title="Copy account number"
                    aria-label={copied ? "Account number copied" : "Copy account number"}
                    onClick={() => void navigator.clipboard.writeText(account.accountNumber)
                      .then(() => setCopied(true))}>
              {copied ? (
                <svg viewBox="0 0 24 24" width="15" height="15" fill="none"
                     stroke="currentColor" strokeWidth="2" strokeLinecap="round"
                     strokeLinejoin="round" aria-hidden="true">
                  <path d="M20 6 9 17l-5-5" />
                </svg>
              ) : (
                <svg viewBox="0 0 24 24" width="15" height="15" fill="none"
                     stroke="currentColor" strokeWidth="2" strokeLinecap="round"
                     strokeLinejoin="round" aria-hidden="true">
                  <rect x="9" y="9" width="11" height="11" rx="2" />
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                </svg>
              )}
            </button>
          </p>
          <p className="dash__total">{formatMoney(account.balance, account.currency)}</p>
        </div>
        <div className="dash__headactions">
          <button className="btn btn--ghost" onClick={() => openMove("deposit")}>Deposit</button>
          <button className="btn btn--ghost" onClick={() => openMove("withdraw")}>Withdraw</button>
        </div>
      </header>

      {error && <p className="notice notice--error">{error}</p>}

      <div className="inlineform">
        <label className="filterfield">
          <span className="filterfield__label">Type</span>
          <select className="field__input" value={type} onChange={(e) => setType(e.target.value)}>
            {TYPES.map((t) => <option key={t} value={t}>{t === "" ? "All types" : t.toLowerCase()}</option>)}
          </select>
        </label>
        <label className="filterfield">
          <span className="filterfield__label">From</span>
          <input className="field__input" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label className="filterfield">
          <span className="filterfield__label">To</span>
          <input className="field__input" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
        <button className="btn btn--ghost" onClick={downloadStatement}>Download Statement</button>
      </div>

      <section className="ledger">
        <div className="ledger__cols" aria-hidden="true">
          <span>Date</span>
          <span>Description</span>
          <span className="ledger__cols-amt">Amount</span>
        </div>
        <div className="ledger__rows">
          {history.length === 0 && <p className="ledger__empty">No transactions for this filter.</p>}
          {history.map((t) => (
            <div className="ledger__row" key={t.id}>
              <span className="ledger__date">{formatDate(t.createdAt)}</span>
              <span className="ledger__desc">{t.type.toLowerCase()} · {t.reference}</span>
              <span className={"mono ledger__amt" + (t.direction === "CREDIT" ? " is-credit" : "")}>
                {t.direction === "CREDIT" ? "+" : "-"}{formatMoney(t.amount, account.currency)}
              </span>
            </div>
          ))}
        </div>
      </section>

      {moveKind && createPortal(
        <div className="modal" role="presentation" onClick={closeMove}>
          <div className="modal__panel" role="dialog" aria-modal="true"
               aria-labelledby="move-title" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal__title" id="move-title">
              {moveKind === "deposit" ? "Deposit Funds" : "Withdraw Funds"}
            </h2>
            <p className="modal__sub">
              {accountLabel(account.type)} account · {account.accountNumber}
            </p>
            <label className="field">
              <span className="field__label">Amount ({account.currency})</span>
              <input ref={amountRef} className="field__input" type="number"
                     inputMode="decimal" min="0" step="0.01" value={amount}
                     placeholder="0.00" aria-invalid={!!moveFieldError || undefined}
                     onChange={(e) => setAmount(e.target.value)}
                     onKeyDown={(e) => { if (e.key === "Enter") void submitMove(); }} />
              {moveFieldError && <span className="field__error" role="alert">{moveFieldError}</span>}
            </label>
            {moveError && <p className="notice notice--error">{moveError}</p>}
            <div className="modal__actions">
              <button className="btn btn--primary" onClick={() => void submitMove()} disabled={moveBusy}>
                {moveBusy ? "Working…" : moveKind === "deposit" ? "Deposit" : "Withdraw"}
              </button>
              <button className="btn btn--ghost" onClick={closeMove} disabled={moveBusy}>Cancel</button>
            </div>
          </div>
        </div>,
        document.body,
      )}
    </section>
  );
}