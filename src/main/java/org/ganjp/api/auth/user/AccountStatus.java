package org.ganjp.api.auth.user;

/**
 * Account status enumeration for user accounts.
 * Matches exact values in the database ENUM column.
 */
public enum AccountStatus {
    /**
     * Account is active and can be used normally
     */
    active,
    
    /**
     * Account is locked due to security issues (too many failed logins, etc.)
     */
    locked,
    
    /**
     * Account is suspended (temporary ban)
     */
    suspended,
    
    /**
     * Account is pending email verification
     */
    pending_verification
}
