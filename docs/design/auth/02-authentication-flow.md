# Auth Module — Authentication Flow

## 1. Overview

The authentication system implements a **dual-token strategy** using JWT access tokens and opaque refresh tokens. This design balances security (short-lived access tokens) with user experience (seamless token refresh without re-login).

| Token Type | Storage | Lifetime | Purpose |
|-----------|---------|----------|---------|
| Access Token | JWT (stateless) | 30 minutes | API authorization |
| Refresh Token | Opaque (DB-backed) | 30 days | Obtain new access tokens |

## 2. Registration Flow

```
Client                           Server
  │                                │
  │  POST /v1/register             │
  │  { username, email, password } │
  │ ──────────────────────────────►│
  │                                ├─ Validate input (Bean Validation)
  │                                ├─ Check username uniqueness
  │                                ├─ Check email uniqueness
  │                                ├─ Check mobile uniqueness (if provided)
  │                                ├─ Hash password with BCrypt
  │                                ├─ Create user (status: pending_verification)
  │                                ├─ Assign default ROLE_USER
  │                                │
  │  201 Created                   │
  │  { id, username, email, ... }  │
  │ ◄──────────────────────────────│
```

**Key behaviors:**
- At least one contact method (email or mobile) is required
- If both mobile country code and mobile number are provided, they must both be present
- Default account status is `pending_verification`
- Default role is `ROLE_USER`
- Password is hashed with BCrypt before storage

## 3. Login Flow

```
Client                           Server
  │                                │
  │  POST /v1/auth/tokens          │
  │  { username|email|mobile,      │
  │    password }                  │
  │ ──────────────────────────────►│
  │                                ├─ Validate exactly one login method
  │                                ├─ Rate limit check (10 attempts/60s per IP)
  │                                ├─ AuthenticationManager.authenticate()
  │                                │   └─ CustomAuthenticationProvider
  │                                │       ├─ Resolve user by username/email/mobile
  │                                │       ├─ Verify password (BCrypt)
  │                                │       ├─ Check account status (active, not locked)
  │                                │       └─ Check account lock expiry
  │                                ├─ On success:
  │                                │   ├─ Reset failed login attempts to 0
  │                                │   ├─ Update last_login_at, last_login_ip
  │                                │   ├─ Load active user roles
  │                                │   ├─ Generate JWT access token (30 min)
  │                                │   ├─ Generate opaque refresh token (30 days)
  │                                │   │   └─ Store SHA-256 hash in DB
  │                                │   └─ Return both tokens
  │                                ├─ On failure:
  │                                │   ├─ Increment failed_login_attempts
  │                                │   ├─ Record last_failed_login_at
  │                                │   ├─ Lock account if threshold exceeded
  │                                │   └─ Return 401
  │                                │
  │  200 OK                        │
  │  { accessToken, refreshToken,  │
  │    tokenType, expiresIn,       │
  │    username, email, roleCodes, │
  │    accountStatus, ... }        │
  │ ◄──────────────────────────────│
```

### 3.1 Multi-Method Authentication

The `CustomAuthenticationProvider` supports three login methods:

| Method | Principal Format | Resolution |
|--------|-----------------|------------|
| Username | `john_doe` | `findByUsername()` |
| Email | `john@example.com` | `findByEmail()` |
| Mobile | `65-98765432` | `findByMobileCountryCodeAndMobileNumber()` |

The `LoginRequest` validates that exactly one method is provided (mutual exclusivity).

### 3.2 Account Lock Mechanism

When consecutive failed login attempts exceed the configured threshold:
1. `account_status` is set to `locked`
2. `account_locked_until` is set to a future timestamp
3. Subsequent login attempts are rejected until the lock expires
4. A successful login after lock expiry resets all counters

### 3.3 Login Failure Recording

Failed login attempts are recorded using `@Transactional(propagation = REQUIRES_NEW)` to ensure the failure counter is persisted even if the outer transaction rolls back.

## 4. Token Refresh Flow

