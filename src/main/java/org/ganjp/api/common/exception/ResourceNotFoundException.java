package org.ganjp.api.common.exception;

/**
 * Exception thrown when a requested resource is not found.
 * This allows the GlobalExceptionHandler to return a 404 status code.
 */
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: %s", resourceName, fieldName, fieldValue));
    }
}
