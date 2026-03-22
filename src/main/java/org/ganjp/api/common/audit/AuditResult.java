package org.ganjp.api.common.audit;

/**
 * Enumeration of possible audit results.
 * Indicates the outcome of the audited operation.
 */
public enum AuditResult {
    /**
     * Operation completed successfully
     */
    SUCCESS("Operation completed successfully"),
    
    /**
     * Operation failed due to client error (4xx)
     */
    FAILURE("Operation failed"),
    
    /**
     * Operation failed due to server error (5xx)
     */
    ERROR("Operation encountered an error"),
    
    /**
     * Operation was denied due to insufficient permissions
     */
    DENIED("Operation denied due to insufficient permissions"),
    
    /**
     * Operation was blocked due to validation errors
     */
    VALIDATION_ERROR("Operation blocked due to validation errors"),
    
    /**
     * Operation was denied due to authentication failure
     */
    AUTHENTICATION_FAILED("Authentication failed"),
    
    /**
     * Operation timed out
     */
    TIMEOUT("Operation timed out"),
    
    /**
     * Operation was cancelled or aborted
     */
    CANCELLED("Operation was cancelled");

    private final String description;

    AuditResult(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Determine audit result based on HTTP status code
     */
    public static AuditResult fromStatusCode(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return SUCCESS;
        } else if (statusCode == 401) {
            return AUTHENTICATION_FAILED;
        } else if (statusCode == 403) {
            return DENIED;
        } else if (statusCode == 400 || statusCode == 422) {
            return VALIDATION_ERROR;
        } else if (statusCode == 408) {
            return TIMEOUT;
        } else if (statusCode >= 400 && statusCode < 500) {
            return FAILURE;
        } else if (statusCode >= 500) {
            return ERROR;
        } else {
            return FAILURE;
        }
    }
}
