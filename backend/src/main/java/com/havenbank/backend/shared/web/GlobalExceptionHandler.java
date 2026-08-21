package com.havenbank.backend.shared.web;

import com.havenbank.backend.shared.error.BusinessException;
import com.havenbank.backend.shared.error.ErrorType;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;

/**
 * Translates exceptions into RFC 7807 {@link ProblemDetail} responses. Guarantees that internal
 * exceptions, stack traces and framework details never reach the client (NFR-5.4); unexpected
 * errors are logged with the correlation id and returned as an opaque {@code 500}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        ProblemDetail pd = problem(ex.getStatus(), ex.getErrorType().type(),
                ex.getErrorType().title(), ex.getMessage());
        // If the failure names a field, surface it in the same `errors` shape bean-validation uses,
        // so the client shows it under that input rather than only in the form-level slot.
        if (ex.getField() != null) {
            pd.setProperty("errors", List.of(new FieldError(ex.getField(), ex.getMessage())));
        }
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, ErrorType.VALIDATION.type(),
                ErrorType.VALIDATION.title(), "One or more fields are invalid");
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraint(ConstraintViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, ErrorType.VALIDATION.type(),
                ErrorType.VALIDATION.title(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, ErrorType.FORBIDDEN.type(), ErrorType.FORBIDDEN.title(),
                "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // Spring's own MVC exceptions (no handler, method not allowed, unreadable body, ...)
        // implement ErrorResponse and already carry the right status. Reporting those as 500 would
        // turn an ordinary 404 into a phantom server fault, so honour the status they declare and
        // reserve 500 for genuinely unexpected failures.
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            log.debug("Request could not be handled: {} {}", status.value(), ex.getMessage());
            return problem(status, ErrorType.BASE_URI + status.value(),
                    status.getReasonPhrase(), status.getReasonPhrase());
        }
        // Log the detail server-side; never expose it to the client.
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorType.BASE_URI + "internal",
                "Internal server error", "An unexpected error occurred");
    }

    private ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(type));
        pd.setTitle(title);
        pd.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return pd;
    }

    /**
     * Minimal field-error projection included in validation problem responses.
     */
    public record FieldError(String field, String message) {
    }
}