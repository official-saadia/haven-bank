package com.havenbank.backend.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a resource does not exist <em>or</em> when the caller is not permitted to know that it
 * exists. Rendering both cases as {@code 404} is a deliberate anti-enumeration control (FR-2.3).
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(ErrorType.NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
