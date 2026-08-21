import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ApiError, apiRequest } from "../api/client";
import { AuthCard } from "./Register";

type State = "verifying" | "ok" | "error";

/** Consumes the ?token from the verification email and activates the account. */
export function VerifyEmail() {
  const [params] = useSearchParams();
  const token = params.get("token");
  const [state, setState] = useState<State>("verifying");
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;
    if (!token) { setState("error"); return; }
    apiRequest("/api/v1/register/verify", { method: "POST", body: { token } })
      .then(() => setState("ok"))
      .catch((err) => { setState("error"); void (err instanceof ApiError); });
  }, [token]);

  return (
    <AuthCard title={state === "ok" ? "Email verified" : state === "error" ? "Link expired" : "Verifying…"}>
      {state === "verifying" && <p className="lede">Confirming your email address.</p>}
      {state === "ok" && (
        <>
          <p className="lede">Your account is active. You can sign in now.</p>
          <Link className="btn btn--primary" to="/">Sign In</Link>
        </>
      )}
      {state === "error" && (
        <>
          <p className="lede">This verification link is invalid or has expired. Request a new one from the sign-in page.</p>
          <Link className="btn btn--ghost" to="/register">Back to register</Link>
        </>
      )}
    </AuthCard>
  );
}
