# Auth Module — API Reference

## 1. Base URL

```
http://localhost:8082/api
```

All endpoints are prefixed with `/api` (configured via `server.servlet.context-path`).

## 2. Response Envelope

All responses use a consistent envelope:

```json
{
  "status": {
    "code": 200,
    "message": "Success message",
    "errors": null
  },
  "data": { ... },
  "meta": {
    "serverDateTime": "2026-03-26 10:30:00",
    "requestId": "uuid"
  }
}
```

Error responses:
```json
{
  "status": {
    "code": 400,
    "message": "Error description",
    "errors": {
      "fieldName": "Validation error message"
    }
  },
  "data": null,
  "meta": { ... }
}
```

---

## 3. Authentication Endpoints

### 3.1 Register User

**`POST /v1/register`** — Public

Creates a new user with default `ROLE_USER` role.

**Request:**
```json
{
  "username": "john_doe",
  "nickname": "John",
  "email": "john@example.com",
  "mobileCountryCode": "65",
  "mobileNumber": "98765432",
  "password": "SecurePass1!"
}
```

| Field | Required | Validation |
|-------|----------|------------|
| `username` | Yes | 3-30 chars, alphanumeric + `.` `_` `-` |
| `nickname` | No | Max 30 chars |
| `email` | Conditional | Valid email, max 128 chars |
| `mobileCountryCode` | Conditional | 1-4 digits, starts with 1-9 |
| `mobileNumber` | Conditional | 4-15 digits |
| `password` | Yes | 8-128 chars, uppercase+lowercase+digit+special, no whitespace |

> At least one contact method (email or mobile) required. Mobile fields must be provided together.

**Response:** `201 Created`
```json
{
  "data": {
    "id": "uuid",
    "username": "john_doe",
    "nickname": "John",
    "email": "john@example.com",
    "accountStatus": "pending_verification",
    "active": true,
    "roles": [{ "code": "USER", "name": "User" }]
  }
}
```

---

### 3.2 Login (Create Tokens)

**`POST /v1/auth/tokens`** — Public

Authenticates user and returns dual tokens. Supports three login methods (exactly one required).

**Request (by username):**
```json
{
  "username": "john_doe",
  "password": "SecurePass1!"
}
```

**Request (by email):**
```json
{
  "email": "john@example.com",
  "password": "SecurePass1!"
}
```

**Request (by mobile):**
```json
{
  "mobileCountryCode": "65",
  "mobileNumber": "98765432",
  "password": "SecurePass1!"
}
```

**Response:** `200 OK`
```json
{
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "dGhpcyBpcyBhIHNlY3VyZSByYW5kb20gdG9rZW4...",
    "tokenType": "Bearer",
    "expiresIn": 1800000,
    "username": "john_doe",
    "nickname": "John",
    "email": "john@example.com",
    "mobileCountryCode": "65",
    "mobileNumber": "98765432",
    "accountStatus": "active",
    "lastLoginAt": "2026-03-26T10:30:00",
    "lastLoginIp": "192.168.1.1",
    "roleCodes": ["USER", "ADMIN"]
  }
}
```

**Headers:** `Cache-Control: no-store`, `Pragma: no-cache`

---

### 3.3 Refresh Tokens

**`PUT /v1/auth/tokens`** — Public

Refreshes access token using a valid refresh token. Implements token rotation.

**Request:**
```json
{
  "refreshToken": "dGhpcyBpcyBhIHNlY3VyZSByYW5kb20gdG9rZW4..."
}
```

**Response:** `200 OK`
```json
{
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "bmV3IHJlZnJlc2ggdG9rZW4gdmFsdWU...",
    "tokenType": "Bearer",
    "expiresIn": 1800000
  }
}
```

> **Note:** The old refresh token is revoked. Use the new refresh token for subsequent refreshes.

---

### 3.4 Logout (Revoke Tokens)

**`DELETE /v1/auth/tokens`** — Authenticated

