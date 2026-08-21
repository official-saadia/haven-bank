import { useEffect } from "react";
import { useAuth } from "react-oidc-context";
import { useNavigate } from "react-router-dom";
import { Splash } from "../components/Splash";

/** Lands after the auth server redirects back; oidc-client-ts exchanges the code, then we continue. */
export function Callback() {
  const auth = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (auth.isAuthenticated) navigate("/dashboard", { replace: true });
  }, [auth.isAuthenticated, navigate]);

  if (auth.error) {
    // A stale or missing PKCE state is recoverable - the user simply needs a fresh authorization
    // request - so offer that rather than dead-ending. Everything else gets the raw message, since
    // bouncing silently to the landing page hides the cause and looks like "never signed in".
    const staleState = /matching state|No state in response|state not found/i.test(auth.error.message);

    return (
      <div className="authcard">
        <h1 className="authcard__title">Sign-in could not be completed</h1>
        {staleState ? (
          <p className="notice notice--info">
            This sign-in link has expired or was already used. Start again to get a fresh one.
          </p>
        ) : (
          <p className="notice notice--error">{auth.error.message}</p>
        )}
        <button
          className="btn btn--primary"
          onClick={() => {
            // Drop any half-finished attempt before starting a clean one.
            void auth.removeUser();
            void auth.signinRedirect();
          }}
        >
          Start sign-in again
        </button>
        <p className="cardfoot">
          <a href="/">Back to start</a>
        </p>
      </div>
    );
  }

  return <Splash label="Completing sign-in" />;
}
