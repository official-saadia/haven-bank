import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "react-oidc-context";
import { ApiError, apiRequest, type PreferenceView } from "../api/client";
import { Splash } from "../components/Splash";

const LABELS: Record<string, string> = {
  ACCOUNT_CREATED: "Account opened confirmations",
  MONEY_MOVEMENT: "Transaction receipts",
};

/** Manage convenience notifications. Security alerts are mandatory and not shown here. */
export function Preferences() {
  const auth = useAuth();
  const token = auth.user?.access_token;
  const [prefs, setPrefs] = useState<PreferenceView[] | null>(null);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!token) return;
    apiRequest<PreferenceView[]>("/api/v1/me/notification-preferences", { token })
      .then(setPrefs)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load preferences"));
  }, [token]);

  useEffect(load, [load]);

  async function toggle(type: string) {
    if (!prefs || !token) return;
    const next = prefs.map((p) => (p.type === type ? { ...p, enabled: !p.enabled } : p));
    setPrefs(next);
    setSaved(false);
    try {
      const updated = await apiRequest<PreferenceView[]>("/api/v1/me/notification-preferences", {
        method: "PUT", token, body: { preferences: next },
      });
      setPrefs(updated);
      setSaved(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not save");
    }
  }

  if (error) return <p className="notice notice--error">{error}</p>;
  if (!prefs) return <Splash label="Loading preferences" />;

  return (
    <section className="authcard authcard--wide">
      <h1 className="authcard__title">Notifications</h1>
      <p className="lede">Security alerts always stay on. Choose which convenience emails you receive.</p>
      <ul className="prefs">
        {prefs.map((p) => (
          <li className="prefs__row" key={p.type}>
            <span>{LABELS[p.type] ?? p.type}</span>
            <button
              className={"toggle" + (p.enabled ? " toggle--on" : "")}
              role="switch" aria-checked={p.enabled}
              onClick={() => toggle(p.type)}
            >
              <span className="toggle__knob" />
            </button>
          </li>
        ))}
      </ul>
      {saved && <p className="notice notice--info">Preferences saved.</p>}
      <p className="cardfoot"><Link to="/dashboard">Back to accounts</Link></p>
    </section>
  );
}
