import { useEffect, useRef } from "react";
import { useAuth } from "react-oidc-context";
import { Link, Navigate, useSearchParams } from "react-router-dom";

/**
 * The entry point. There is no marketing landing page: an unauthenticated visitor is sent straight
 * into the OAuth 2.1 authorization-code flow, so the first thing they see is the sign-in form.
 *
 * <p>That form is rendered by the authorization server, not here, and deliberately so. Collecting a
 * password in the SPA and posting it to the API would be the resource-owner password-credentials
 * grant - removed in OAuth 2.1, and contrary to FR-1.4. The client must never see the credential.
 *
 * <p>Two states stop the redirect, because each would otherwise loop: an explicit sign-out landing
 * back here, and a failed sign-in whose error has nowhere else to surface.
 */
export function Home() {
  const auth = useAuth();
  const [params] = useSearchParams();
  const signedOut = params.has("signedout");
  const started = useRef(false);

  const gated = auth.isLoading || auth.isAuthenticated || !!auth.error
    || !!auth.activeNavigator || signedOut;

  useEffect(() => {
    // The ref guards against StrictMode's double effect invocation and against re-renders that
    // occur while the redirect is in flight - either would start a second authorize request.
    if (gated || started.current) return;
    started.current = true;
    void auth.signinRedirect();
  }, [gated, auth]);

  if (auth.isAuthenticated) return <Navigate to="/dashboard" replace />;

  function signIn() {
    started.current = true;
    void auth.signinRedirect();
  }

  if (auth.error) {
    return (
      <section className="hero hero--gate">
        <p className="hero__mark">Haven Bank</p>
        <p className="notice notice--error">Sign-in failed: {auth.error.message}</p>
        <div className="actions actions--stacked">
          <button className="btn btn--primary btn--lg" onClick={signIn}>Try Again</button>
          <Link className="btn btn--ghost btn--lg" to="/register">Open an Account</Link>
        </div>
      </section>
    );
  }

  if (signedOut) {
    return (
      <section className="hero hero--gate">
        <p className="hero__mark">Haven Bank</p>
        <p className="gate__note">You've been signed out.</p>
        <div className="actions actions--stacked">
          <button className="btn btn--primary btn--lg" onClick={signIn}>Sign In</button>
          <Link className="btn btn--ghost btn--lg" to="/register">Open an Account</Link>
        </div>
      </section>
    );
  }

  return (
    <section className="hero hero--gate">
      <p className="hero__mark">Haven Bank</p>
      <p className="gate__note">Taking you to sign in…</p>
    </section>
  );
}
