package org.ganjp.api.auth.token;

import org.ganjp.api.auth.token.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing refresh tokens.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /**
     * Find a valid (non-revoked, non-expired) refresh token by its hash
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash AND rt.isRevoked = false AND rt.expiresAt > :now")
    Optional<RefreshToken> findValidTokenByHash(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    /**
     * Find all active (non-revoked, non-expired) refresh tokens for a user
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.userId = :userId AND rt.isRevoked = false AND rt.expiresAt > :now")
    List<RefreshToken> findActiveTokensByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);

    /**
     * Find all active tokens for a user (alias method for service compatibility)
     */
    default List<RefreshToken> findActiveTokensForUser(String userId, LocalDateTime now) {
        return findActiveTokensByUserId(userId, now);
    }

    /**
     * Revoke all refresh tokens for a user (logout from all devices)
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.userId = :userId AND rt.isRevoked = false")
    int revokeAllTokensForUser(@Param("userId") String userId);

    /**
     * Revoke a specific refresh token by its hash
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.tokenHash = :tokenHash")
    int revokeTokenByHash(@Param("tokenHash") String tokenHash);

    /**
     * Delete expired tokens (cleanup job)
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :cutoffDate")
    int deleteExpiredTokens(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count active tokens for a user (for session limiting)
     */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.userId = :userId AND rt.isRevoked = false AND rt.expiresAt > :now")
    long countActiveTokensForUser(@Param("userId") String userId, @Param("now") LocalDateTime now);

    /**
     * Find oldest active tokens for a user (for cleanup when exceeding max sessions)
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.userId = :userId AND rt.isRevoked = false AND rt.expiresAt > :now ORDER BY rt.createdAt ASC")
    List<RefreshToken> findOldestActiveTokensForUser(@Param("userId") String userId, @Param("now") LocalDateTime now);
}
