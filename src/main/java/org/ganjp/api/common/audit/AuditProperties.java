package org.ganjp.api.common.audit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for audit logging.
 */
@Data
@Component
@ConfigurationProperties(prefix = "audit")
public class AuditProperties {

    /**
     * Whether audit logging is enabled
     */
    private boolean enabled = true;

    /**
     * Whether to log request data
     */
    private boolean logRequestData = true;

    /**
     * Whether to log response data
     */
    private boolean logResponseData = true;

    /**
     * Maximum length for request/response data in audit logs
     */
    private int maxDataLength = 10000;

    /**
     * Whether to log successful operations
     */
    private boolean logSuccessfulOperations = true;

    /**
     * Whether to log failed operations
     */
    private boolean logFailedOperations = true;

    /**
     * Number of days to retain audit logs (0 = keep forever)
     */
    private int retentionDays = 90;

    /**
     * Whether to enable async processing for audit logs
     */
    private boolean asyncProcessing = true;

    /**
     * Thread pool configuration for async audit processing
     */
    private ThreadPoolConfig threadPool = new ThreadPoolConfig();

    /**
     * Endpoints to exclude from auditing (regex patterns)
     */
    private String[] excludePatterns = {
            "/actuator/.*",
            "/swagger-.*",
            "/v3/api-docs.*",
            "/favicon.ico"
    };

    /**
     * Whether to include sensitive data in audit logs (not recommended for production)
     */
    private boolean includeSensitiveData = false;

    /**
     * Whether to audit authentication events separately
     */
    private boolean auditAuthenticationEvents = true;

    /**
     * Maximum number of failed attempts to log before rate limiting
     */
    private int maxFailedAttemptsPerMinute = 10;

    @Data
    public static class ThreadPoolConfig {
        private int corePoolSize = 2;
        private int maxPoolSize = 5;
        private int queueCapacity = 100;
        private int keepAliveSeconds = 60;
        private String threadNamePrefix = "audit-";
    }
}
