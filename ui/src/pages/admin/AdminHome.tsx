import { Navigate } from "react-router-dom";
import { useRoles } from "../../auth/useRoles";

/** Land admins on Users, staff (read-only) on the audit trail. */
export function AdminHome() {
  const roles = useRoles();
  return <Navigate to={roles.includes("ADMIN") ? "/admin/users" : "/admin/audit"} replace />;
}
