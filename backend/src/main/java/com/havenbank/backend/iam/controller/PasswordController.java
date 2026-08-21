package com.havenbank.backend.iam.controller;

import com.havenbank.backend.iam.dto.ChangePasswordRequest;
import com.havenbank.backend.iam.dto.ForgotPasswordRequest;
import com.havenbank.backend.iam.dto.ResetPasswordRequest;
import com.havenbank.backend.iam.service.PasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * Password lifecycle endpoints. {@code change} is authenticated (the user id is read from the JWT
 * subject); {@code forgot} and {@code reset} are public and enumeration-safe.
 */
@Tag(name = "Password", description = "Change, forgot and reset. Forgot/reset are public and enumeration-safe.")
@RestController
@RequestMapping("/api/v1/password")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService passwordService;

    @Operation(summary = "Change password", description = "Changes the authenticated user's password.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Password changed"),
            @ApiResponse(responseCode = "400", description = "Invalid request body or password policy failure",
                    content = @Content())})
    @RateLimited(RateLimitTier.SENSITIVE)
    @PostMapping("/change")
    public ResponseEntity<Void> change(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        passwordService.changePassword(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.noContent().build();
    }

    @SecurityRequirements()
    @Operation(summary = "Request a password reset", description = "Emails a single-use reset link if the address exists." +
            " Always returns 202 so it cannot be used to enumerate accounts (FR-1.7). Public.")
    @ApiResponse(responseCode = "202", description = "If the address exists, a reset email has been sent")
    @RateLimited(RateLimitTier.CRITICAL)
    @PostMapping("/forgot")
    public ResponseEntity<Void> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordService.forgotPassword(request);
        return ResponseEntity.accepted().build();
    }

    @SecurityRequirements()
    @Operation(summary = "Reset password", description = "Sets a new password using the single-use reset token. Public.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Password reset"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token, or password policy failure", content = @Content())})
    @RateLimited(RateLimitTier.CRITICAL)
    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}