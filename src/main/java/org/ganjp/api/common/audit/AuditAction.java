package org.ganjp.api.common.audit;

/**
 * Enumeration of audit actions that can be tracked.
 * Represents the type of operation being performed.
 */
public enum AuditAction {
    // Authentication actions
    LOGIN("User login attempt"),
    LOGOUT("User logout"),
    SIGNUP("User registration"),
    PASSWORD_CHANGE("Password change"),
    PASSWORD_RESET("Password reset"),
    
    // User management actions
    USER_CREATE("User creation"),
    USER_UPDATE("User update"),
    USER_PATCH("User partial update"),
    USER_DELETE("User deletion"),
    USER_DELETE_PERMANENT("User permanent deletion"),
    USER_STATUS_CHANGE("User status change"),
    USER_ROLE_ASSIGN("User role assignment"),
    USER_ROLE_REVOKE("User role revocation"),
    
    // Role management actions
    ROLE_CREATE("Role creation"),
    ROLE_UPDATE("Role update"),
    ROLE_PATCH("Role partial update"),
    ROLE_DELETE("Role deletion"),
    ROLE_STATUS_CHANGE("Role status change"),
    
    // Generic actions
    CREATE("Generic creation"),
    UPDATE("Generic update"),
    DELETE("Generic deletion"),
    OTHER("Other action");

    private final String description;

    AuditAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Determine audit action based on HTTP method and endpoint
     */
    public static AuditAction fromHttpMethodAndEndpoint(String httpMethod, String endpoint) {
        if (endpoint == null) return OTHER;
        
        // Authentication endpoints
        if (endpoint.contains("/auth/login")) return LOGIN;
        if (endpoint.contains("/auth/signup")) return SIGNUP;
        if (endpoint.contains("/auth/logout")) return LOGOUT;
        
        // User management endpoints
        if (endpoint.contains("/users")) {
            if (endpoint.contains("/password")) return PASSWORD_CHANGE;
            if (endpoint.contains("/toggle-status")) return USER_STATUS_CHANGE;
            if (endpoint.contains("/permanent")) return USER_DELETE_PERMANENT;
            
            switch (httpMethod.toUpperCase()) {
                case "POST": return USER_CREATE;
                case "PUT": return USER_UPDATE;
                case "PATCH": return USER_PATCH;
                case "DELETE": return USER_DELETE;
                default: return OTHER;
            }
        }
        
        // Role management endpoints
        if (endpoint.contains("/roles")) {
            if (endpoint.contains("/toggle-status")) return ROLE_STATUS_CHANGE;
            
            switch (httpMethod.toUpperCase()) {
                case "POST": return ROLE_CREATE;
                case "PUT": return ROLE_UPDATE;
                case "PATCH": return ROLE_PATCH;
                case "DELETE": return ROLE_DELETE;
                default: return OTHER;
            }
        }
        
        // Generic fallback based on HTTP method
        return switch (httpMethod.toUpperCase()) {
            case "POST" -> CREATE;
            case "PUT", "PATCH" -> UPDATE;
            case "DELETE" -> DELETE;
            default -> OTHER;
        };
    }
}
