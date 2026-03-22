package org.ganjp.api.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.common.audit.AuditProperties;
import org.ganjp.api.common.audit.AuditService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for audit log maintenance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = true)
public class AuditLogScheduler {

    private final AuditService auditService;
    private final AuditProperties auditProperties;

    /**
     * Clean up old audit logs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldAuditLogs() {
        if (auditProperties.getRetentionDays() > 0) {
            log.info("Starting scheduled cleanup of audit logs older than {} days", 
                    auditProperties.getRetentionDays());
            
            try {
                auditService.cleanupOldAuditLogs(auditProperties.getRetentionDays());
                log.info("Scheduled audit log cleanup completed successfully");
            } catch (Exception e) {
                log.error("Failed to complete scheduled audit log cleanup", e);
            }
        } else {
            log.debug("Audit log cleanup skipped: retention is set to keep forever");
        }
    }
}
