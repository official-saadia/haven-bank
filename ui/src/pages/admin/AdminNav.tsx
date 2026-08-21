import { NavLink } from "react-router-dom";
import { useRoles } from "../../auth/useRoles";

/** Tabs across the admin area; Users/Roles/Policies are ADMIN, Audit is also STAFF. */
export function AdminNav() {
  const roles = useRoles();
  const isAdmin = roles.includes("ADMIN");
  return (
    <nav className="adminnav">
      {isAdmin && <NavLink to="/admin/users">Users</NavLink>}
      {isAdmin && <NavLink to="/admin/roles">Roles</NavLink>}
      <NavLink to="/admin/audit">Audit</NavLink>
      {isAdmin && <NavLink to="/admin/policies">Fees &amp; policy</NavLink>}
    </nav>
  );
}
