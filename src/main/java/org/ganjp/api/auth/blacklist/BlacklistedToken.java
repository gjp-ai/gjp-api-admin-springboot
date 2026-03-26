package org.ganjp.api.auth.blacklist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing a blacklisted JWT access token.
 * Tokens are added here on logout/revocation and checked during authentication.
 * Expired entries are cleaned up periodically.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auth_token_blacklist")
public class BlacklistedToken {

    @Id
    @Column(name = "token_id", length = 255)
    private String tokenId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlacklistedToken that = (BlacklistedToken) o;
        return tokenId != null && Objects.equals(tokenId, that.tokenId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenId);
    }
}
