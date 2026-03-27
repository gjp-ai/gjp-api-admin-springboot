package org.ganjp.api.auth.verification;

/**
 * Type of verification token.
 * Matches the ENUM values in the auth_verification_tokens table.
 */
public enum VerificationTokenType {
    PASSWORD_RESET,
    EMAIL_VERIFICATION
}
