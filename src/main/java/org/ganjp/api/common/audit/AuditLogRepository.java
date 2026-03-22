package org.ganjp.api.common.audit;

import org.ganjp.api.common.audit.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for AuditLog entity.
 * Provides data access methods for simplified audit logs.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    /**
     * Find audit logs by user ID
     */
    Page<AuditLog> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    /**
     * Find audit logs by user ID within a time range
     */
    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    Page<AuditLog> findByUserIdAndTimestampBetween(
            @Param("userId") String userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * Find audit logs by HTTP method
     */
    Page<AuditLog> findByHttpMethodOrderByTimestampDesc(String httpMethod, Pageable pageable);

    /**
     * Find audit logs by result message (like pattern)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.result LIKE %:resultPattern% ORDER BY a.timestamp DESC")
    Page<AuditLog> findByResultContaining(@Param("resultPattern") String resultPattern, Pageable pageable);

    /**
     * Find failed operations for a specific user (based on result message patterns)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND (a.result LIKE '%failed%' OR a.result LIKE '%error%' OR a.errorMessage IS NOT NULL) ORDER BY a.timestamp DESC")
    Page<AuditLog> findFailedOperationsByUser(@Param("userId") String userId, Pageable pageable);

    /**
     * Find audit logs within a time range
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    Page<AuditLog> findByTimestampBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * Find audit logs by IP address
     */
    Page<AuditLog> findByIpAddressOrderByTimestampDesc(String ipAddress, Pageable pageable);

    /**
     * Count failed login attempts for a user within a time period
     * Updated to work with simplified schema
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.userId = :userId AND a.endpoint LIKE '%/auth/login%' AND (a.result LIKE '%failed%' OR a.statusCode = 401) AND a.timestamp >= :since")
    long countFailedLoginAttempts(@Param("userId") String userId, @Param("since") LocalDateTime since);

    /**
     * Count failed login attempts from an IP address within a time period
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.ipAddress = :ipAddress AND a.endpoint LIKE '%/auth/login%' AND (a.result LIKE '%failed%' OR a.statusCode = 401) AND a.timestamp >= :since")
    long countFailedLoginAttemptsByIp(@Param("ipAddress") String ipAddress, @Param("since") LocalDateTime since);

    /**
     * Find recent audit logs (last 24 hours)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findRecentAuditLogs(@Param("since") LocalDateTime since);

    /**
     * Count operations by user and endpoint pattern within a time period
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.userId = :userId AND a.endpoint LIKE :endpointPattern AND a.timestamp >= :since")
    long countOperationsByUserAndEndpoint(
            @Param("userId") String userId,
            @Param("endpointPattern") String endpointPattern,
            @Param("since") LocalDateTime since);

    /**
     * Find audit logs by multiple criteria (simplified)
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:httpMethod IS NULL OR a.httpMethod = :httpMethod) AND " +
           "(:resultPattern IS NULL OR a.result LIKE %:resultPattern%) AND " +
           "(:endpointPattern IS NULL OR a.endpoint LIKE %:endpointPattern%) AND " +
           "(:startTime IS NULL OR a.timestamp >= :startTime) AND " +
           "(:endTime IS NULL OR a.timestamp <= :endTime) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> findByCriteria(
            @Param("userId") String userId,
            @Param("httpMethod") String httpMethod,
            @Param("resultPattern") String resultPattern,
            @Param("endpointPattern") String endpointPattern,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * Find audit logs by enhanced criteria with additional filtering options
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:username IS NULL OR a.username LIKE %:username%) AND " +
           "(:httpMethod IS NULL OR a.httpMethod = :httpMethod) AND " +
           "(:endpoint IS NULL OR a.endpoint LIKE %:endpoint%) AND " +
           "(:result IS NULL OR a.result LIKE %:result%) AND " +
           "(:statusCode IS NULL OR a.statusCode = :statusCode) AND " +
           "(:ipAddress IS NULL OR a.ipAddress = :ipAddress) AND " +
           "(:minDurationMs IS NULL OR a.durationMs >= :minDurationMs) AND " +
           "(:maxDurationMs IS NULL OR a.durationMs <= :maxDurationMs) AND " +
           "(:startTime IS NULL OR a.timestamp >= :startTime) AND " +
           "(:endTime IS NULL OR a.timestamp <= :endTime) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> findByEnhancedCriteria(
            @Param("userId") String userId,
            @Param("username") String username,
            @Param("httpMethod") String httpMethod,
            @Param("endpoint") String endpoint,
            @Param("result") String result,
            @Param("statusCode") Integer statusCode,
            @Param("ipAddress") String ipAddress,
            @Param("minDurationMs") Long minDurationMs,
            @Param("maxDurationMs") Long maxDurationMs,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * Delete audit logs older than specified date (for cleanup)
     */
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :cutoffDate")
    void deleteOldAuditLogs(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count total audit logs
     */
    @Query("SELECT COUNT(a) FROM AuditLog a")
    long countTotalAuditLogs();

    /**
     * Get audit log statistics for dashboard (simplified)
     */
    @Query("SELECT a.httpMethod, a.result, COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since GROUP BY a.httpMethod, a.result")
    List<Object[]> getAuditStatistics(@Param("since") LocalDateTime since);
    
    /**
     * Find all audit logs ordered by timestamp in descending order
     */
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    /**
     * Find audit logs by request ID for correlation
     */
    Page<AuditLog> findByRequestIdOrderByTimestampDesc(String requestId, Pageable pageable);

    /**
     * Find audit logs by status code
     */
    Page<AuditLog> findByStatusCodeOrderByTimestampDesc(Integer statusCode, Pageable pageable);

    /**
     * Count operations by endpoint within a time period
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.endpoint = :endpoint AND a.timestamp >= :since")
    long countOperationsByEndpoint(@Param("endpoint") String endpoint, @Param("since") LocalDateTime since);
}
