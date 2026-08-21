package com.havenbank.backend.iam.dto;

import com.havenbank.backend.iam.domain.UserStatus;

import java.util.Set;
import java.util.UUID;

/**
 * Safe projection of a user for API responses. Never includes the password hash or any secret
 * material (FR-1.3, FR-5.3).
 */
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        UserStatus status,
        boolean emailVerified,
        Set<String> roles
) {
}
