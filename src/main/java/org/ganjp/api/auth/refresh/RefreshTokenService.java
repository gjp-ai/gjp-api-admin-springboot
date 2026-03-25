package org.ganjp.api.auth.refresh;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.config.SecurityProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing refresh tokens.
 * Handles creation, validation, rotation, and revocation of refresh tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a new refresh token for a user
     */
    @Transactional
    public RefreshToken createRefreshToken(String userId) {
        // Generate random token value
        String tokenValue = generateSecureToken();
        
        // Calculate expiration time
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(securityProperties.getJwt().getRefreshExpiration() / 1000);
        
        // Create refresh token entity
        LocalDateTime now = LocalDateTime.now();
        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .tokenHash(hashToken(tokenValue))
                .expiresAt(expiresAt)
                .createdAt(now)
                .updatedAt(now)
                .isRevoked(false)
                .build();
        
        // Save to database
        RefreshToken savedToken = refreshTokenRepository.save(refreshToken);
        
        // Set the plain token value for return (not stored in DB)
        savedToken.setTokenValue(tokenValue);
        
        log.debug("Created refresh token for user: {}, expires at: {}", userId, expiresAt);
        return savedToken;
    }

    /**
     * Validate a refresh token and return the token entity if valid
     */
    @Transactional
    public Optional<RefreshToken> validateRefreshToken(String tokenValue) {
        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            log.debug("Empty token value provided for validation");
            return Optional.empty();
        }
        
        String tokenHash = hashToken(tokenValue);
        LocalDateTime now = LocalDateTime.now();
        
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findValidTokenByHash(tokenHash, now);
        
        if (tokenOpt.isPresent()) {
            RefreshToken token = tokenOpt.get();
            
            // Update last used timestamp
            token.setLastUsedAt(now);
            token.setUpdatedAt(now);
            refreshTokenRepository.save(token);
            
            log.debug("Refresh token validated successfully for user: {}", token.getUserId());
            return tokenOpt;
        } else {
            log.debug("Invalid or expired refresh token");
            return Optional.empty();
        }
    }

    /**
     * Rotate a refresh token - revoke the old one and create a new one
     */
    @Transactional
    public RefreshToken rotateRefreshToken(String oldTokenValue, String userId) {
        // Validate and revoke the old token
        Optional<RefreshToken> oldTokenOpt = validateRefreshToken(oldTokenValue);
        if (oldTokenOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid refresh token provided for rotation");
        }
        
        RefreshToken oldToken = oldTokenOpt.get();
        
        // Ensure the token belongs to the specified user
        if (!oldToken.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Refresh token does not belong to the specified user");
        }
        
        // Revoke the old token
        oldToken.setIsRevoked(true);
        oldToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(oldToken);
        
        // Create new refresh token
        RefreshToken newToken = createRefreshToken(userId);
        
        log.debug("Rotated refresh token for user: {}", userId);
        return newToken;
    }

    /**
     * Revoke a refresh token
     */
    @Transactional
    public boolean revokeRefreshToken(String tokenValue) {
        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            return false;
        }
        
        String tokenHash = hashToken(tokenValue);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findValidTokenByHash(tokenHash, LocalDateTime.now());
        
        if (tokenOpt.isPresent()) {
            RefreshToken token = tokenOpt.get();
            token.setIsRevoked(true);
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
            
            log.debug("Revoked refresh token for user: {}", token.getUserId());
            return true;
        }
        
        return false;
    }

    /**
     * Revoke all refresh tokens for a user
     */
    @Transactional
    public int revokeAllUserTokens(String userId) {
        LocalDateTime now = LocalDateTime.now();
        List<RefreshToken> activeTokens = refreshTokenRepository.findActiveTokensForUser(userId, now);
        
        int revokedCount = 0;
        for (RefreshToken token : activeTokens) {
            token.setIsRevoked(true);
            token.setRevokedAt(now);
            refreshTokenRepository.save(token);
            revokedCount++;
        }
        
        if (revokedCount > 0) {
            log.debug("Revoked {} refresh tokens for user: {}", revokedCount, userId);
        }
        
        return revokedCount;
    }

    /**
     * Clean up expired tokens from the database
     */
    @Transactional
    public int cleanupExpiredTokens() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7); // Keep expired tokens for 7 days for audit
        int deletedCount = refreshTokenRepository.deleteExpiredTokens(cutoffTime);
        
        if (deletedCount > 0) {
            log.info("Cleaned up {} expired refresh tokens older than {}", deletedCount, cutoffTime);
        }
        
        return deletedCount;
    }

    /**
     * Generate a cryptographically secure random token
     */
    private String generateSecureToken() {
        byte[] randomBytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Hash a token value for secure storage
     */
    private String hashToken(String tokenValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
