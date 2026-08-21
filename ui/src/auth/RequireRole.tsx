import { type ReactNode } from "react";
import { useRoles } from "./useRoles";

/** Renders children only if the user holds any of the given roles; otherwise a polite denial. */
export function RequireRole({ anyOf, children }: { anyOf: string[]; children: ReactNode }) {
  const roles = useRoles();
  const allowed = anyOf.some((r) => roles.includes(r));
  if (!allowed) {
    return <p className="notice notice--error">You don't have access to this area.</p>;
  }
  return <>{children}</>;
}
