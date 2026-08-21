package com.havenbank.backend.iam.dto;

import com.havenbank.backend.iam.domain.UserStatus;

import java.util.Set;
import java.util.UUID;

public record AdminUserResponse(UUID id, String email, String fullName, UserStatus status,
                                boolean emailVerified, Set<String> roles) {
}
