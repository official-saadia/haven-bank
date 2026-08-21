package com.havenbank.backend.shared.error;

/**
 * Stable, documentable problem categories surfaced to API clients via RFC 7807 {@code type} URIs.
 * Keeping these as an enum (rather than free-form strings) means the set of error contracts is
 * explicit and testable.
 */
public enum ErrorType {

    VALIDATION("validation", "Validation failed"),
    NOT_FOUND("not-found", "Resource not found"),
    CONFLICT("conflict", "Conflict"),
    BUSINESS_RULE("business-rule", "Business rule violation"),
    INVALID_TOKEN("invalid-token", "Invalid or expired token"),
    UNAUTHORIZED("unauthorized", "Authentication required"),
    FORBIDDEN("forbidden", "Access denied");

    public static final String BASE_URI = "https://errors.havenbank.example/";
    private static final String BASE = BASE_URI;

    private final String slug;
    private final String title;

    ErrorType(String slug, String title) {
        this.slug = slug;
        this.title = title;
    }

    /**
     * @return the absolute URI used as the RFC 7807 {@code type}.
     */
    public String type() {
        return BASE + slug;
    }

    public String title() {
        return title;
    }
}