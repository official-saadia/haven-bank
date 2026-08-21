package com.havenbank.backend.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a one-time token (email verification, password reset) is missing, expired or used.
 */
public class InvalidTokenException extends BusinessException {

    public InvalidTokenException(String message) {
        super(ErrorType.INVALID_TOKEN, HttpStatus.GONE, message);
    }
}