Revokes access and refresh tokens.

**Request:**
```json
{
  "refreshToken": "dGhpcyBpcyBhIHNlY3VyZSByYW5kb20gdG9rZW4...",
  "logoutFromAllDevices": false
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `refreshToken` | No | Specific refresh token to revoke |
| `logoutFromAllDevices` | No | `true` to revoke ALL refresh tokens for user |

**Response:** `200 OK`
```json
{
  "data": null,
  "status": { "code": 200, "message": "Logout successful - all tokens revoked" }
}
```

---

## 4. User Management Endpoints

All require `Authorization: Bearer <accessToken>` header.

### 4.1 List Users (Paginated + Filterable)

**`GET /v1/users`** — ADMIN, SUPER_ADMIN

| Parameter | Default | Description |
|-----------|---------|-------------|
| `page` | 0 | Page number (0-based) |
| `size` | 10 | Page size |
| `sort` | `username` | Sort field |
| `direction` | `asc` | Sort direction (`asc`/`desc`) |
| `username` | — | Filter by username (contains) |
| `nickname` | — | Filter by nickname (contains) |
| `email` | — | Filter by email (contains) |
| `mobileCountryCode` | — | Filter by mobile country code |
| `mobileNumber` | — | Filter by mobile number |
| `accountStatus` | — | Filter: `active`, `locked`, `suspended`, `pending_verification` |
| `active` | — | Filter by active status (`true`/`false`) |
| `roleCode` | — | Filter by assigned role code |

**Response:** `200 OK` with paginated data
```json
{
  "data": {
    "content": [{ "id": "...", "username": "...", "roles": [...] }],
    "page": 0,
    "size": 10,
    "totalElements": 42
  }
}
```

### 4.2 Get User by ID

**`GET /v1/users/{id}`** — ADMIN, SUPER_ADMIN

### 4.3 Get User by Username

**`GET /v1/users/username/{username}`** — ADMIN, SUPER_ADMIN

### 4.4 Create User

**`POST /v1/users`** — ADMIN, SUPER_ADMIN

**Request:** Same fields as registration, plus:
```json
{
  "accountStatus": "active",
  "active": true,
  "roleCodes": ["ADMIN", "USER"]
}
```

**Response:** `201 Created`

### 4.5 Replace User (Full Update)

**`PUT /v1/users/{id}`** — ADMIN, SUPER_ADMIN

Full replacement — all fields should be provided. Uses `UserCreateRequest`.

### 4.6 Update User (Partial)

**`PATCH /v1/users/{id}`** — ADMIN, SUPER_ADMIN

Partial update — only provided fields are updated. Uses `UserUpdateRequest`.

### 4.7 Delete User (Soft Delete)

**`DELETE /v1/users/{id}`** — SUPER_ADMIN only

Soft deletes user: sets `active=false`, `accountStatus=suspended`, revokes refresh tokens, removes active session.

### 4.8 Change User Password

**`PATCH /v1/users/{id}/password`** — ADMIN, SUPER_ADMIN

```json
{
  "password": "NewSecurePass1!"
}
```

### 4.9 Toggle User Status

**`PATCH /v1/users/{id}/toggle-status`** — ADMIN, SUPER_ADMIN

Toggles the `active` field between `true` and `false`.

### 4.10 Dashboard Statistics

**`GET /v1/users/dashboard`** — ADMIN, SUPER_ADMIN

```json
{
  "data": {
    "totalUsers": 150,
    "activeUsers": 130,
    "lockedUsers": 5,
    "suspendedUsers": 10,
    "pendingVerificationUsers": 5,
    "activeSessions": 42
  }
}
```

---

## 5. Role Management Endpoints

### 5.1 List All Roles

**`GET /v1/roles`** — ADMIN, SUPER_ADMIN

Returns all roles sorted by `sortOrder`, then `name`.

### 5.2 List Active Roles

**`GET /v1/roles/active`** — ADMIN, SUPER_ADMIN

### 5.3 Get Role by ID

**`GET /v1/roles/{id}`** — ADMIN, SUPER_ADMIN

### 5.4 Get Role by Code

**`GET /v1/roles/code/{code}`** — ADMIN, SUPER_ADMIN

### 5.5 Create Role

**`POST /v1/roles`** — SUPER_ADMIN

```json
{
  "code": "EDITOR",
  "name": "Content Editor",
  "description": "Can edit content",
  "parentRoleId": "uuid-of-parent-role",
  "sortOrder": 10,
  "active": true
}
```

- `code` must be unique and match `^[A-Z][A-Z0-9_]*$`
- If `parentRoleId` is provided, `level` is automatically set to `parent.level + 1`

### 5.6 Update Role (Full)

**`PUT /v1/roles/{id}`** — SUPER_ADMIN

System roles cannot be modified (returns 409).

### 5.7 Update Role (Partial)

**`PATCH /v1/roles/{id}`** — SUPER_ADMIN

System roles cannot be modified (returns 409).

### 5.8 Delete Role

**`DELETE /v1/roles/{id}`** — SUPER_ADMIN

Blocked if:
- Role is a system role (409)
- Users have this role assigned (409)
- Role has child roles (409)

### 5.9 Toggle Role Status

**`PATCH /v1/roles/{id}/toggle-status`** — SUPER_ADMIN

---

## 6. User Profile Endpoints

Self-service endpoints for authenticated users.

### 6.1 Get My Profile

**`GET /v1/profile`** — Authenticated

### 6.2 Update My Profile

**`PUT /v1/profile`** — Authenticated

```json
{
  "nickname": "New Nickname",
  "email": "newemail@example.com",
  "mobileCountryCode": "65",
  "mobileNumber": "91234567"
}
```

### 6.3 Change My Password

**`PUT /v1/profile/password`** — Authenticated

```json
{
  "currentPassword": "OldPass1!",
  "newPassword": "NewPass1!"
}
```

### 6.4 Check Email Availability

**`GET /v1/profile/check-email?email=test@example.com`** — Authenticated

### 6.5 Check Mobile Availability

**`GET /v1/profile/check-mobile?countryCode=65&number=91234567`** — Authenticated

---

## 7. Session Management Endpoints

### 7.1 Active Session Statistics

**`GET /v1/admin/sessions/stats`** — ADMIN, SUPER_ADMIN

```json
{
  "data": {
    "activeSessionCount": 42,
    "sessionTimeoutMinutes": 30
  }
}
```

### 7.2 List Active Sessions

**`GET /v1/admin/sessions/active`** — ADMIN, SUPER_ADMIN

### 7.3 Force Session Cleanup

**`POST /v1/admin/sessions/cleanup`** — SUPER_ADMIN

```json
{
  "data": {
    "sessionsBeforeCleanup": 50,
    "sessionsAfterCleanup": 42,
    "sessionsRemoved": 8
  }
}
```

---

## 8. Public Endpoints (No Authentication Required)

```
/                          — Health/root
/v1/register               — User registration
/v1/auth/**                — Login, token refresh
/actuator/health           — Health check
/swagger-ui/**             — Swagger UI
/v3/api-docs/**            — OpenAPI docs
```

## 9. Common Error Codes

| HTTP Status | Exception Type | Scenario |
|------------|---------------|----------|
| 400 | `IllegalArgumentException` | Invalid input, business rule violation |
| 400 | `MethodArgumentNotValidException` | Bean validation failure |
| 401 | `BadCredentialsException` | Invalid credentials, expired token |
| 404 | `ResourceNotFoundException` | Entity not found by ID/code |
| 409 | `DuplicateResourceException` | Unique constraint violation |
| 409 | `IllegalStateException` | System role modification, role in use |
| 429 | Rate limit | Too many login attempts from same IP |
| 500 | `Exception` (catch-all) | Internal error (details suppressed) |
