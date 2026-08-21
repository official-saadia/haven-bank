package com.havenbank.backend.money.controller;

import com.havenbank.backend.money.dto.AccountResponse;
import com.havenbank.backend.money.dto.OpenAccountRequest;
import com.havenbank.backend.money.service.AccountService;
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
 * Account read + lifecycle endpoints, scoped to the authenticated customer.
 */
@Tag(name = "Accounts", description = "Account read and lifecycle for the authenticated customer.")
@RestController
@RequestMapping("/api/v1/accounts")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "List my accounts",
            description = "Returns every account owned by the authenticated customer.")
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return accountService.listOwn(userId(jwt));
    }

    @Operation(summary = "Get one account", description = "Fetches a single owned account by id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The account"),
            @ApiResponse(responseCode = "404", description = "No such account, or not owned by the caller", content = @Content())
    })
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping("/{id}")
    public AccountResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return accountService.getOwned(userId(jwt), id);
    }

    @Operation(summary = "Open an account",
            description = "Opens a new account. Requires an Idempotency-Key header; replaying a key returns the original" +
                    " account rather than opening a second one (FR-3.7).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account opened (or the original account, on a replayed key)"),
            @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content())
    })
    @RateLimited(RateLimitTier.SENSITIVE)
    @PostMapping
    public ResponseEntity<AccountResponse> open(@AuthenticationPrincipal Jwt jwt,
                                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                @Valid @RequestBody OpenAccountRequest request) {
        AccountResponse account = accountService.open(userId(jwt), request.type(), request.currency(),
                idempotencyKey);
        return ResponseEntity.status(201).body(account);
    }

    @Operation(summary = "Close an account", description = "Closes an owned account.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Account closed"),
            @ApiResponse(responseCode = "404", description = "No such account, or not owned by the caller", content = @Content()),
            @ApiResponse(responseCode = "422", description = "Account cannot be closed in its current state", content = @Content())
    })
    @RateLimited(RateLimitTier.SENSITIVE)
    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        accountService.close(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}