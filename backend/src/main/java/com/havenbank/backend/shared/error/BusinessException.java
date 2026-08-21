package com.havenbank.backend.shared.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for expected, client-facing failures. Carries the HTTP status and the {@link ErrorType}
 * so the global handler can render a consistent RFC 7807 response without leaking internals.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorType errorType;
    private final HttpStatus status;
    /**
     * The input this failure concerns, if any, so the client can show it under that field rather
     * than only at the form level. Null for errors that aren't tied to a single field.
     */
    private final String field;

    public BusinessException(ErrorType errorType, HttpStatus status, String message) {
        this(errorType, status, message, null);
    }

    public BusinessException(ErrorType errorType, HttpStatus status, String message, String field) {
        super(message);
        this.errorType = errorType;
        this.status = status;
        this.field = field;
    }
}