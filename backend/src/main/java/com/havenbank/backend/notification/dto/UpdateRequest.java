package com.havenbank.backend.notification.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateRequest(
        @NotNull(message = "Provide the preferences to update.") List<PreferenceView> preferences) {
}
