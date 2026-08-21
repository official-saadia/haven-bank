package com.havenbank.backend.money.controller;

import com.havenbank.backend.money.dto.AccountResponse;
import com.havenbank.backend.money.dto.DepositRequest;
import com.havenbank.backend.money.dto.TransferRequest;
import com.havenbank.backend.money.dto.WithdrawRequest;
import com.havenbank.backend.money.service.MoneyMovementService;
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

import java.util.UUID;

/**
 * Money-movement endpoints. All require an {@code Idempotency-Key} header (FR-3.7).
 */
@Tag(name = "Money movement", description = "Deposits, withdrawals and transfers. All require an Idempotency-Key header (FR-3.7).")
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class MoneyController {

    private final MoneyMovementService money;

    @Operation(summary = "Deposit into an owned account",
            description = "Credits an owned account. Idempotent on the Idempotency-Key header.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Deposit posted; returns the updated account"),
            @ApiResponse(responseCode = "400", description = "Invalid amount", content = @Content()),
            @ApiResponse(responseCode = "404", description = "No such account, or not owned by the caller", content = @Content())
    })
    @RateLimited(RateLimitTier.SENSITIVE)
    @PostMapping("/accounts/{id}/deposit")
    public ResponseEntity<AccountResponse> deposit(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID id,
                                                   @RequestHeader("Idempotency-Key") String key,
                                                   @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.status(201)
                .body(money.deposit(userId(jwt), email(jwt), id, request.amount(), key));
    }

    @Operation(summary = "Withdraw from an owned account",
            description = "Debits an owned account, subject to sufficient available balance. Idempotent on the Idempotency-Key header.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Withdrawal posted; returns the updated account"),
            @ApiResponse(responseCode = "400", description = "Invalid amount", content = @Content()),
            @ApiResponse(responseCode = "404", description = "No such account, or not owned by the caller", content = @Content()),
            @ApiResponse(responseCode = "422", description = "Business rule violated, e.g. insufficient funds or a frozen account", content = @Content())
    })
    @RateLimited(RateLimitTier.SENSITIVE)
    @PostMapping("/accounts/{id}/withdraw")
    public ResponseEntity<AccountResponse> withdraw(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID id,
                                                    @RequestHeader("Idempotency-Key") String key,
                                                    @Valid @RequestBody WithdrawRequest request) {
        return ResponseEntity.status(201)
                .body(money.withdraw(userId(jwt), email(jwt), id, request.amount(), key));
    }

    @Operation(summary = "Transfer funds",
            description = "Moves funds between own accounts or to a validated third-party account, as a balanced double-entry pair. Idempotent on the Idempotency-Key header.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transfer posted; returns the source account"),
            @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content()),
            @ApiResponse(responseCode = "404", description = "Source account not found or not owned by the caller", content = @Content()),
            @ApiResponse(responseCode = "422", description = "Business rule violated, e.g. insufficient funds, daily limit exceeded, frozen account, or step-up required", content = @Content())
    })
    @RateLimited(RateLimitTier.SENSITIVE)
    @PostMapping("/transfers")
    public ResponseEntity<AccountResponse> transfer(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestHeader("Idempotency-Key") String key,
                                                    @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(201)
                .body(money.transfer(userId(jwt), email(jwt), null, request, key));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private String email(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }
}