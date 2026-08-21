package com.havenbank.backend.money.controller;

import com.havenbank.backend.money.domain.TransactionType;
import com.havenbank.backend.money.dto.TransactionResponse;
import com.havenbank.backend.money.service.TransactionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.havenbank.backend.shared.ratelimit.RateLimited;
import com.havenbank.backend.shared.ratelimit.RateLimitTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Paginated, filterable transaction history and CSV statement export for an owned account.
 */
@Tag(name = "Transaction history", description = "Paginated history and CSV statement export for an owned account.")
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionQueryService transactionQueryService;

    @Operation(summary = "List account transactions",
            description = "Returns the transaction history for an owned account, filterable by type, date range and amount range.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of transactions"),
            @ApiResponse(responseCode = "404", description = "No such account, or not owned by the caller", content = @Content())
    })
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping("/accounts/{id}/transactions")
    public Page<TransactionResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                             @RequestParam(required = false) TransactionType type,
                                             @RequestParam(required = false) Instant from,
                                             @RequestParam(required = false) Instant to,
                                             @RequestParam(required = false) BigDecimal minAmount,
                                             @RequestParam(required = false) BigDecimal maxAmount,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return transactionQueryService.history(UUID.fromString(jwt.getSubject()), id, type, from, to,
                minAmount, maxAmount, pageable);
    }

    @Operation(summary = "Export a statement (CSV)",
            description = "Streams a CSV statement for the chosen period as a file download.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV statement"),
            @ApiResponse(responseCode = "404", description = "No such account, or not owned by the caller", content = @Content())
    })
    @RateLimited(RateLimitTier.STANDARD)
    @GetMapping("/accounts/{id}/statement")
    public ResponseEntity<String> statement(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                            @RequestParam Instant from, @RequestParam Instant to) {
        String csv = transactionQueryService.statementCsv(UUID.fromString(jwt.getSubject()), id, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"statement.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}