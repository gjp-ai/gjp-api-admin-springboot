package org.ganjp.api.auth.blacklist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Database-backed token blacklist service.
 * Blacklisted tokens survive server restarts and work in clustered deployments.
 * Expired entries are cleaned up every 30 minutes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    /**
     * Add a token to the blacklist
     * @param tokenId Unique identifier of the token (jti claim)
     * @param expirationTime Token expiration time in milliseconds (from exp claim)
     */
    @Transactional
    public void blacklistToken(String tokenId, long expirationTime) {
        if (tokenId == null || tokenId.trim().isEmpty()) {
            return;
        }

        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(expirationTime), ZoneId.systemDefault());

        BlacklistedToken entry = BlacklistedToken.builder()
                .tokenId(tokenId)
                .expiresAt(expiresAt)
                .build();

        blacklistedTokenRepository.save(entry);
        log.debug("Token blacklisted: {} (expires at: {})", tokenId, expiresAt);
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
        return blacklistedTokenRepository.existsById(tokenId);
    }

    /**
     * Remove expired tokens from the blacklist (runs every 30 minutes)
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    @Transactional
    public void cleanupExpiredTokens() {
        int removed = blacklistedTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        if (removed > 0) {
            log.info("Cleaned up {} expired tokens from blacklist", removed);
        }
    }

    /**
     * Get current blacklist size (for monitoring/debugging)
     */
    public long getBlacklistSize() {
        return blacklistedTokenRepository.count();
    }

    /**
     * Clear all blacklisted tokens (for testing or admin purposes)
     */
    @Transactional
    public void clearBlacklist() {
        long size = blacklistedTokenRepository.count();
        blacklistedTokenRepository.deleteAll();
        log.warn("Blacklist cleared. Removed {} tokens", size);
    }
}
