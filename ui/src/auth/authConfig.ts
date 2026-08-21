import { WebStorageStateStore, InMemoryWebStorage } from "oidc-client-ts";
import type { AuthProviderProps } from "react-oidc-context";

/**
 * OIDC configuration for the Authorization Code + PKCE flow (oidc-client-ts performs PKCE
 * automatically for the code flow).
 *
 * Token storage: access and refresh tokens are held in an in-memory store, never in localStorage or
 * sessionStorage (NFR-1.4). The transient PKCE state (code_verifier) still uses the default
 * sessionStorage-backed stateStore, because it must survive the full-page redirect to the auth
 * server and back; it is short-lived and consumed at the callback.
 *
 * Trade-off: tokens are lost on a hard refresh, so the app relies on the auth-server session for
 * silent re-authentication. For production the recommended pattern is a Backend-for-Frontend that
 * keeps tokens server-side and hands the browser only a session cookie.
 */
export const oidcConfig: AuthProviderProps = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY,
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID,
  redirect_uri: import.meta.env.VITE_OIDC_REDIRECT_URI,
  post_logout_redirect_uri: import.meta.env.VITE_OIDC_POST_LOGOUT_URI,
  scope: import.meta.env.VITE_OIDC_SCOPE,
  response_type: "code",
  automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
  onSigninCallback: () => {
    // Strip the ?code&state query from the URL after the exchange completes.
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};
