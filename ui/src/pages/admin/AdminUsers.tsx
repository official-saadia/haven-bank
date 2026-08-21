import { useCallback, useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { useAuth } from "react-oidc-context";
import {
  ApiError, apiRequest, type AdminUser, type Page, type RoleResponse,
} from "../../api/client";
import { AdminNav } from "./AdminNav";
import { Splash } from "../../components/Splash";
import { LockIcon, UnlockIcon, BanIcon } from "../../components/icons";

/** Admin user list: lifecycle actions and role assignment. */
export function AdminUsers() {
  const auth = useAuth();
  const token = auth.user?.access_token;
  const meId = auth.user?.profile.sub;   // current admin; may not edit own roles
  const [users, setUsers] = useState<AdminUser[] | null>(null);
  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  // Deactivation is permanent (-> CLOSED), so it goes through a confirm modal.
  const [pendingClose, setPendingClose] = useState<AdminUser | null>(null);
  const [closeBusy, setCloseBusy] = useState(false);
  const [closeError, setCloseError] = useState<string | null>(null);

  // Lock is reversible, but still confirmed so it isn't a stray click.
  const [pendingLock, setPendingLock] = useState<AdminUser | null>(null);
  const [lockBusy, setLockBusy] = useState(false);
  const [lockError, setLockError] = useState<string | null>(null);

  const [pendingUnlock, setPendingUnlock] = useState<AdminUser | null>(null);
  const [unlockBusy, setUnlockBusy] = useState(false);
  const [unlockError, setUnlockError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!token) return;
    apiRequest<Page<AdminUser>>("/api/v1/admin/users?size=50", { token })
      .then((p) => setUsers(p.content))
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load users"));
    apiRequest<RoleResponse[]>("/api/v1/admin/roles", { token }).then(setRoles).catch(() => {});
  }, [token]);

  useEffect(load, [load]);

  async function action(id: string, verb: string) {
    try {
      await apiRequest(`/api/v1/admin/users/${id}/${verb}`, { method: "POST", token });
      load();
    } catch (e) { setError(e instanceof ApiError ? e.message : "Action failed"); }
  }

  async function confirmClose() {
    if (!pendingClose) return;
    setCloseBusy(true); setCloseError(null);
    try {
      await apiRequest(`/api/v1/admin/users/${pendingClose.id}/deactivate`, { method: "POST", token });
      setPendingClose(null);
      load();
    } catch (e) {
      setCloseError(e instanceof ApiError ? e.message : "Could not close account");
    } finally { setCloseBusy(false); }
  }

  async function confirmUnlock() {
    if (!pendingUnlock) return;
    setUnlockBusy(true); setUnlockError(null);
    try {
      await apiRequest(`/api/v1/admin/users/${pendingUnlock.id}/unlock`, { method: "POST", token });
      setPendingUnlock(null);
      load();
    } catch (e) {
      setUnlockError(e instanceof ApiError ? e.message : "Could not unlock user");
    } finally { setUnlockBusy(false); }
  }

  async function confirmLock() {
    if (!pendingLock) return;
    setLockBusy(true); setLockError(null);
    try {
      await apiRequest(`/api/v1/admin/users/${pendingLock.id}/lock`, { method: "POST", token });
      setPendingLock(null);
      load();
    } catch (e) {
      setLockError(e instanceof ApiError ? e.message : "Could not lock user");
    } finally { setLockBusy(false); }
  }

  async function toggleRole(u: AdminUser, roleName: string) {
    const has = u.roles.includes(roleName);
    if (has && u.roles.length === 1) return;   // a user must keep at least one role
    const next = has ? u.roles.filter((r) => r !== roleName) : [...u.roles, roleName];
    const roleIds = roles.filter((r) => next.includes(r.name)).map((r) => r.id);
    try {
      await apiRequest(`/api/v1/admin/users/${u.id}/roles`, { method: "PUT", token, body: { roleIds } });
      load();
    } catch (e) { setError(e instanceof ApiError ? e.message : "Could not update roles"); }
  }

  // Roles may be edited only on a live account that isn't the current admin's own.
  function editable(u: AdminUser): boolean {
    return u.id !== meId && u.status !== "CLOSED";
  }
  // Server SoD rule mirrored: CUSTOMER can't coexist with STAFF/ADMIN, so a role is offered only
  // when the user already holds it (removable) or adding it wouldn't create that combination.
  function canAdd(u: AdminUser, roleName: string): boolean {
    if (roleName === "CUSTOMER") return !(u.roles.includes("ADMIN") || u.roles.includes("STAFF"));
    if (roleName === "ADMIN" || roleName === "STAFF") return !u.roles.includes("CUSTOMER");
    return true;
  }

  function renderRoleChips(u: AdminUser) {
    // Closed / own row: nothing is changeable, so the held roles are plain text, not buttons.
    if (!editable(u)) {
      const held = roles.filter((r) => u.roles.includes(r.name));
      return held.length
        ? <span className="chiptext">{held.map((r) => r.name).join(", ")}</span>
        : <span className="muted">—</span>;
    }
    // Editable: held + addable roles. Anything toggleable is a button; the sole remaining role
    // can't be removed, so it renders as text. A role-less (legacy) row shows addable buttons.
    const shown = roles.filter((r) => u.roles.includes(r.name) || canAdd(u, r.name));
    if (shown.length === 0) return <span className="muted">—</span>;
    return shown.map((r) => {
      const on = u.roles.includes(r.name);
      if (on && u.roles.length === 1) {
        return <span key={r.id} className="chiptext"
                     title="A user must keep at least one role">{r.name}</span>;
      }
      return <button key={r.id} className={"chip" + (on ? " chip--on" : "")}
                     onClick={() => toggleRole(u, r.name)}>{r.name}</button>;
    });
  }

  if (error) return <p className="notice notice--error">{error}</p>;
  if (!users) return <Splash label="Loading users" />;

  return (
    <section className="adminwrap">
      <h1 className="admin__title">Users</h1>
      <AdminNav />
      <div className="table table--users">
        <div className="table__head"><span>User</span><span>Status</span><span>Roles</span><span>Actions</span></div>
        {users.map((u) => (
          <div className="table__row" key={u.id}>
            <span>
              <strong>{u.fullName}</strong><br /><span className="muted mono">{u.email}</span>
            </span>
            <span><span className={"pill pill--" + u.status.toLowerCase()}>{u.status.toLowerCase()}</span></span>
            <span className="rolechips">
              {renderRoleChips(u)}
            </span>
            <span className="rowactions">
              {u.status === "LOCKED" &&
                <button className="iconbtn" title="Unlock" aria-label="Unlock user"
                        onClick={() => setPendingUnlock(u)}><UnlockIcon /></button>}
              {(u.status === "ACTIVE" || u.status === "PENDING_VERIFICATION") &&
                <button className="iconbtn" title="Lock" aria-label="Lock user"
                        onClick={() => setPendingLock(u)}><LockIcon /></button>}
              {u.status !== "CLOSED" &&
                <button className="iconbtn iconbtn--danger" title="Close account" aria-label="Close account"
                        onClick={() => setPendingClose(u)}><BanIcon /></button>}
            </span>
          </div>
        ))}
      </div>

      {pendingUnlock && createPortal(
        <div className="modal" role="presentation" onClick={() => !unlockBusy && setPendingUnlock(null)}>
          <div className="modal__panel" role="dialog" aria-modal="true"
               aria-labelledby="unlock-title" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal__title" id="unlock-title">Unlock User</h2>
            <p className="modal__sub">
              Unlock {pendingUnlock.fullName} ({pendingUnlock.email})? They'll be able to sign in again.
            </p>
            {unlockError && <p className="notice notice--error">{unlockError}</p>}
            <div className="modal__actions">
              <button className="btn btn--primary" onClick={() => void confirmUnlock()} disabled={unlockBusy}>
                {unlockBusy ? "Unlocking…" : "Unlock"}
              </button>
              <button className="btn btn--ghost" onClick={() => setPendingUnlock(null)} disabled={unlockBusy}>Cancel</button>
            </div>
          </div>
        </div>,
        document.body,
      )}

      {pendingLock && createPortal(
        <div className="modal" role="presentation" onClick={() => !lockBusy && setPendingLock(null)}>
          <div className="modal__panel" role="dialog" aria-modal="true"
               aria-labelledby="lock-title" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal__title" id="lock-title">Lock User</h2>
            <p className="modal__sub">
              Lock {pendingLock.fullName} ({pendingLock.email})? They won't be able to sign in
              until an admin unlocks the account.
            </p>
            {lockError && <p className="notice notice--error">{lockError}</p>}
            <div className="modal__actions">
              <button className="btn btn--danger" onClick={() => void confirmLock()} disabled={lockBusy}>
                {lockBusy ? "Locking…" : "Lock"}
              </button>
              <button className="btn btn--ghost" onClick={() => setPendingLock(null)} disabled={lockBusy}>Cancel</button>
            </div>
          </div>
        </div>,
        document.body,
      )}

      {pendingClose && createPortal(
        <div className="modal" role="presentation" onClick={() => !closeBusy && setPendingClose(null)}>
          <div className="modal__panel" role="dialog" aria-modal="true"
               aria-labelledby="close-title" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal__title" id="close-title">Close Account</h2>
            <p className="modal__sub">
              Permanently close {pendingClose.fullName}'s account ({pendingClose.email})?
              This is final — a closed account cannot be reopened or reactivated.
            </p>
            {closeError && <p className="notice notice--error">{closeError}</p>}
            <div className="modal__actions">
              <button className="btn btn--danger" onClick={() => void confirmClose()} disabled={closeBusy}>
                {closeBusy ? "Closing…" : "Close Account"}
              </button>
              <button className="btn btn--ghost" onClick={() => setPendingClose(null)} disabled={closeBusy}>Cancel</button>
            </div>
          </div>
        </div>,
        document.body,
      )}
    </section>
  );
}