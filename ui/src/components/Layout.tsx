import { type ReactNode } from "react";
import { Link, NavLink, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "react-oidc-context";
import { useRoles } from "../auth/useRoles";
import { apiRequest } from "../api/client";

/**
 * App chrome: an obsidian rail on the left carrying the wordmark and navigation, with the
 * working surface — forms, tables, panels — on a lighter field to its right.
 */
export function Layout({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const roles = useRoles();
  const { pathname } = useLocation();
  const isStaff = roles.includes("ADMIN") || roles.includes("STAFF");

  async function signOut() {
    const token = auth.user?.access_token;
    if (token) {
      // Best-effort: denylist this access token server-side before ending the session.
      try { await apiRequest("/api/v1/auth/logout", { method: "POST", token }); } catch { /* ignore */ }
    }
    void auth.signoutRedirect();
  }

  // The landing page carries its own wordmark and call to action, so a rail beside it would only
  // repeat them. It runs bare — the hero and nothing else. Declared after the hooks above so the
  // hook order stays identical on every route.
  if (pathname === "/") {
    return (
      <div className="shell shell--bare">
        <main className="canvas canvas--center canvas--bare">{children}</main>
      </div>
    );
  }

  return (
    <div className="shell">
      <header className="topbar">
        <Link to={auth.isAuthenticated ? "/dashboard" : "/"} className="topbar__mark">Haven Bank</Link>
      </header>

      <aside className="rail">
        <nav className="rail__nav">
          {auth.isAuthenticated ? (
            <>
              <p className="rail__label">Banking</p>
              <NavLink to="/dashboard" className={navClass}>Accounts</NavLink>
              <NavLink to="/transfer" className={navClass}>Move Money</NavLink>
              <NavLink to="/beneficiaries" className={navClass}>Payees</NavLink>

              <p className="rail__label">Settings</p>
              <NavLink to="/preferences" className={navClass}>Notifications</NavLink>
              <NavLink to="/profile" className={navClass}>Profile</NavLink>

              {isStaff && (
                <>
                  <p className="rail__label">Staff</p>
                  <NavLink to="/admin" className={navClass}>Admin</NavLink>
                </>
              )}
            </>
          ) : (
            <>
              <p className="rail__label">Welcome</p>
              <NavLink to="/register" className={navClass}>Open an Account</NavLink>
              <button className="rail__link" onClick={() => navigate("/")}>Sign In</button>
            </>
          )}
        </nav>

        <div className="rail__foot">
          {auth.isAuthenticated && (
            <button className="rail__link" onClick={() => void signOut()}>Sign Out</button>
          )}
          <p className="rail__note">
            Deposits held on account. Transfers are recorded to the ledger.
          </p>
        </div>
      </aside>

      <main className={auth.isAuthenticated ? "canvas" : "canvas canvas--center"}>{children}</main>
    </div>
  );
}

/** Active-route styling for a rail link. */
function navClass({ isActive }: { isActive: boolean }) {
  return isActive ? "rail__link rail__link--on" : "rail__link";
}
