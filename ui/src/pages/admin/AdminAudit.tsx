import { useCallback, useEffect, useState } from "react";
import { useAuth } from "react-oidc-context";
import { ApiError, apiRequest, type AuditRecord, type Page } from "../../api/client";
import { AdminNav } from "./AdminNav";
import { Pager } from "../../components/Pager";
import { Splash } from "../../components/Splash";

/** Read-only audit trail with an action filter and paging. */
export function AdminAudit() {
  const token = useAuth().user?.access_token;
  const [records, setRecords] = useState<AuditRecord[] | null>(null);
  const [action, setAction] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!token) return;
    const q = new URLSearchParams({ page: String(page), size: "25" });
    if (action) q.set("action", action);
    apiRequest<Page<AuditRecord>>(`/api/v1/admin/audit?${q}`, { token })
      .then((p) => { setRecords(p.content); setTotalPages(p.totalPages || 1); })
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load audit trail"));
  }, [token, action, page]);

  useEffect(load, [load]);

  if (error) return <p className="notice notice--error">{error}</p>;

  return (
    <section className="adminwrap">
      <h1 className="admin__title">Audit Trail</h1>
      <AdminNav />
      <div className="inlineform">
        <input className="field__input" placeholder="Filter by action (e.g. LOGIN_SUCCESS)"
               value={action} onChange={(e) => { setPage(0); setAction(e.target.value.toUpperCase()); }} />
      </div>
      {!records ? <Splash label="Loading" /> : (
        <>
          <div className="table table--audit">
            <div className="table__head"><span>Time</span><span>Actor</span><span>Action</span><span>Outcome</span><span>Detail</span><span>IP</span><span>Correlation</span></div>
            {records.map((r) => (
              <div className="table__row" key={r.id}>
                <span className="mono muted">{r.createdAt.replace("T", " ").slice(0, 19)}</span>
                <span>{r.actor ?? "system"}</span>
                <span className="mono">{r.action}</span>
                <span><span className={"pill pill--" + r.outcome.toLowerCase()}>{r.outcome.toLowerCase()}</span></span>
                <span>{r.detail}</span>
                <span className="mono muted">{r.sourceIp ?? "—"}</span>
                <span className="mono muted">{r.correlationId?.slice(0, 8)}</span>
              </div>
            ))}
          </div>
          <Pager page={page} totalPages={totalPages} onChange={setPage} />
        </>
      )}
    </section>
  );
}