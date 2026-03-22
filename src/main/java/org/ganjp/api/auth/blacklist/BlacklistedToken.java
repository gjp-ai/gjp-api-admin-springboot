package org.ganjp.api.auth.blacklist;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a blacklisted JWT access token.
 * Tokens are added here on logout/revocation and checked during authentication.
 * Expired entries are cleaned up periodically.
 */
@Entity
@Table(name = "auth_token_blacklist")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistedToken {

    @Id
    @Column(name = "token_id", length = 255)
    private String tokenId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
