package org.ganjp.api.auth.token;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * High-performance in-memory token blacklist service.
 * This service maintains a blacklist of invalidated JWT tokens without requiring database storage.
 * Tokens are automatically cleaned up when they expire to prevent memory leaks.
 */
@Slf4j
@Service
public class TokenBlacklistService {

    // In-memory store for blacklisted tokens with their expiration times
    private final ConcurrentHashMap<String, Long> blacklistedTokens = new ConcurrentHashMap<>();
    
    // Scheduled executor for cleanup tasks
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public TokenBlacklistService() {
        // Clean up expired tokens every 30 minutes to prevent memory leaks
        scheduler.scheduleAtFixedRate(this::cleanupExpiredTokens, 30, 30, TimeUnit.MINUTES);
        log.info("TokenBlacklistService initialized with automatic cleanup every 30 minutes");
    }
    
    /**
     * Add a token to the blacklist
     * @param tokenId Unique identifier of the token (jti claim)
     * @param expirationTime Token expiration time in milliseconds (from exp claim)
     */
    public void blacklistToken(String tokenId, long expirationTime) {
        if (tokenId != null && !tokenId.trim().isEmpty()) {
            blacklistedTokens.put(tokenId, expirationTime);
            log.debug("Token blacklisted: {} (expires at: {})", tokenId, expirationTime);
        }
    }
    
    /**
     * Check if a token is blacklisted
     * @param tokenId Unique identifier of the token (jti claim)
     * @return true if the token is blacklisted, false otherwise
     */
    public boolean isTokenBlacklisted(String tokenId) {
        if (tokenId == null || tokenId.trim().isEmpty()) {
            return false;
        }
        
        Long expirationTime = blacklistedTokens.get(tokenId);
        if (expirationTime == null) {
            return false; // Token not in blacklist
        }
        
        // Check if the blacklisted token has expired
        if (System.currentTimeMillis() > expirationTime) {
            // Token has expired, remove it from blacklist and return false
            blacklistedTokens.remove(tokenId);
            log.debug("Expired token removed from blacklist: {}", tokenId);
            return false;
        }
        
        log.debug("Token is blacklisted: {}", tokenId);
        return true;
    }
    
    /**
     * Remove expired tokens from the blacklist to prevent memory leaks
     */
    private void cleanupExpiredTokens() {
        long currentTime = System.currentTimeMillis();
        
        // Count expired tokens and collect them for removal
        long expiredCount = blacklistedTokens.entrySet().stream()
                .filter(entry -> currentTime > entry.getValue())
                .count();
        
        // Remove all tokens that have expired
        blacklistedTokens.entrySet().removeIf(entry -> currentTime > entry.getValue());
        
        if (expiredCount > 0) {
            log.info("Cleaned up {} expired tokens from blacklist. Current blacklist size: {}", 
                    expiredCount, blacklistedTokens.size());
        } else {
            log.debug("No expired tokens to clean up. Current blacklist size: {}", blacklistedTokens.size());
        }
    }
    
    /**
     * Get current blacklist size (for monitoring/debugging)
     */
    public int getBlacklistSize() {
        return blacklistedTokens.size();
    }
    
    /**
     * Clear all blacklisted tokens (for testing or admin purposes)
     */
    public void clearBlacklist() {
        int size = blacklistedTokens.size();
        blacklistedTokens.clear();
        log.warn("Blacklist cleared. Removed {} tokens", size);
    }
    
    /**
     * Shutdown the cleanup scheduler (called on application shutdown)
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("TokenBlacklistService shutdown completed");
    }
}
