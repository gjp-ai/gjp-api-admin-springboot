package org.ganjp.api.auth.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, String> {

    /**
     * Find a valid (unused, non-expired) token by its hash and type.
     */
    @Query("SELECT vt FROM VerificationToken vt WHERE vt.tokenHash = :tokenHash " +
           "AND vt.tokenType = :tokenType AND vt.usedAt IS NULL AND vt.expiresAt > :now")
    Optional<VerificationToken> findValidToken(@Param("tokenHash") String tokenHash,
                                               @Param("tokenType") VerificationTokenType tokenType,
                                               @Param("now") LocalDateTime now);

    /**
     * Invalidate all unused tokens of a given type for a user (e.g., when a new token is issued).
     */
    @Modifying
    @Query("UPDATE VerificationToken vt SET vt.usedAt = :now, vt.updatedAt = :now " +
           "WHERE vt.userId = :userId AND vt.tokenType = :tokenType AND vt.usedAt IS NULL")
    int invalidateTokensForUser(@Param("userId") String userId,
                                @Param("tokenType") VerificationTokenType tokenType,
                                @Param("now") LocalDateTime now);

    /**
     * Delete expired tokens older than the given cutoff time.
     */
    @Modifying
    @Query("DELETE FROM VerificationToken vt WHERE vt.expiresAt < :cutoff")
    int deleteExpiredTokens(@Param("cutoff") LocalDateTime cutoff);
}
