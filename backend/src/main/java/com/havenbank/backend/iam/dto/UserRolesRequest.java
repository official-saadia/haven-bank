package com.havenbank.backend.iam.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Replaces a user's role assignments with exactly this set. An empty list is allowed (strips all
 * roles); null is not, so a malformed body is rejected rather than treated as "no change".
 */
public record UserRolesRequest(
        @NotNull(message = "Provide the set of roles to assign.") List<UUID> roleIds
) {
}