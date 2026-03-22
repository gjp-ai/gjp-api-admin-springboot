package org.ganjp.api.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.common.audit.AuditLog;
import org.ganjp.api.common.audit.AuditLogRepository;
import org.ganjp.api.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for querying audit logs.
 * Provides methods for retrieving and analyzing audit data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Find audit logs with multiple criteria (simplified)
     */
    public Page<AuditLog> findAuditLogs(
            String userId,
            String httpMethod,
            String resultPattern,
            String endpointPattern,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Pageable pageable) {

        return auditLogRepository.findByCriteria(
                userId, httpMethod, resultPattern, endpointPattern, startTime, endTime, pageable);
    }

    /**
     * Find audit logs with enhanced criteria including additional filtering options
     */
    public Page<AuditLog> findAuditLogsEnhanced(
            String userId,
            String username,
            String httpMethod,
            String endpoint,
            String result,
            Integer statusCode,
            String ipAddress,
            Long minDurationMs,
            Long maxDurationMs,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Pageable pageable) {

        return auditLogRepository.findByEnhancedCriteria(
                userId, username, httpMethod, endpoint, result, statusCode, ipAddress,
                minDurationMs, maxDurationMs, startTime, endTime, pageable);
    }

    /**
     * Find audit logs for a specific user
     */
    public Page<AuditLog> findUserAuditLogs(String userId, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        if (startTime != null && endTime != null) {
            return auditLogRepository.findByUserIdAndTimestampBetween(userId, startTime, endTime, pageable);
        } else {
            return auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
        }
    }

    /**
     * Find failed operations for a user
     */
    public Page<AuditLog> findFailedOperationsByUser(String userId, Pageable pageable) {
        return auditLogRepository.findFailedOperationsByUser(userId, pageable);
    }

    /**
     * Find audit logs for a specific endpoint pattern
     */
    public Page<AuditLog> findAuditLogsByEndpoint(String endpointPattern, Pageable pageable) {
        return auditLogRepository.findByCriteria(null, null, null, endpointPattern, null, null, pageable);
    }

    /**
     * Find audit logs by IP address
     */
    public Page<AuditLog> findAuditLogsByIpAddress(String ipAddress, Pageable pageable) {
        return auditLogRepository.findByIpAddressOrderByTimestampDesc(ipAddress, pageable);
    }

    /**
     * Find recent audit logs (last 24 hours)
     */
    public List<AuditLog> findRecentAuditLogs() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return auditLogRepository.findRecentAuditLogs(since);
    }

    /**
     * Get audit log by ID
     */
    public AuditLog findAuditLogById(String id) {
        return auditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AuditLog", "id", id));
    }

    /**
     * Count failed login attempts for a user within specified hours
     */
    public long countFailedLoginAttempts(String userId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return auditLogRepository.countFailedLoginAttempts(userId, since);
    }

    /**
     * Count failed login attempts from an IP address within specified hours
     */
    public long countFailedLoginAttemptsByIp(String ipAddress, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return auditLogRepository.countFailedLoginAttemptsByIp(ipAddress, since);
    }

    /**
     * Get audit statistics for dashboard
     */
    public Map<String, Object> getAuditStatistics(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        
        Map<String, Object> statistics = new HashMap<>();
        
        // Get total count
        long totalLogs = auditLogRepository.countTotalAuditLogs();
        statistics.put("totalLogs", totalLogs);
        
        // Get statistics by HTTP method and result
        List<Object[]> rawStats = auditLogRepository.getAuditStatistics(since);
        
        Map<String, Map<String, Long>> methodResultStats = new HashMap<>();
        long totalSuccessful = 0;
        long totalFailed = 0;
        
        for (Object[] row : rawStats) {
            String httpMethod = (String) row[0];
            String result = (String) row[1];
            Long count = (Long) row[2];
            
            methodResultStats.computeIfAbsent(httpMethod, k -> new HashMap<>())
                           .put(result, count);
            
            if (result != null && (result.toLowerCase().contains("success") || !result.toLowerCase().contains("fail"))) {
                totalSuccessful += count;
            } else {
                totalFailed += count;
            }
        }
        
        statistics.put("methodResultStats", methodResultStats);
        statistics.put("totalSuccessful", totalSuccessful);
        statistics.put("totalFailed", totalFailed);
        statistics.put("periodDays", days);
        statistics.put("generatedAt", LocalDateTime.now());
        
        // Calculate success rate
        long total = totalSuccessful + totalFailed;
        double successRate = total > 0 ? (double) totalSuccessful / total * 100 : 0.0;
        statistics.put("successRate", Math.round(successRate * 100.0) / 100.0);
        
        return statistics;
    }

    /**
     * Get count of operations by user and endpoint pattern within specified hours
     */
    public long countOperationsByUserAndEndpoint(String userId, String endpointPattern, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return auditLogRepository.countOperationsByUserAndEndpoint(userId, endpointPattern, since);
    }

    /**
     * Check if a user has exceeded the operation rate limit for an endpoint
     */
    public boolean hasExceededRateLimit(String userId, String endpointPattern, int maxOperations, int hours) {
        long count = countOperationsByUserAndEndpoint(userId, endpointPattern, hours);
        return count >= maxOperations;
    }

    /**
     * Clean up old audit logs
     */
    @Transactional
    public long cleanupOldAuditLogs(int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        
        // Count logs to be deleted
        Page<AuditLog> logsToDelete = auditLogRepository.findByTimestampBetween(
                LocalDateTime.MIN, cutoffDate, Pageable.unpaged());
        long countToDelete = logsToDelete.getTotalElements();
        
        // Delete old logs
        auditLogRepository.deleteOldAuditLogs(cutoffDate);
        
        log.info("Cleaned up {} audit logs older than {} days", countToDelete, retentionDays);
        return countToDelete;
    }

    /**
     * Get audit logs for security analysis (suspicious activities)
     */
    public Map<String, Object> getSecurityAnalysis(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Map<String, Object> analysis = new HashMap<>();
        
        // Find IPs with multiple failed login attempts
        // This would require a custom query, simplified for now
        analysis.put("analysisTimestamp", LocalDateTime.now());
        analysis.put("analysisPeriodHours", hours);
        
        return analysis;
    }

    /**
     * Export audit logs for compliance (simplified version)
     */
    public List<AuditLog> exportAuditLogs(LocalDateTime startTime, LocalDateTime endTime) {
        // For large datasets, this should be paginated or streamed
        return auditLogRepository.findByTimestampBetween(startTime, endTime, Pageable.unpaged()).getContent();
    }

    /**
     * Find audit logs by user ID
     */
    public Page<AuditLog> findAuditLogsByUserId(String userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }

    /**
     * Find all audit logs
     */
    public Page<AuditLog> findAllAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    /**
     * Find audit logs by HTTP method
     */
    public Page<AuditLog> findAuditLogsByHttpMethod(String httpMethod, Pageable pageable) {
        return auditLogRepository.findByHttpMethodOrderByTimestampDesc(httpMethod, pageable);
    }

    /**
     * Find audit logs by result pattern
     */
    public Page<AuditLog> findAuditLogsByResult(String resultPattern, Pageable pageable) {
        return auditLogRepository.findByResultContaining(resultPattern, pageable);
    }
}
