import { useAuth } from "react-oidc-context";

/** Read the roles claim from the ID token (added by the auth server's token customizer). */
export function useRoles(): string[] {
  const auth = useAuth();
  const claim = (auth.user?.profile as { roles?: unknown } | undefined)?.roles;
  return Array.isArray(claim) ? (claim as string[]) : [];
}
