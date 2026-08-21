package com.havenbank.backend.money.controller;

import com.havenbank.backend.money.dto.FeeScheduleRequest;
import com.havenbank.backend.money.dto.FeeScheduleResponse;
import com.havenbank.backend.money.dto.PolicyRequest;
import com.havenbank.backend.money.dto.PolicyResponse;
import com.havenbank.backend.money.service.FeeAdminService;
import com.havenbank.backend.money.service.PolicyAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.havenbank.backend.shared.ratelimit.RateLimited;
import com.havenbank.backend.shared.ratelimit.RateLimitTier;

import java.util.List;
import java.util.UUID;

/**
 * Versioned fee-schedule and policy administration (ADMIN only).
 */
@Tag(name = "Admin: fees & policies", description = "Versioned fee-schedule and policy administration (ADMIN only).")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminFeePolicyController {

    private final FeeAdminService feeAdmin;
    private final PolicyAdminService policyAdmin;

    @Operation(summary = "List fee schedules", description = "Returns all fee-schedule versions.")
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping("/fee-schedules")
    public List<FeeScheduleResponse> feeSchedules() {
        return feeAdmin.list();
    }

    @Operation(summary = "Create a fee-schedule version", description = "Publishes a new fee-schedule version.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "New version created"),
            @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content())
    })
    @RateLimited(RateLimitTier.STANDARD)
    @PostMapping("/fee-schedules")
    public ResponseEntity<FeeScheduleResponse> newFee(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody FeeScheduleRequest request) {
        return ResponseEntity.status(201).body(feeAdmin.createVersion(actor(jwt), request));
    }

    @Operation(summary = "List policies", description = "Returns all policy versions.")
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping("/policies")
    public List<PolicyResponse> policies() {
        return policyAdmin.list();
    }

    @Operation(summary = "Create a policy version", description = "Publishes a new policy version.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "New version created"),
            @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content())
    })
    @RateLimited(RateLimitTier.STANDARD)
    @PostMapping("/policies")
    public ResponseEntity<PolicyResponse> newPolicy(@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody PolicyRequest request) {
        return ResponseEntity.status(201).body(policyAdmin.createVersion(actor(jwt), request));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}