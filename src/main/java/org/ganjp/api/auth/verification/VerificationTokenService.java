package org.ganjp.api.auth.verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.security.SecurityProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared service for creating and validating verification tokens.
 * Used by both password reset and email verification flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationTokenService {

    private final VerificationTokenRepository verificationTokenRepository;
    private final SecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Create a verification token for a user. Invalidates any existing unused tokens of the same type.
     */
    @Transactional
    public VerificationToken createToken(String userId, VerificationTokenType tokenType) {
        LocalDateTime now = LocalDateTime.now();

        // Invalidate any previous unused tokens of this type for this user
        verificationTokenRepository.invalidateTokensForUser(userId, tokenType, now);

        // Generate secure random token
        String tokenValue = generateSecureToken();

        // Calculate expiration
        long expirationMs = (tokenType == VerificationTokenType.PASSWORD_RESET)
                ? securityProperties.getVerification().getPasswordResetExpiration()
                : securityProperties.getVerification().getEmailVerificationExpiration();
        LocalDateTime expiresAt = now.plusSeconds(expirationMs / 1000);

        VerificationToken token = VerificationToken.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .tokenHash(hashToken(tokenValue))
                .tokenType(tokenType)
                .expiresAt(expiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();

        VerificationToken savedToken = verificationTokenRepository.save(token);
        savedToken.setTokenValue(tokenValue);

        log.debug("Created {} token for user {}, expires at {}", tokenType, userId, expiresAt);
        return savedToken;
    }

    /**
     * Validate a token value and return the entity if valid (unused and not expired).
     */
    @Transactional(readOnly = true)
    public Optional<VerificationToken> validateToken(String tokenValue, VerificationTokenType tokenType) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }

        String tokenHash = hashToken(tokenValue);
        return verificationTokenRepository.findValidToken(tokenHash, tokenType, LocalDateTime.now());
    }

    /**
     * Mark a token as used.
     */
    @Transactional
    public void markTokenUsed(VerificationToken token) {
        LocalDateTime now = LocalDateTime.now();
        token.setUsedAt(now);
        token.setUpdatedAt(now);
        verificationTokenRepository.save(token);
    }

    /**
     * Clean up expired tokens older than 7 days.
     */
    @Transactional
    public int cleanupExpiredTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int deleted = verificationTokenRepository.deleteExpiredTokens(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} expired verification tokens older than {}", deleted, cutoff);
        }
        return deleted;
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

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
