package org.ganjp.api.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a resource with the same identifier already exists.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {

    /**
     * Constructs a new duplicate resource exception with the specified detail message.
     *
     * @param message the detail message
     */
    public DuplicateResourceException(String message) {
        super(message);
    }

    /**
     * Constructs a new duplicate resource exception for a specific resource and field.
     *
     * @param resourceType the type of resource (e.g., "Role", "User")
     * @param field the field name that has a duplicate value
     * @param value the duplicate value
     * @return a new DuplicateResourceException with formatted message
     */
    public static DuplicateResourceException of(String resourceType, String field, String value) {
        return new DuplicateResourceException(resourceType + " with " + field + " '" + value + "' already exists");
    }
}
