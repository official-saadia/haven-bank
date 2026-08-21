package com.havenbank.backend.iam.dto;

import java.util.Set;
import java.util.UUID;

public record UserView(UUID id, String email, String fullName, Set<String> roles) {
}
