package org.ganjp.api.auth.session;

import jakarta.annotation.PreDestroy;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service to track active users in memory.
 * Maintains a map of currently active users based on their JWT token activity.
 */
@Slf4j
@Service
public class ActiveUserService {

    // Map to store active users: userId -> SessionInfo
    private final Map<String, SessionInfo> activeUsers = new ConcurrentHashMap<>();
    
    private static final long SESSION_TIMEOUT_MINUTES = 30;
    private static final long CLEANUP_INTERVAL_MINUTES = 5;

    // Scheduled executor for cleanup task
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ActiveUserService() {
        scheduler.scheduleAtFixedRate(this::cleanupExpiredSessions,
                CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
        log.info("ActiveUserService initialized with session timeout: {} minutes", SESSION_TIMEOUT_MINUTES);
    }

    /**
     * Shut down the scheduler thread on application shutdown to allow clean exit.
     * Without this, the non-daemon scheduler thread prevents JVM termination.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down ActiveUserService scheduler");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Register a user as active (called when JWT token is validated)
     * 
     * @param userId User ID
     * @param username Username for logging purposes
     * @param userAgent User agent string from request
     * @param ipAddress IP address of the user
     */
    public void registerActiveUser(String userId, String username, String userAgent, String ipAddress) {
        SessionInfo sessionInfo = new SessionInfo(
            userId,
            username,
            LocalDateTime.now(),
            userAgent,
            ipAddress
        );
        
        activeUsers.put(userId, sessionInfo);
        log.debug("User {} ({}) registered as active from IP: {}", username, userId, ipAddress);
    }
    
    /**
     * Update the last activity time for an active user
     * 
     * @param userId User ID
     */
    public void updateLastActivity(String userId) {
        SessionInfo sessionInfo = activeUsers.get(userId);
        if (sessionInfo != null) {
            sessionInfo.setLastActivity(LocalDateTime.now());
            log.debug("Updated last activity for user: {}", userId);
        }
    }
    
    /**
     * Remove a user from active users (called when user logs out)
     * 
     * @param userId User ID
     */
    public void removeActiveUser(String userId) {
        SessionInfo removed = activeUsers.remove(userId);
        if (removed != null) {
            log.debug("User {} ({}) removed from active users", removed.getUsername(), userId);
        }
    }
    
    /**
     * Get the count of currently active users
     * 
     * @return Number of active users
     */
    public long getActiveUserCount() {
        return activeUsers.size();
    }
    
    /**
     * Get all active users (for admin purposes)
     * 
     * @return Map of active users
     */
    public Map<String, SessionInfo> getAllActiveUsers() {
        return new ConcurrentHashMap<>(activeUsers);
    }
    
    /**
     * Check if a user is currently active
     * 
     * @param userId User ID
     * @return true if user is active, false otherwise
     */
    public boolean isUserActive(String userId) {
        return activeUsers.containsKey(userId);
    }
    
    /**
     * Clean up expired sessions based on last activity time
     */
    private void cleanupExpiredSessions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(SESSION_TIMEOUT_MINUTES);
        
        activeUsers.entrySet().removeIf(entry -> {
            SessionInfo sessionInfo = entry.getValue();
            boolean isExpired = sessionInfo.getLastActivity().isBefore(cutoffTime);
            
            if (isExpired) {
                log.debug("Removing expired session for user: {} ({})", 
                    sessionInfo.getUsername(), entry.getKey());
            }
            
            return isExpired;
        });
        
        log.debug("Cleanup completed. Current active users: {}", activeUsers.size());
    }
    
    /**
     * Force cleanup of expired sessions (can be called manually)
     */
    public void forceCleanup() {
        cleanupExpiredSessions();
    }
    
    /**
     * Get session timeout in minutes
     * 
     * @return Session timeout in minutes
     */
    public long getSessionTimeoutMinutes() {
        return SESSION_TIMEOUT_MINUTES;
    }
    
    /**
     * Inner class to hold session information.
     */
    @Getter
    @Setter
    public static class SessionInfo {
        private final String userId;
        private final String username;
        private final LocalDateTime loginTime;
        private LocalDateTime lastActivity;
        private final String userAgent;
        private final String ipAddress;

        public SessionInfo(String userId, String username, LocalDateTime loginTime,
                          String userAgent, String ipAddress) {
            this.userId = userId;
            this.username = username;
            this.loginTime = loginTime;
            this.lastActivity = loginTime;
            this.userAgent = userAgent;
            this.ipAddress = ipAddress;
        }
    }
}
