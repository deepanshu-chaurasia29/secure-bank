package com.deepu.securebank.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown for any expected business-rule failure
 * (duplicate email, wrong password, account locked, etc).
 * The GlobalExceptionHandler turns this into a clean JSON error,
 * never a raw stack trace.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
