# Auth Module — Security Features

## 1. Password Security

### 1.1 Hashing

All passwords are hashed using **BCrypt** (via Spring Security's `PasswordEncoder`). BCrypt includes:
- Automatic salt generation (128-bit random salt per hash)
- Configurable work factor (default: 10 rounds = 2^10 iterations)
- Resistance to rainbow table attacks

The plain-text password is never stored, logged, or returned in API responses.

### 1.2 Password Policy

A **unified password policy** is enforced across all entry points (registration, user creation, user update, profile password change, admin password reset):

| Rule | Constraint |
|------|-----------|
| Minimum length | 8 characters |
| Maximum length | 128 characters |
| Uppercase letter | At least one required |
| Lowercase letter | At least one required |
| Digit | At least one required |
| Special character | At least one of `@$!%*?&#^+=` |
| Whitespace | Not allowed |

**Validation regex:**
```regex
^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#^+=])(?=\S+$).+$
```

This policy is enforced via Jakarta Bean Validation annotations on all 5 DTOs:
- `RegisterRequest`
- `UserCreateRequest`
- `UserUpdateRequest`
- `ChangePasswordRequest`
- `AdminResetPasswordRequest`

## 2. Rate Limiting

### 2.1 Login Rate Limiter

An in-memory rate limiter protects the login endpoint from brute-force attacks:

| Parameter | Value |
|-----------|-------|
| Max attempts | 10 per minute |
| Window | 60 seconds (sliding) |
| Key | Client IP address |
| Scope | Per-instance (in-memory) |

**Behavior:**
- Each login attempt (successful or failed) increments the counter for the client's IP
- When the counter exceeds 10 within a 60-second window, subsequent attempts receive HTTP 429
- Counters automatically expire after the window elapses

### 2.2 Limitations

> **Note**: The current rate limiter is in-memory and scoped to a single application instance. In a multi-instance deployment behind a load balancer, each instance maintains its own counter. For production multi-instance deployments, consider a distributed rate limiter backed by Redis or a similar shared store.

### 2.3 IP Address Extraction

The rate limiter uses `IpAddressUtils.getClientIp()` which checks headers in order:
1. `X-Forwarded-For` (first IP in comma-separated list)
2. `X-Real-IP`
3. `Proxy-Client-IP`
4. `WL-Proxy-Client-IP`
5. `HTTP_CLIENT_IP`
6. `HTTP_X_FORWARDED_FOR`
7. `request.getRemoteAddr()` (fallback)

## 3. Account Locking

### 3.1 Mechanism

The system automatically locks user accounts after excessive failed login attempts:

| Field | Purpose |
|-------|---------|
| `failed_login_attempts` | Counter incremented on each failed login |
| `last_failed_login_at` | Timestamp of the most recent failed attempt |
| `account_locked_until` | Timestamp until which the account is locked |
| `account_status` | Set to `locked` when threshold exceeded |

### 3.2 Lifecycle

```
Failed login attempt
  ├─ Increment failed_login_attempts
  ├─ Set last_failed_login_at = now
  ├─ If attempts >= threshold:
  │    ├─ Set account_status = 'locked'
  │    └─ Set account_locked_until = now + lock_duration
  └─ Return 401

Subsequent login attempt (while locked)
  ├─ Check account_locked_until > now
  ├─ If still locked: reject with "Account is locked until ..."
  └─ If lock expired:
       ├─ Reset account_status = 'active'
       ├─ Reset failed_login_attempts = 0
       └─ Proceed with normal authentication

Successful login
  ├─ Reset failed_login_attempts = 0
  ├─ Set last_login_at = now
  └─ Set last_login_ip = client IP
```

### 3.3 Transaction Isolation

Failed login recording uses `@Transactional(propagation = Propagation.REQUIRES_NEW)` to ensure the failure counter is persisted independently of the main authentication transaction. This prevents counter updates from being lost if the outer transaction rolls back.

## 4. Token Blacklisting

See [03-jwt-token-design.md](03-jwt-token-design.md) Section 4 for detailed blacklisting design.

**Summary:**
- Access tokens are blacklisted by their `jti` (JWT ID) claim on logout
- Blacklist is database-backed (`auth_token_blacklist` table) — survives restarts
- Every authenticated request checks the blacklist
- Duplicate blacklist entries are handled gracefully (idempotent)

## 5. System Role Protection

System roles (marked with `is_system_role = TRUE`) are protected from modification or deletion:

| Operation | Protection |
|-----------|-----------|
| Update (PUT) | `IllegalStateException: "Cannot modify system role: {code}"` |
| Update (PATCH) | `IllegalStateException: "Cannot modify system role: {code}"` |
| Delete | `IllegalStateException: "Cannot delete system role: {code}"` |

Additionally, role deletion is blocked if:
- Any users currently have the role assigned
- The role has child roles in the hierarchy

## 6. Authorization Model

### 6.1 Method-Level Security

Every controller endpoint has a `@PreAuthorize` annotation:

```java
// Read operations: ADMIN or SUPER_ADMIN
@PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")

// Write operations: SUPER_ADMIN only
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")

// Profile endpoints: any authenticated user
@PreAuthorize("isAuthenticated()")
```

### 6.2 Authority Mapping

Authorities are embedded in the JWT access token with the `ROLE_` prefix:

```
Database role code: ADMIN → JWT authority: ROLE_ADMIN
Database role code: SUPER_ADMIN → JWT authority: ROLE_SUPER_ADMIN
```

### 6.3 Endpoint Authorization Matrix

| Endpoint Group | GET | POST | PUT | PATCH | DELETE |
|---------------|-----|------|-----|-------|--------|
| `/v1/users` | ADMIN, SUPER_ADMIN | ADMIN, SUPER_ADMIN | ADMIN, SUPER_ADMIN | ADMIN, SUPER_ADMIN | SUPER_ADMIN |
| `/v1/roles` | ADMIN, SUPER_ADMIN | SUPER_ADMIN | SUPER_ADMIN | SUPER_ADMIN | SUPER_ADMIN |
| `/v1/profile` | Authenticated | — | Authenticated | — | — |
| `/v1/admin/sessions` | ADMIN, SUPER_ADMIN | SUPER_ADMIN | — | — | — |
| `/v1/auth/tokens` | — | Public | Public | — | Authenticated |
| `/v1/register` | — | Public | — | — | — |

## 7. Soft Delete

Users are never physically deleted from the database. The delete operation performs a soft delete:

```java
user.setActive(false);
user.setAccountStatus(AccountStatus.suspended);
```

Additionally:
- All refresh tokens for the user are revoked
- The user is removed from active session tracking
- The audit trail is preserved (created_by, updated_by, timestamps)

## 8. Input Validation

### 8.1 Validation Annotations

All DTOs use Jakarta Bean Validation:

| Field | Constraints |
|-------|------------|
| Username | `@NotBlank`, `@Size(3-30)`, `@Pattern(^[A-Za-z0-9._-]{3,30}$)` |
| Email | `@Email`, `@Size(max=128)` |
| Mobile Country Code | `@Pattern(^[1-9]\d{0,3}$)` |
| Mobile Number | `@Pattern(^\d{4,15}$)` |
| Password | `@Size(8-128)`, `@Pattern(complex)` |
| Nickname | `@Size(max=30)` |

### 8.2 Cross-Field Validation

Custom `@AssertTrue` validators ensure:
- **Mobile info**: Both country code and number must be provided together, or neither
- **Contact method**: At least one contact method (email or mobile) is required for registration

### 8.3 Database-Level Validation

MySQL CHECK constraints provide a second layer of defense:

```sql
CONSTRAINT chk_auth_users_username_fmt CHECK (username REGEXP '^[A-Za-z0-9._-]{3,30}$')
CONSTRAINT chk_users_email_fmt CHECK (email IS NULL OR email REGEXP '...')
CONSTRAINT chk_auth_users_mobile_country_code_fmt CHECK (...)
CONSTRAINT chk_auth_users_mobile_number_fmt CHECK (...)
CONSTRAINT chk_auth_roles_code_format CHECK (code REGEXP '^[A-Z][A-Z0-9_]*$')
```

## 9. CORS Configuration

CORS is configured with specific allowed origins (no wildcards):

```yaml
security:
  cors:
    allowed-origins:
      - https://ganjianping.com
      - https://www.ganjianping.com
      - http://localhost:8082
      - http://127.0.0.1:8082
      - http://localhost:3000
```

## 10. Sensitive Data Protection

| Data | Protection |
|------|-----------|
| Passwords | Hashed with BCrypt, never logged or returned |
| Access tokens | Not logged in audit records (sanitized) |
| Refresh tokens | Not stored in DB (only SHA-256 hash), not logged |
| Login requests | Password stripped before audit logging |
| Error responses | Internal error details suppressed in catch-all handler |
| Audit config | `include-sensitive-data: false` by default |
