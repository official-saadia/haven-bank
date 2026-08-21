import { NavLink } from "react-router-dom";

/** Static design preview of the admin Users screen (mock data, no backend) - for review/screenshots. */
const users = [
  { name: "Alice Nkemdirim", email: "alice@example.com", status: "active", roles: ["CUSTOMER"] },
  { name: "Bhavin Patel", email: "bhavin@example.com", status: "active", roles: ["CUSTOMER", "STAFF"] },
  { name: "Chen Wei", email: "chen@example.com", status: "locked", roles: ["CUSTOMER"] },
  { name: "Dara O'Brien", email: "dara@example.com", status: "active", roles: ["ADMIN"] },
];
const allRoles = ["CUSTOMER", "STAFF", "ADMIN"];

export function AdminPreview() {
  return (
    <section className="adminwrap">
      <h1 className="admin__title">Users</h1>
      <nav className="adminnav">
        <NavLink to="/admin-preview" className="active">Users</NavLink>
        <a>Roles</a><a>Audit</a><a>Fees &amp; policy</a>
      </nav>
      <div className="table table--users">
        <div className="table__head"><span>User</span><span>Status</span><span>Roles</span><span>Actions</span></div>
        {users.map((u) => (
          <div className="table__row" key={u.email}>
            <span><strong>{u.name}</strong><br /><span className="muted mono">{u.email}</span></span>
            <span><span className={"pill pill--" + u.status}>{u.status}</span></span>
            <span className="rolechips">
              {allRoles.map((r) => (
                <span key={r} className={"chip" + (u.roles.includes(r) ? " chip--on" : "")}>{r}</span>
              ))}
            </span>
            <span className="rowactions">
              <span className="linklike">{u.status === "locked" ? "Unlock" : "Lock"}</span>
              <span className="linklike danger">Deactivate</span>
            </span>
          </div>
        ))}
      </div>
    </section>
  );
}
