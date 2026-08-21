package com.havenbank.backend.iam.dto;

import java.util.List;
import java.util.UUID;

public record RolePermissionsRequest(List<UUID> permissionIds) {
}
