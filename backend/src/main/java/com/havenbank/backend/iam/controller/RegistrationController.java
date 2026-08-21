package com.havenbank.backend.iam.controller;

import com.havenbank.backend.iam.dto.RegisterRequest;
import com.havenbank.backend.iam.dto.VerifyEmailRequest;
import com.havenbank.backend.iam.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.havenbank.backend.shared.ratelimit.RateLimited;
import com.havenbank.backend.shared.ratelimit.RateLimitTier;


/**
 * Public registration and email-verification endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/v1/register} &rarr; {@code 202 Accepted} (always; enumeration-safe)</li>
 *   <li>{@code POST /api/v1/register/verify} &rarr; {@code 204 No Content} on success</li>
 * </ul>
 */
@Tag(name = "Registration", description = "Public sign-up and email verification.")
@RestController
@RequestMapping("/api/v1/register")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @SecurityRequirements()
    @Operation(summary = "Register", description = "Creates a pending customer and emails a verification link. Public.")
    @ApiResponses({@ApiResponse(responseCode = "202", description = "Registration accepted; verification email sent"),
            @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content())})
    @RateLimited(RateLimitTier.CRITICAL)
    @PostMapping
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        registrationService.register(request);
        return ResponseEntity.accepted().build();
    }

    @SecurityRequirements()
    @Operation(summary = "Verify email", description = "Activates the account using the single-use verification token. Public.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Email verified; account active"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token", content = @Content())})
    @RateLimited(RateLimitTier.CRITICAL)
    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@Valid @RequestBody VerifyEmailRequest request) {
        registrationService.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }
}