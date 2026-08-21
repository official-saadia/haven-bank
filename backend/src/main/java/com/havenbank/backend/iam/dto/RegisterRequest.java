package com.havenbank.backend.iam.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Password policy beyond length (breached-password check) is enforced in the
 * service layer against an external source (FR-1.2); bean validation covers the cheap structural
 * checks here.
 *
 * <p>Two deliberate choices on the human-facing fields:
 * <ul>
 *   <li><strong>Email</strong> requires a dot in the domain ({@code a@b.c}), which rejects the
 *       obvious {@code 1212121@erwr} typo at the form. It is intentionally not stricter than that:
 *       deliverability is proven by the verification email (FR-1.1), not by a regex, and tight
 *       address patterns are notorious for rejecting valid addresses.</li>
 *   <li><strong>Name</strong> must contain a letter and no digits, but the letter may be from any
 *       script — so O'Brien, José-María, Müller and Ng all pass while {@code 1212121} does not.
 *       Restricting to {@code [A-Za-z]} would lock out a large share of real customers, which for a
 *       bank is a genuine failure, not a nicety.</li>
 * </ul>
 */
public record RegisterRequest(
        @NotBlank(message = "Enter your email address.")
        @Size(max = 320)
        @Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "Enter a valid email address.")
        String email,

        @NotBlank(message = "Enter a password.")
        @Size(min = 12, max = 128, message = "Password must be at least 12 characters.")
        String password,

        @NotBlank(message = "Enter your name.")
        @Size(max = 200)
        @Pattern(regexp = "(?=.*\\p{L})[\\p{L}\\p{M} .,''-]+",
                message = "Name can contain letters, spaces, hyphens and apostrophes, but not numbers.")
        String fullName
) {
}