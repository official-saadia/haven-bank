import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "react-oidc-context";
import { ApiError, apiRequest, type UserProfile } from "../api/client";
import { Splash } from "../components/Splash";

/**
 * The account "passbook" - the signature surface. Identity is rendered on ledger hairlines with
 * monospaced values, echoing a bank passbook page. Data comes from GET /api/v1/me.
 */
export function Profile() {
  const auth = useAuth();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const token = auth.user?.access_token;
    if (!token) return;
    apiRequest<UserProfile>("/api/v1/me", { token })
      .then(setProfile)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Could not load your account"));
  }, [auth.user?.access_token]);

  if (error) return <p className="notice notice--error">{error}</p>;
  if (!profile) return <Splash label="Loading your account" />;

  return (
    <section className="dash">
      <header className="dash__head">
        <div>
          <h1 className="dash__heading">Profile</h1>
          <p className="dash__totalnote">Account details</p>
        </div>
        <div className="dash__headactions">
          <Link className="btn btn--primary" to="/change-password">Change Password</Link>
        </div>
      </header>

      <section className="passbook">
        <dl className="passbook__rows">
          <Row term="Name" value={profile.fullName} />
          <Row term="Email" value={profile.email} />
          <Row term="Status" value={profile.status.toLowerCase()} />
          <Row term="Verified" value={profile.emailVerified ? "yes" : "no"} />
          <Row term="Roles" value={profile.roles.join(", ") || "—"} />
          <Row term="Reference" value={profile.id} mono />
        </dl>
      </section>
    </section>
  );
}

function Row({ term, value, mono }: { term: string; value: string; mono?: boolean }) {
  return (
    <div className="passbook__row">
      <dt>{term}</dt>
      <dd className={mono ? "mono" : undefined}>{value}</dd>
    </div>
  );
}
