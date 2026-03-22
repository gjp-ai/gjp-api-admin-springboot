package org.ganjp.api.common.exception;

/**
 * Exception thrown for business logic violations or validation failures.
 * This allows the GlobalExceptionHandler to return appropriate status codes.
 */
public class BusinessException extends RuntimeException {
    
    public BusinessException(String message) {
        super(message);
    }
    
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
