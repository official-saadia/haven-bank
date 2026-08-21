package com.havenbank.backend.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create or rename a role. The name is an identifier, so it is constrained to a conventional
 * token shape rather than free text.
 */
public record RoleRequest(
        @NotBlank(message = "Enter a role name.")
        @Size(max = 32)
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_ -]*",
                message = "Role name must start with a letter and use only letters, digits, spaces, hyphens or underscores.")
        String name,

        @Size(max = 256, message = "Description is too long.") String description
) {
}