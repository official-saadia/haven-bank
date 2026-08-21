package com.havenbank.backend.iam.controller;

import com.havenbank.backend.iam.dto.UserResponse;
import com.havenbank.backend.iam.service.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.havenbank.backend.shared.ratelimit.RateLimited;
import com.havenbank.backend.shared.ratelimit.RateLimitTier;

import java.util.UUID;

/**
 * Self-service profile endpoint for the authenticated user.
 */
@Tag(name = "Me", description = "The authenticated user's own profile.")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final UserQueryService userQueryService;

    @Operation(summary = "Get my profile", description = "Returns the authenticated user's profile.")
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userQueryService.getById(UUID.fromString(jwt.getSubject()));
    }
}