```
Client                           Server
  │                                │
  │  PUT /v1/auth/tokens           │
  │  { refreshToken }             │
  │ ──────────────────────────────►│
  │                                ├─ Hash provided token with SHA-256
  │                                ├─ Look up token hash in DB
  │                                ├─ Validate: not expired, not revoked
  │                                ├─ Verify user is still active
  │                                ├─ Revoke old refresh token
  │                                ├─ Generate new refresh token (rotation)
  │                                │   └─ Store new SHA-256 hash in DB
  │                                ├─ Reload user roles (picks up changes)
  │                                ├─ Generate new access token
  │                                │
  │  200 OK                        │
  │  { accessToken, refreshToken,  │
  │    tokenType, expiresIn }      │
  │ ◄──────────────────────────────│
```

**Token rotation** is a critical security feature:
- Each refresh operation invalidates the old refresh token
- A new refresh token is issued alongside the new access token
- If a stolen refresh token is used after the legitimate user has refreshed, the stolen token is already revoked
- This limits the damage window of a compromised refresh token

## 5. Logout Flow

```
Client                           Server
  │                                │
  │  DELETE /v1/auth/tokens        │
  │  Authorization: Bearer <jwt>   │
  │  { refreshToken?,              │
  │    logoutFromAllDevices? }     │
  │ ──────────────────────────────►│
  │                                ├─ Extract access token from header
  │                                ├─ Blacklist access token (by jti claim)
  │                                │   └─ Store in auth_token_blacklist table
  │                                ├─ If logoutFromAllDevices:
  │                                │   └─ Revoke ALL refresh tokens for user
  │                                ├─ Else if refreshToken provided:
  │                                │   └─ Revoke the specific refresh token
  │                                ├─ Remove from active user tracking
  │                                ├─ Clear SecurityContext
  │                                │
  │  200 OK                        │
  │  { message: "Logout successful │
  │    - all tokens revoked" }     │
  │ ◄──────────────────────────────│
```

**Key behaviors:**
- Access token blacklisting survives server restarts (database-backed)
- Blacklist entries are automatically cleaned up after token expiration
- Double-logout is handled gracefully (duplicate blacklist check before insert)
- `logoutFromAllDevices: true` revokes all refresh tokens across all sessions

## 6. Email Verification Flow

After registration, the user's account status is `pending_verification`. The email verification flow allows users to activate their account.

### 6.1 Send Verification Token (triggered after registration)

```
Client                           Server
  │                                │
  │  POST /v1/auth/email/          │
  │       resend-verification      │
  │  { email }                     │
  │ ──────────────────────────────►│
  │                                ├─ Find user by email
  │                                ├─ Validate account is pending_verification
  │                                ├─ Invalidate any existing verification tokens
  │                                ├─ Generate 32-byte SecureRandom token
  │                                ├─ Store SHA-256 hash in auth_verification_tokens
  │                                │   (type: EMAIL_VERIFICATION, expires: 24h)
  │                                ├─ Log plain-text token (dev only; send via email in prod)
  │                                │
  │  200 OK                        │
  │  { message: "Verification      │
  │    email sent" }               │
  │ ◄──────────────────────────────│
```

### 6.2 Verify Email

```
Client                           Server
  │                                │
  │  POST /v1/auth/email/verify    │
  │  { token }                     │
  │ ──────────────────────────────►│
  │                                ├─ Hash provided token with SHA-256
  │                                ├─ Look up valid token in DB
  │                                │   (matching hash, type, not used, not expired)
  │                                ├─ Mark token as used (set used_at)
  │                                ├─ Set user account status to active
  │                                ├─ Set updatedBy to user ID
  │                                │
  │  200 OK                        │
  │  { message: "Email verified    │
  │    successfully" }             │
  │ ◄──────────────────────────────│
```

**Key behaviors:**
- Verification tokens expire after 24 hours (configurable via `security.verification.email-verification-expiration`)
- Requesting a new token invalidates all existing tokens of the same type for that user
- Tokens are stored as SHA-256 hashes — the plain-text token is never persisted
- Only users with `pending_verification` status can verify or request a resend
- Returns generic success message even if user not found (prevents email enumeration)

