package com.havenbank.backend.authserver;

import com.havenbank.backend.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-5.1: the audit trail is gated on the {@code AUDIT_READ} permission specifically (granted to
 * STAFF and ADMIN by V7), not on any particular role name - a plain CUSTOMER must be forbidden
 * regardless of what role string they carry, since the check is {@code hasAuthority(...)}, not
 * {@code hasRole(...)}.
 */
class AuditQueryIntegrationTest extends AbstractIntegrationTest {

    @Test
    void aCustomerWithoutAuditReadIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/admin/audit")
                        .with(asCustomer(UUID.randomUUID(), "customer@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffWithAuditReadCanListTheAuditTrail() throws Exception {
        mvc.perform(get("/api/v1/admin/audit")
                        .with(asStaffWithAuditRead(UUID.randomUUID(), "staff@example.com")))
                .andExpect(status().isOk());
    }

    @Test
    void anUnauthenticatedRequestIsRejectedBeforeAnyPermissionCheck() throws Exception {
        mvc.perform(get("/api/v1/admin/audit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingTheAuditTrailIsItselfAnAuditableEvent() throws Exception {
        // AuditQueryController.list() records an AUDIT_VIEWED entry on every successful list call
        // (a read of sensitive data being itself worth auditing). Calling it as staff, twice,
        // should never fail or degrade - the self-referential write must not deadlock or error.
        var staff = asStaffWithAuditRead(UUID.randomUUID(), "staff2@example.com");
        mvc.perform(get("/api/v1/admin/audit").with(staff)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/audit").with(staff)).andExpect(status().isOk());
    }
}
