# Auth Module — JWT Token Design

## 1. Token Strategy

The system uses a **dual-token architecture**:

| Aspect | Access Token | Refresh Token |
|--------|-------------|---------------|
| Format | JWT (signed) | Opaque (random Base64) |
| Lifetime | 30 minutes (1,800,000 ms) | 30 days (2,592,000,000 ms) |
| Storage (client) | Memory / HTTP header | Secure storage |
| Storage (server) | Stateless (no server-side storage) | SHA-256 hash in `auth_refresh_tokens` table |
| Purpose | Authorize API requests | Obtain new access tokens |
| Revocation | Token blacklist (`auth_token_blacklist`) | Set `is_revoked = true` in DB |

## 2. Access Token (JWT)

### 2.1 Structure

The access token is a standard JWT with three parts: Header, Payload, Signature.

**Header:**
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**Payload (Claims):**
```json
{
  "sub": "john_doe",
  "iss": "gjp-api-admin",
  "jti": "550e8400-e29b-41d4-a716-446655440000",
  "iat": 1711468800,
  "exp": 1711470600,
  "userId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "authorities": ["ROLE_ADMIN", "ROLE_USER"]
}
```

| Claim | Type | Description |
|-------|------|-------------|
| `sub` | Standard | Username (subject) |
| `iss` | Standard | Issuer identifier: `gjp-api-admin` |
| `jti` | Standard | Unique token ID (UUID) for blacklisting |
| `iat` | Standard | Issued-at timestamp |
| `exp` | Standard | Expiration timestamp (iat + 30 min) |
| `userId` | Custom | User UUID from `auth_users.id` |
| `authorities` | Custom | List of granted authorities (prefixed with `ROLE_`) |

### 2.2 Signing

| Property | Value |
|----------|-------|
| Algorithm | HMAC-SHA256 (`HS256`) |
| Key Format | Base64-encoded secret key |
| Key Source | `security.jwt.secret-key` in `application.yml` / environment variable |
| Library | io.jsonwebtoken (jjwt) |

### 2.3 Validation

On each request, the `JwtAuthenticationFilter` validates:

1. **Signature verification** — Key matches, token not tampered
2. **Issuer verification** — `iss` claim equals configured issuer (`gjp-api-admin`)
3. **Expiration check** — `exp` claim is in the future
4. **Username match** — `sub` claim matches loaded `UserDetails`
5. **Blacklist check** — `jti` claim not found in `auth_token_blacklist`

### 2.4 Token ID (jti) for Blacklisting

Every generated token includes a unique `jti` (JWT ID) claim — a UUID generated at token creation time. This enables individual token revocation:

```
Logout → Extract jti from access token → Insert into auth_token_blacklist →
         All subsequent requests with this token are rejected
```

Blacklist entries include the token's expiration time. Expired entries can be periodically purged since the token itself would fail expiration validation.

## 3. Refresh Token

### 3.1 Generation

The refresh token is **not a JWT**. It is a cryptographically secure random value:

```java
byte[] randomBytes = new byte[32]; // 256 bits of entropy
secureRandom.nextBytes(randomBytes);
String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
```

This produces a 43-character URL-safe Base64 string with 256 bits of entropy.

### 3.2 Secure Storage

The **plain-text refresh token is never stored in the database**. Only a SHA-256 hash is persisted:

```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] hash = digest.digest(tokenValue.getBytes(StandardCharsets.UTF_8));
String tokenHash = Base64.getEncoder().encodeToString(hash);
```

**Validation flow:**
1. Client sends plain refresh token
2. Server hashes the received token with SHA-256
3. Server queries `auth_refresh_tokens` for matching hash
4. If found, not expired, and not revoked → token is valid

This means a database compromise does not expose usable refresh tokens.

### 3.3 Token Rotation

On every refresh operation:

```
1. Validate old refresh token (hash lookup + expiry + revocation check)
2. Mark old token as revoked (is_revoked = true, revoked_at = now)
3. Generate new random refresh token
4. Store SHA-256 hash of new token
5. Generate new access token with updated authorities
6. Return both new tokens to client
```

**Why rotation matters:**
- Limits the lifetime of any single refresh token
- Detects token theft: if a stolen token is used after the legitimate user refreshed, it's already revoked
- Keeps user role/authority changes reflected in new access tokens

### 3.4 Database Schema

```sql
CREATE TABLE auth_refresh_tokens (
    id              CHAR(36) NOT NULL,          -- UUID primary key
    user_id         CHAR(36) NOT NULL,          -- FK to auth_users
    token_hash      VARCHAR(255) NOT NULL,      -- SHA-256 hash
    expires_at      TIMESTAMP NOT NULL,         -- Expiration time
    is_revoked      BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMP NULL,             -- When revoked
    last_used_at    TIMESTAMP NULL,             -- Last refresh time
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,

    INDEX idx_refresh_tokens_hash (token_hash),
    INDEX idx_refresh_tokens_user_valid (user_id, expires_at, is_revoked),
    INDEX idx_refresh_tokens_cleanup (expires_at, is_revoked)
);
```

## 4. Token Blacklist

### 4.1 Purpose

Access tokens are stateless JWTs — once issued, they are valid until expiration. The blacklist enables **immediate revocation** (e.g., on logout) before the natural expiration.

### 4.2 Schema

```sql
CREATE TABLE auth_token_blacklist (
    token_id    VARCHAR(255) NOT NULL,  -- JWT jti claim
    expires_at  TIMESTAMP NOT NULL,     -- Token expiration time

    PRIMARY KEY (token_id),
    INDEX idx_token_blacklist_expires_at (expires_at)
);
```

### 4.3 Lifecycle

1. **Blacklist**: On logout, extract `jti` from access token and insert into table
2. **Check**: On every authenticated request, check if `jti` is in blacklist
3. **Cleanup**: Periodically remove entries where `expires_at < NOW()` (token already expired naturally)

### 4.4 Duplicate Protection

To handle double-logout scenarios (e.g., rapid button clicks), the service checks `existsById()` before inserting:

```java
if (blacklistedTokenRepository.existsById(tokenId)) {
    log.debug("Token already blacklisted, skipping: {}", tokenId);
    return;
}
```

## 5. Security Considerations

| Concern | Mitigation |
|---------|-----------|
| Token theft | Short-lived access tokens (30 min), refresh token rotation |
| Database compromise | Refresh tokens stored as SHA-256 hashes, not plaintext |
| Replay attacks | Token blacklist for revoked access tokens |
| Privilege escalation | Authorities embedded in JWT, re-loaded on token refresh |
| Token leakage in logs | Tokens sanitized in audit log data |
| Caching of tokens | `Cache-Control: no-store` and `Pragma: no-cache` headers |
| Algorithm confusion | Signing key is typed to HMAC-SHA256; issuer validated on parse |