## 7. Password Reset Flow

### 7.1 Request Password Reset

```
Client                           Server
  │                                │
  │  POST /v1/auth/password/forgot │
  │  { email }                     │
  │ ──────────────────────────────►│
  │                                ├─ Find user by email
  │                                ├─ Validate account is not suspended
  │                                │   (locked accounts CAN reset password)
  │                                ├─ Invalidate existing password reset tokens
  │                                ├─ Generate 32-byte SecureRandom token
  │                                ├─ Store SHA-256 hash in auth_verification_tokens
  │                                │   (type: PASSWORD_RESET, expires: 1h)
  │                                ├─ Log plain-text token (dev only; send via email in prod)
  │                                │
  │  200 OK                        │
  │  { message: "Password reset    │
  │    instructions sent" }        │
  │ ◄──────────────────────────────│
```

### 7.2 Reset Password

```
Client                           Server
  │                                │
  │  POST /v1/auth/password/reset  │
  │  { token, newPassword }        │
  │ ──────────────────────────────►│
  │                                ├─ Hash provided token with SHA-256
  │                                ├─ Look up valid token in DB
  │                                │   (matching hash, type, not used, not expired)
  │                                ├─ Validate password against policy
  │                                │   (8-128 chars, uppercase, lowercase, digit, special)
  │                                ├─ Mark token as used (set used_at)
  │                                ├─ Update user password (BCrypt hash)
  │                                ├─ If account was locked:
  │                                │   ├─ Set status to active
  │                                │   ├─ Reset failed_login_attempts to 0
  │                                │   └─ Clear account_locked_until
  │                                ├─ Set updatedBy to user ID
  │                                │
  │  200 OK                        │
  │  { message: "Password reset    │
  │    successfully" }             │
  │ ◄──────────────────────────────│
```

**Key behaviors:**
- Password reset tokens expire after 1 hour (configurable via `security.verification.password-reset-expiration`)
- Locked accounts **can** request password resets (unlocks the account on success)
- Suspended accounts **cannot** request password resets
- Password must meet the full password policy (8-128 chars, mixed case, digit, special character)
- Returns generic success message even if email not found (prevents email enumeration)
- A successful reset also unlocks the account and resets failure counters

## 8. Request Authentication Filter

Every incoming request passes through `JwtAuthenticationFilter` (a `OncePerRequestFilter`):

```
Request ──► Extract Bearer token from Authorization header
         ──► Skip if no token or OPTIONS request (CORS preflight)
         ──► Check token blacklist (reject if blacklisted)
         ──► Extract username from JWT claims
         ──► Load UserDetails from database
         ──► Validate token (signature, expiration, username match)
         ──► Extract authorities from JWT claims
         ──► Set SecurityContext authentication
         ──► Track active user session (register or update)
         ──► Continue filter chain
```

## 9. Security Headers

Token-related responses include security headers to prevent caching:

```http
Cache-Control: no-store
Pragma: no-cache
```

## 10. Error Responses

| Scenario | HTTP Status | Message |
|----------|------------|--------|
| Invalid credentials | 401 | "Unauthorized" |
| Account locked | 401 | "Account is locked until {timestamp}" |
| Account suspended | 401 | "Account is suspended" |
| Account pending verification | 401 | "Account is pending verification. Please verify your email first." |
| Invalid login method | 401 | "Please provide exactly one login method" |
| Invalid refresh token | 401 | "Invalid or expired refresh token" |
| User account deactivated | 401 | "User account is not active" |
| Rate limit exceeded | 429 | "Too many login attempts" |
| Invalid/expired verification token | 400 | "Invalid or expired verification token" |
| Account not pending verification | 400 | "Account is not pending verification" |
| Suspended account reset attempt | 400 | "Cannot reset password for suspended account" |
| Validation errors | 400 | Field-specific error messages |
