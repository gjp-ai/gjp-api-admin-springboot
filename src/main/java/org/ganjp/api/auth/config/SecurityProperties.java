package org.ganjp.api.auth.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

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
        private String secretKey;
        private long expiration;
        private long refreshExpiration = 2592000000L; // 30 days in milliseconds (default)
    }
}