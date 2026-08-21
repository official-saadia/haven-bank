import { type ReactNode, useEffect } from "react";
import { useAuth } from "react-oidc-context";
import { Splash } from "../components/Splash";

/** Guards a route: sends unauthenticated visitors into the OAuth sign-in flow. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const auth = useAuth();

  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated && !auth.activeNavigator) {
      void auth.signinRedirect();
    }
  }, [auth.isLoading, auth.isAuthenticated, auth.activeNavigator, auth]);

  if (auth.isLoading) return <Splash label="Loading your account" />;
  if (!auth.isAuthenticated) return <Splash label="Taking you to sign in" />;
  return <>{children}</>;
}
