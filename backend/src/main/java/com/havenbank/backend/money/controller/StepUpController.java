package com.havenbank.backend.money.controller;

import com.havenbank.backend.money.dto.VerifyRequest;
import com.havenbank.backend.money.service.StepUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.havenbank.backend.shared.ratelimit.RateLimited;
import com.havenbank.backend.shared.ratelimit.RateLimitTier;

import java.util.UUID;

/**
 * Step-up OTP challenge/verify for high-value transfers (FR-3.9).
 */
@Tag(name = "Step-up (OTP)", description = "Fresh OTP challenge/verify for high-value transfers (FR-3.9).")
@RestController
@RequestMapping("/api/v1/auth/otp")
@RequiredArgsConstructor
public class StepUpController {

    private final StepUpService stepUpService;

    @Operation(summary = "Request a step-up OTP", description = "Issues a fresh one-time passcode by email for a high-value action.")
    @ApiResponse(responseCode = "202", description = "Challenge issued (code sent by email)")
    @RateLimited(RateLimitTier.CRITICAL)
    @PostMapping("/challenge")
    public ResponseEntity<Void> challenge(@AuthenticationPrincipal Jwt jwt) {
        stepUpService.issueChallenge(UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("email"), null);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Verify a step-up OTP", description = "Verifies the emailed code, clearing the step-up requirement for the pending action.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Code accepted"),
            @ApiResponse(responseCode = "422", description = "Code incorrect or expired", content = @Content())
    })
    @RateLimited(RateLimitTier.CRITICAL)
    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody VerifyRequest request) {
        boolean ok = stepUpService.verify(UUID.fromString(jwt.getSubject()), request.code());
        return ok ? ResponseEntity.ok().build() : ResponseEntity.unprocessableEntity().build();
    }
}