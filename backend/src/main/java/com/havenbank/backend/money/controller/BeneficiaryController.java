package com.havenbank.backend.money.controller;

import com.havenbank.backend.money.dto.BeneficiaryRequest;
import com.havenbank.backend.money.dto.BeneficiaryResponse;
import com.havenbank.backend.money.service.BeneficiaryService;
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
 * Saved payees. Role check here, ownership check in the service (FR-1.12).
 */
@Tag(name = "Payees", description = "Saved beneficiaries for the authenticated customer.")
@RestController
@RequestMapping("/api/v1/beneficiaries")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    @Operation(summary = "List my payees", description = "Returns the caller's saved beneficiaries.")
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping
    public List<BeneficiaryResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return beneficiaryService.listOwn(userId(jwt));
    }

    @Operation(summary = "Add a payee", description = "Saves a new beneficiary for the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payee created"),
            @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content()),
            @ApiResponse(responseCode = "409", description = "A payee with the same account number already exists", content = @Content())
    })
    @RateLimited(RateLimitTier.SENSITIVE)
    @PostMapping
    public ResponseEntity<BeneficiaryResponse> add(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody BeneficiaryRequest request) {
        return ResponseEntity.status(201).body(beneficiaryService.add(userId(jwt), request));
    }

    @Operation(summary = "Update a payee", description = "Updates an owned beneficiary.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated payee"),
            @ApiResponse(responseCode = "404", description = "No such payee, or not owned by the caller", content = @Content())
    })
    @RateLimited(RateLimitTier.SENSITIVE)
    @PutMapping("/{id}")
    public BeneficiaryResponse update(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody BeneficiaryRequest request) {
        return beneficiaryService.update(userId(jwt), id, request);
    }

    @Operation(summary = "Remove a payee", description = "Deletes an owned beneficiary.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Payee removed"),
            @ApiResponse(responseCode = "404", description = "No such payee, or not owned by the caller", content = @Content())
    })
    @RateLimited(RateLimitTier.SENSITIVE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        beneficiaryService.delete(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}