import { type FormEvent, useCallback, useEffect, useState } from "react";
import { useAuth } from "react-oidc-context";
import {
  ApiError, apiRequest, type PermissionResponse, type RoleResponse,
} from "../../api/client";
import { AdminNav } from "./AdminNav";
import { Splash } from "../../components/Splash";
import { TrashIcon } from "../../components/icons";

/** Structural roles the app depends on: not deletable, permissions fixed. Mirrors the server rule. */
const SYSTEM_ROLES = ["CUSTOMER", "STAFF", "ADMIN"];

/** Admin roles: create roles and manage their permissions. */
export function AdminRoles() {
  const token = useAuth().user?.access_token;
  const [roles, setRoles] = useState<RoleResponse[] | null>(null);
  const [permissions, setPermissions] = useState<PermissionResponse[]>([]);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!token) return;
    apiRequest<RoleResponse[]>("/api/v1/admin/roles", { token })
      .then(setRoles)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load roles"));
    apiRequest<PermissionResponse[]>("/api/v1/admin/permissions", { token }).then(setPermissions).catch(() => {});
  }, [token]);

  useEffect(load, [load]);

  async function create(e: FormEvent) {
    e.preventDefault();
    try {
      await apiRequest("/api/v1/admin/roles", { method: "POST", token, body: { name, description } });
      setName(""); setDescription(""); load();
    } catch (e) { setError(e instanceof ApiError ? e.message : "Could not create role"); }
  }

  async function togglePermission(role: RoleResponse, permName: string) {
    const has = role.permissions.includes(permName);
    const next = has ? role.permissions.filter((p) => p !== permName) : [...role.permissions, permName];
    const permissionIds = permissions.filter((p) => next.includes(p.name)).map((p) => p.id);
    try {
      await apiRequest(`/api/v1/admin/roles/${role.id}/permissions`, { method: "PUT", token, body: { permissionIds } });
      load();
    } catch (e) { setError(e instanceof ApiError ? e.message : "Could not update permissions"); }
  }

  async function remove(id: string) {
    try { await apiRequest(`/api/v1/admin/roles/${id}`, { method: "DELETE", token }); load(); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Could not delete (still assigned?)"); }
  }

  if (error) return <p className="notice notice--error">{error}</p>;
  if (!roles) return <Splash label="Loading roles" />;

  return (
    <section className="adminwrap">
      <h1 className="admin__title">Roles</h1>
      <AdminNav />
      <form onSubmit={create} className="inlineform">
        <input className="field__input" placeholder="New role name" value={name}
               onChange={(e) => setName(e.target.value)} required />
        <input className="field__input" placeholder="Description" value={description}
               onChange={(e) => setDescription(e.target.value)} />
        <button className="btn btn--primary">Create</button>
      </form>
      <div className="table table--roles">
        <div className="table__head table__head--roles"><span>Role</span><span>Permissions</span><span>Actions</span></div>
        {roles.map((r) => (
          <div className="table__row table__row--roles" key={r.id}>
            <span>
              <strong>{r.name}</strong>
              {r.description && <><br /><span className="muted">{r.description}</span></>}
            </span>
            <span className="rolechips">
              {SYSTEM_ROLES.includes(r.name)
                ? (() => {
                    const held = permissions.filter((p) => r.permissions.includes(p.name));
                    return held.length
                      ? <span className="chiptext">{held.map((p) => p.name).join(", ")}</span>
                      : <span className="muted">—</span>;
                  })()
                : permissions.map((p) => (
                    <button key={p.id}
                            className={"chip" + (r.permissions.includes(p.name) ? " chip--on" : "")}
                            onClick={() => togglePermission(r, p.name)}>{p.name}</button>
                  ))}
            </span>
            <span className="rowactions">
              {SYSTEM_ROLES.includes(r.name)
                ? <span className="muted">System role</span>
                : <button className="iconbtn iconbtn--danger" title="Delete role" aria-label="Delete role"
                          onClick={() => remove(r.id)}><TrashIcon /></button>}
            </span>
          </div>
        ))}
      </div>
    </section>
  );
}