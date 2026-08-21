import { type FormEvent, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "react-oidc-context";
import { ApiError, apiRequest, fieldErrorsOf, generalMessageOf, idempotencyKey,
         type AccountResponse, type BeneficiaryResponse } from "../api/client";
import { accountLabel, formatMoney } from "../lib/money";
import { AuthCard, Field } from "./Register";

/** Move money to another account number. Handles the step-up OTP challenge for large transfers. */
export function Transfer() {
  const auth = useAuth();
  const token = auth.user?.access_token;
  const [params] = useSearchParams();
  // "Send" on the payees page arrives here with ?payee=<id>, so a cancel returns there rather than
  // dumping every cancel on the dashboard regardless of origin.
  const cancelTo = params.has("payee") ? "/beneficiaries" : "/dashboard";
  const [accounts, setAccounts] = useState<AccountResponse[]>([]);
  const [payees, setPayees] = useState<BeneficiaryResponse[]>([]);
  const [payeeId, setPayeeId] = useState("");
  const [payeesFailed, setPayeesFailed] = useState(false);
  const [savePayee, setSavePayee] = useState(false);
  const [sourceAccountId, setSource] = useState("");
  const [destinationAccountNumber, setDest] = useState("");
  const [beneficiaryName, setBeneficiary] = useState("");
  const [amount, setAmount] = useState("");
  const [otp, setOtp] = useState("");
  const [needsOtp, setNeedsOtp] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!token) return;
    apiRequest<AccountResponse[]>("/api/v1/accounts", { token }).then((a) => {
      setAccounts(a);
      if (a[0]) setSource(a[0].id);
    }).catch(() => {});

    apiRequest<BeneficiaryResponse[]>("/api/v1/beneficiaries", { token }).then((list) => {
      setPayees(list);
      // Arriving from "Send" on the payees page: preselect, but the fields stay editable.
      const requested = params.get("payee");
      const match = requested ? list.find((p) => p.id === requested) : undefined;
      if (match) applyPayee(match);
    }).catch(() => setPayeesFailed(true));
    // params is read once on mount; re-running on every query change would fight the user's edits.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  function applyPayee(payee: BeneficiaryResponse) {
    setPayeeId(payee.id);
    setDest(payee.accountNumber);
    setBeneficiary(payee.name);
    setSavePayee(false);
  }

  function onPayeeChange(id: string) {
    const match = payees.find((p) => p.id === id);
    if (match) {
      applyPayee(match);
    } else {
      setPayeeId("");
      setDest("");
      setBeneficiary("");
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setBusy(true);
    try {
      await apiRequest("/api/v1/transfers", {
        method: "POST", token,
        body: { sourceAccountId, destinationAccountNumber, beneficiaryName, amount: Number(amount),
                otp: otp || undefined },
        headers: { "Idempotency-Key": idempotencyKey() },
      });
      // Best-effort and deliberately after the transfer: a failure to save an address-book entry
      // must never surface as a failed transfer, because the money has already moved.
      if (savePayee && !payeeId) {
        try {
          await apiRequest("/api/v1/beneficiaries", {
            method: "POST", token,
            body: { name: beneficiaryName, accountNumber: destinationAccountNumber },
          });
        } catch { /* already saved, or rate limited - not worth interrupting the receipt */ }
      }
      setDone(true);
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) {
        setNeedsOtp(true);
        setError("We've emailed you a code to confirm this transfer. Enter it below.");
      } else {
        setFieldErrors(fieldErrorsOf(e));
        setError(generalMessageOf(e, "Transfer failed. Please try again."));
      }
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <AuthCard title="Transfer sent">
        <p className="lede">Your transfer of {formatMoney(Number(amount), "GBP")} is complete.</p>
        <Link className="btn btn--primary" to="/dashboard">Back to accounts</Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Move Money" wide>
      <form onSubmit={submit} className="form" noValidate>
        <label className="field">
          <span className="field__label">From</span>
          <select className="field__input" value={sourceAccountId} onChange={(e) => setSource(e.target.value)} required>
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {accountLabel(a.type)} ••{a.accountNumber.slice(-4)} — {formatMoney(a.balance, a.currency)}
              </option>
            ))}
          </select>
        </label>
        {payees.length > 0 ? (
          <label className="field">
            <span className="field__label">Saved payee</span>
            <select className="field__input" value={payeeId}
                    onChange={(e) => onPayeeChange(e.target.value)}>
              <option value="">Enter details manually</option>
              {payees.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nickname || p.name} — ••{p.accountNumber.slice(-4)}
                </option>
              ))}
            </select>
            <span className="field__hint">
              Prefills the fields below. They stay editable, and the transfer is checked either way.
            </span>
          </label>
        ) : (
          // Silence here would be ambiguous: no payees and a failed lookup look identical otherwise.
          <p className="field__hint">
            {payeesFailed
              ? "Couldn't load your saved payees — enter the details below."
              : <>No saved payees yet. <Link to="/beneficiaries">Add one</Link> to pick from a list next time.</>}
          </p>
        )}
        <Field label="To account number" value={destinationAccountNumber} onChange={setDest}
               error={fieldErrors.destinationAccountNumber} />
        <Field label="Beneficiary name" value={beneficiaryName} onChange={setBeneficiary}
               error={fieldErrors.beneficiaryName} />
        {!payeeId && destinationAccountNumber.length > 0 && (
          <label className="checkfield">
            <input type="checkbox" checked={savePayee}
                   onChange={(e) => setSavePayee(e.target.checked)} />
            <span>Save this payee for next time</span>
          </label>
        )}
        <Field label="Amount" type="number" value={amount} onChange={setAmount}
               error={fieldErrors.amount} />
        {needsOtp && <Field label="Confirmation code" value={otp} onChange={setOtp}
                            hint="Sent to your email." error={fieldErrors.otp} />}
        {error && <p className={"notice " + (needsOtp ? "notice--info" : "notice--error")}>{error}</p>}
        <div className="form__actions">
          <button className="btn btn--primary" disabled={busy}>{busy ? "Sending…" : "Transfer"}</button>
          <Link to={cancelTo} className="btn btn--ghost">Cancel</Link>
        </div>
      </form>
    </AuthCard>
  );
}