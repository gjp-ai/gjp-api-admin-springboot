package org.ganjp.api.auth.security;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Security properties configuration class.
 * Maps security.* properties from application.yml to Java objects.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {
    
    private List<String> publicEndpoints;
    private List<AuthorizedEndpoint> authorizedEndpoints;
    private Cors cors;
    private Jwt jwt;
    private Verification verification = new Verification();
    private AccountLock accountLock = new AccountLock();
    private RateLimit rateLimit = new RateLimit();

    /**
     * Authorized endpoint configuration with pattern and required roles.
     */
    @Data
    public static class AuthorizedEndpoint {
        private String pattern;
        private List<String> roles;
    }
    
    /**
     * CORS configuration properties.
     */
    @Data
    public static class Cors {
        private List<String> allowedOrigins;
    }
    
    /**
     * JWT configuration properties.
     */
    @Data
    public static class Jwt {
        /** 30 days in milliseconds */
        private static final long DEFAULT_REFRESH_EXPIRATION_MS = 30L * 24 * 60 * 60 * 1000;

        private String secretKey;
        private long expiration;
        private long refreshExpiration = DEFAULT_REFRESH_EXPIRATION_MS;
        private String issuer = "gjp-api-admin";
    }

    /**
     * Verification token configuration properties.
     */
    @Data
    public static class Verification {
        /** Password reset token expiration: 1 hour in milliseconds */
        private long passwordResetExpiration = 3_600_000L;
        /** Email verification token expiration: 24 hours in milliseconds */
        private long emailVerificationExpiration = 86_400_000L;
    }

    /**
     * Account lock configuration properties.
     */
    @Data
    public static class AccountLock {
        /** Max consecutive failed login attempts before account is locked */
        private int maxFailedAttempts = 5;
        /** Account lock duration in minutes */
        private int lockDurationMinutes = 30;
    }

    /**
     * Login rate limit configuration properties.
     */
    @Data
    public static class RateLimit {
        /** Max login attempts per IP within the time window */
        private int maxAttempts = 10;
        /** Time window in milliseconds */
        private long windowMs = 60_000L;
    }
}
