import { type FormEvent, useCallback, useEffect, useState } from "react";
import { useAuth } from "react-oidc-context";
import {
  ApiError, apiRequest, type FeeScheduleResponse, type PolicyResponse,
} from "../../api/client";
import { AdminNav } from "./AdminNav";
import { Splash } from "../../components/Splash";

/** Versioned fee-schedule and policy administration. New versions supersede current ones. */
export function AdminPolicies() {
  const token = useAuth().user?.access_token;
  const [fees, setFees] = useState<FeeScheduleResponse[] | null>(null);
  const [policies, setPolicies] = useState<PolicyResponse[]>([]);
  const [feeFlat, setFeeFlat] = useState("0.00");
  const [feePercent, setFeePercent] = useState("0.0000");
  const [policyKey, setPolicyKey] = useState("DAILY_LIMIT");
  const [policyValue, setPolicyValue] = useState("10000.00");
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!token) return;
    apiRequest<FeeScheduleResponse[]>("/api/v1/admin/fee-schedules", { token })
      .then(setFees)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load"));
    apiRequest<PolicyResponse[]>("/api/v1/admin/policies", { token }).then(setPolicies).catch(() => {});
  }, [token]);

  useEffect(load, [load]);

  async function newFee(e: FormEvent) {
    e.preventDefault();
    try {
      await apiRequest("/api/v1/admin/fee-schedules", { method: "POST", token,
        body: { appliesTo: "TRANSFER", feeFlat: Number(feeFlat), feePercent: Number(feePercent) } });
      load();
    } catch (e) { setError(e instanceof ApiError ? e.message : "Could not save fee"); }
  }

  async function newPolicy(e: FormEvent) {
    e.preventDefault();
    try {
      await apiRequest("/api/v1/admin/policies", { method: "POST", token,
        body: { policyKey, scope: "GLOBAL", value: Number(policyValue) } });
      load();
    } catch (e) { setError(e instanceof ApiError ? e.message : "Could not save policy"); }
  }

  if (error) return <p className="notice notice--error">{error}</p>;
  if (!fees) return <Splash label="Loading" />;

  const active = (to: string | null) => (to === null ? " pill--active" : "");

  return (
    <section className="adminwrap">
      <h1 className="admin__title">Fees &amp; Policy</h1>
      <AdminNav />

      <h2 className="admin__sub">Transfer Fees</h2>
      <form onSubmit={newFee} className="inlineform">
        <input className="field__input" value={feeFlat} onChange={(e) => setFeeFlat(e.target.value)} placeholder="Flat" />
        <input className="field__input" value={feePercent} onChange={(e) => setFeePercent(e.target.value)} placeholder="Percent" />
        <button className="btn btn--primary">New version</button>
      </form>
      <div className="table table--fees">
        <div className="table__head"><span>Applies to</span><span>Flat</span><span>Percent</span><span>From</span><span>Status</span></div>
        {fees.map((f) => (
          <div className="table__row" key={f.id}>
            <span className="mono">{f.appliesTo}</span>
            <span className="mono">{f.feeFlat.toFixed(2)}</span>
            <span className="mono">{(f.feePercent * 100).toFixed(2)}%</span>
            <span className="mono muted">{f.effectiveFrom.slice(0, 10)}</span>
            <span><span className={"pill" + (f.effectiveTo === null ? " pill--active" : "")}>{f.effectiveTo === null ? "active" : "superseded"}</span></span>
          </div>
        ))}
      </div>

      <h2 className="admin__sub">Thresholds</h2>
      <form onSubmit={newPolicy} className="inlineform">
        <select className="field__input" value={policyKey} onChange={(e) => setPolicyKey(e.target.value)}>
          <option value="DAILY_LIMIT">Daily limit</option>
          <option value="STEP_UP_THRESHOLD">Step-up threshold</option>
        </select>
        <input className="field__input" value={policyValue} onChange={(e) => setPolicyValue(e.target.value)} placeholder="Value" />
        <button className="btn btn--primary">New version</button>
      </form>
      <div className="table table--fees">
        <div className="table__head"><span>Key</span><span>Scope</span><span>Value</span><span>From</span><span>Status</span></div>
        {policies.map((p) => (
          <div className="table__row" key={p.id}>
            <span className="mono">{p.policyKey}</span>
            <span className="mono muted">{p.scope}</span>
            <span className="mono">{p.value.toFixed(2)}</span>
            <span className="mono muted">{p.effectiveFrom.slice(0, 10)}</span>
            <span><span className={"pill" + active(p.effectiveTo)}>{p.effectiveTo === null ? "active" : "superseded"}</span></span>
          </div>
        ))}
      </div>
    </section>
  );
}
