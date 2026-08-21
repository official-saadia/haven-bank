import { Route, Routes } from "react-router-dom";
import { Layout } from "./components/Layout";
import { RequireAuth } from "./auth/RequireAuth";
import { Home } from "./pages/Home";
import { Callback } from "./pages/Callback";
import { Register } from "./pages/Register";
import { VerifyEmail } from "./pages/VerifyEmail";
import { ForgotPassword } from "./pages/ForgotPassword";
import { ResetPassword } from "./pages/ResetPassword";
import { Profile } from "./pages/Profile";
import { ChangePassword } from "./pages/ChangePassword";
import { Dashboard } from "./pages/Dashboard";
import { DashboardPreview } from "./pages/DashboardPreview";
import { AccountDetail } from "./pages/AccountDetail";
import { Transfer } from "./pages/Transfer";
import { Beneficiaries } from "./pages/Beneficiaries";
import { Preferences } from "./pages/Preferences";
import { RequireRole } from "./auth/RequireRole";
import { AdminHome } from "./pages/admin/AdminHome";
import { AdminUsers } from "./pages/admin/AdminUsers";
import { AdminRoles } from "./pages/admin/AdminRoles";
import { AdminAudit } from "./pages/admin/AdminAudit";
import { AdminPolicies } from "./pages/admin/AdminPolicies";
import { AdminPreview } from "./pages/admin/AdminPreview";

export function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/preview" element={<DashboardPreview />} />
        <Route path="/admin-preview" element={<AdminPreview />} />
        <Route path="/oauth/callback" element={<Callback />} />
        <Route path="/register" element={<Register />} />
        <Route path="/verify-email" element={<VerifyEmail />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route path="/dashboard" element={<RequireAuth><Dashboard /></RequireAuth>} />
        <Route path="/accounts/:id" element={<RequireAuth><AccountDetail /></RequireAuth>} />
        <Route path="/transfer" element={<RequireAuth><Transfer /></RequireAuth>} />
        <Route path="/beneficiaries" element={<RequireAuth><Beneficiaries /></RequireAuth>} />
        <Route path="/preferences" element={<RequireAuth><Preferences /></RequireAuth>} />
        <Route path="/profile" element={<RequireAuth><Profile /></RequireAuth>} />
        <Route path="/change-password" element={<RequireAuth><ChangePassword /></RequireAuth>} />
        <Route path="/admin" element={<RequireAuth><AdminHome /></RequireAuth>} />
        <Route path="/admin/users" element={<RequireAuth><RequireRole anyOf={["ADMIN"]}><AdminUsers /></RequireRole></RequireAuth>} />
        <Route path="/admin/roles" element={<RequireAuth><RequireRole anyOf={["ADMIN"]}><AdminRoles /></RequireRole></RequireAuth>} />
        <Route path="/admin/audit" element={<RequireAuth><RequireRole anyOf={["ADMIN", "STAFF"]}><AdminAudit /></RequireRole></RequireAuth>} />
        <Route path="/admin/policies" element={<RequireAuth><RequireRole anyOf={["ADMIN"]}><AdminPolicies /></RequireRole></RequireAuth>} />
      </Routes>
    </Layout>
  );
}
