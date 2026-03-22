package org.ganjp.api.common.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing audit logs for API operations.
 * Tracks all non-GET API calls for security and compliance purposes.
 * Simplified schema focusing on essential tracking information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_result", columnList = "result"),
    @Index(name = "idx_audit_endpoint", columnList = "endpoint"),
    @Index(name = "idx_audit_request_id", columnList = "request_id"),
    @Index(name = "idx_audit_user_timestamp", columnList = "user_id, timestamp"),
    @Index(name = "idx_audit_ip_address", columnList = "ip_address"),
    @Index(name = "idx_audit_status_code", columnList = "status_code")
})
public class AuditLog {

    /**
     * Unique identifier for the audit log entry
     */
    @Id
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    /**
     * ID of the user who performed the action (null for anonymous actions)
     */
    @Column(name = "user_id", columnDefinition = "CHAR(36)")
    private String userId;

    /**
     * Username of the user who performed the action (for quick reference)
     */
    @Column(name = "username", length = 30)
    private String username;

    /**
     * HTTP method of the request (POST, PUT, PATCH, DELETE, etc.)
     */
    @Column(name = "http_method", length = 10, nullable = false)
    private String httpMethod;

    /**
     * API endpoint that was called
     */
    @Column(name = "endpoint", length = 255, nullable = false)
    private String endpoint;

    /**
     * Request ID from response meta for tracing
     */
    @Column(name = "request_id", length = 36)
    private String requestId;

    /**
     * Result message from response status.message (changed from enum to varchar)
     */
    @Column(name = "result", length = 100, nullable = false)
    private String result;

    /**
     * HTTP status code of the response
     */
    @Column(name = "status_code")
    private Integer statusCode;

    /**
     * Error message if the operation failed
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * IP address from which the request originated
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User agent string from the request
     */
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    /**
     * Session ID if available
     */
    @Column(name = "session_id", length = 100)
    private String sessionId;

    /**
     * Duration of the operation in milliseconds
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * Timestamp when the action was performed
     */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    /**
     * Pre-persist hook to set ID and timestamp
     */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
