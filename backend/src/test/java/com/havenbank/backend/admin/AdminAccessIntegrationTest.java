package com.havenbank.backend.admin;

import com.havenbank.backend.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-1.12: every {@code hasRole('ADMIN')} controller actually enforces it against real requests -
 * a CUSTOMER-only token must be forbidden, an ADMIN token must succeed. Lighter coverage than the
 * money/notification/audit tests: role-gating only, not each controller's full business logic.
 */
class AdminAccessIntegrationTest extends AbstractIntegrationTest {

    @Test
    void customerCannotListRoles() throws Exception {
        mvc.perform(get("/api/v1/admin/roles").with(asCustomer(UUID.randomUUID(), "customer@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListRoles() throws Exception {
        mvc.perform(get("/api/v1/admin/roles").with(asAdmin())).andExpect(status().isOk());
    }

    @Test
    void customerCannotListUsers() throws Exception {
        mvc.perform(get("/api/v1/admin/users").with(asCustomer(UUID.randomUUID(), "customer@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsers() throws Exception {
        mvc.perform(get("/api/v1/admin/users").with(asAdmin())).andExpect(status().isOk());
    }

    @Test
    void customerCannotListFeeSchedules() throws Exception {
        mvc.perform(get("/api/v1/admin/fee-schedules").with(asCustomer(UUID.randomUUID(), "customer@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListFeeSchedules() throws Exception {
        mvc.perform(get("/api/v1/admin/fee-schedules").with(asAdmin())).andExpect(status().isOk());
    }

    @Test
    void customerCannotListDeadLetteredNotifications() throws Exception {
        mvc.perform(get("/api/v1/admin/notifications").with(asCustomer(UUID.randomUUID(), "customer@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListDeadLetteredNotifications() throws Exception {
        mvc.perform(get("/api/v1/admin/notifications").with(asAdmin())).andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestsAreRejectedBeforeAnyRoleCheck() throws Exception {
        mvc.perform(get("/api/v1/admin/roles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/fee-schedules")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/notifications")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asAdmin() {
        return asUser(UUID.randomUUID(), "admin@example.com", java.util.List.of("ADMIN"), java.util.List.of());
    }
}
