# Auth Module — Role-Based Access Control (RBAC)

## 1. Overview

The auth module implements a **hierarchical role-based access control** system. Roles can be organized in parent-child relationships with configurable levels, and users can be assigned multiple roles with optional temporal constraints.

## 2. Role Hierarchy

```
Level 0 (Top-level)
├── SUPER_ADMIN (System Role)
│   └── Level 1
│       ├── ADMIN (System Role)
│       │   └── Level 2
│       │       ├── EDITOR
│       │       └── MODERATOR
│       └── MANAGER
└── USER (System Role)
```

### 2.1 Role Properties

| Property | Description |
|----------|-------------|
| `code` | Unique identifier (e.g., `ADMIN`). Must match `^[A-Z][A-Z0-9_]*$` |
| `name` | Human-readable name |
| `description` | Optional description |
| `parentRoleId` | Reference to parent role (nullable for top-level roles) |
| `level` | Hierarchy depth (0 = top level, auto-calculated from parent) |
| `isSystemRole` | Protected flag — system roles cannot be modified or deleted |
| `sortOrder` | Display ordering |
| `isActive` | Whether the role can be assigned |

### 2.2 System Roles

System roles (`is_system_role = TRUE`) have special protections:

| Operation | Allowed? |
|-----------|---------|
| View | Yes |
| Modify (PUT/PATCH) | No — throws `IllegalStateException` |
| Delete | No — throws `IllegalStateException` |
| Toggle status | Yes (via toggle endpoint) |
| Assign to users | Yes |

## 3. User-Role Association

### 3.1 Many-to-Many Relationship

Users and roles are linked through the `auth_user_roles` join table:

```
User A ──┬── ROLE_SUPER_ADMIN
         └── ROLE_USER

User B ──┬── ROLE_ADMIN
         └── ROLE_USER

User C ──── ROLE_USER
```

### 3.2 Temporal Roles

Each role assignment supports temporal constraints:

| Field | Purpose |
|-------|---------|
| `granted_at` | When the role was assigned |
| `expires_at` | Optional: when the role assignment expires |
| `is_active` | Whether this specific assignment is currently active |

**Active role resolution** (at authentication time):
```java
// A role assignment is active if:
is_active = true
AND (expires_at IS NULL OR expires_at > NOW())
```

### 3.3 Role Assignment During User Operations

**Registration (`POST /v1/register`):**
- Automatically assigns `ROLE_USER`

**User Creation (`POST /v1/users`):**
- Accepts `roleCodes` set in request body
- Validates all role codes exist
- Creates `auth_user_roles` entries

**User Update (`PUT /v1/users/{id}` or `PATCH /v1/users/{id}`):**
- If `roleCodes` is provided in the request:
  1. All existing role assignments are removed
  2. New role assignments are created from the provided codes
- If `roleCodes` is not provided: existing roles are preserved

## 4. Authority Mapping

### 4.1 From Database to JWT

When a user logs in or refreshes tokens, authorities are resolved:

```
Database:  auth_roles.code = "ADMIN"
     ↓
Mapping:   "ROLE_" + code = "ROLE_ADMIN"
     ↓
JWT claim: authorities: ["ROLE_ADMIN", "ROLE_USER"]
     ↓
Spring:    SimpleGrantedAuthority("ROLE_ADMIN")
```

### 4.2 In JWT Access Token

```json
{
  "authorities": ["ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_USER"]
}
```

### 4.3 In Spring Security Context

On each authenticated request, the `JwtAuthenticationFilter` extracts authorities from the JWT and sets them in the `SecurityContext`:

```java
List<GrantedAuthority> authorities = jwtUtils.extractAuthorities(jwt);
UsernamePasswordAuthenticationToken authToken =
    new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
SecurityContextHolder.getContext().setAuthentication(authToken);
```

## 5. Authorization Enforcement

### 5.1 Method-Level Security

Authorization is enforced via `@PreAuthorize` annotations on every controller method:

```java
// Requires ADMIN or SUPER_ADMIN authority
@PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")

// Requires only SUPER_ADMIN authority
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")

// Requires any authenticated user
@PreAuthorize("isAuthenticated()")
```

### 5.2 Authorization Levels

| Level | Authorities | Typical Endpoints |
|-------|------------|-------------------|
| Public | None | Register, Login, Token Refresh, Email Verification, Password Reset |
| Authenticated | Any valid token | Profile endpoints |
| Admin | `ROLE_ADMIN` or `ROLE_SUPER_ADMIN` | User listing, session stats |
| Super Admin | `ROLE_SUPER_ADMIN` only | Role management, user deletion |

### 5.3 Full Authorization Matrix

| Endpoint | Method | Required Authority |
|----------|--------|-------------------|
| `POST /v1/register` | POST | Public |
| `POST /v1/auth/tokens` | POST | Public |
| `PUT /v1/auth/tokens` | PUT | Public |
| `DELETE /v1/auth/tokens` | DELETE | Authenticated |
| `POST /v1/auth/email/verify` | POST | Public |
| `POST /v1/auth/email/resend-verification` | POST | Public |
| `POST /v1/auth/password/forgot` | POST | Public |
| `POST /v1/auth/password/reset` | POST | Public |
| `GET /v1/profile` | GET | Authenticated |
| `PUT /v1/profile` | PUT | Authenticated |
| `PUT /v1/profile/password` | PUT | Authenticated |
| `GET /v1/profile/check-email` | GET | Authenticated |
| `GET /v1/profile/check-mobile` | GET | Authenticated |
| `GET /v1/users` | GET | ADMIN, SUPER_ADMIN |
| `GET /v1/users/{id}` | GET | ADMIN, SUPER_ADMIN |
| `GET /v1/users/username/{username}` | GET | ADMIN, SUPER_ADMIN |
| `POST /v1/users` | POST | ADMIN, SUPER_ADMIN |
| `PUT /v1/users/{id}` | PUT | ADMIN, SUPER_ADMIN |
| `PATCH /v1/users/{id}` | PATCH | ADMIN, SUPER_ADMIN |
| `DELETE /v1/users/{id}` | DELETE | SUPER_ADMIN |
| `PATCH /v1/users/{id}/password` | PATCH | ADMIN, SUPER_ADMIN |
| `PATCH /v1/users/{id}/toggle-status` | PATCH | ADMIN, SUPER_ADMIN |
| `GET /v1/users/dashboard` | GET | ADMIN, SUPER_ADMIN |
| `GET /v1/roles` | GET | ADMIN, SUPER_ADMIN |
| `GET /v1/roles/active` | GET | ADMIN, SUPER_ADMIN |
| `GET /v1/roles/{id}` | GET | ADMIN, SUPER_ADMIN |
| `GET /v1/roles/code/{code}` | GET | ADMIN, SUPER_ADMIN |
| `POST /v1/roles` | POST | SUPER_ADMIN |
| `PUT /v1/roles/{id}` | PUT | SUPER_ADMIN |
| `PATCH /v1/roles/{id}` | PATCH | SUPER_ADMIN |
| `DELETE /v1/roles/{id}` | DELETE | SUPER_ADMIN |
| `PATCH /v1/roles/{id}/toggle-status` | PATCH | SUPER_ADMIN |
| `GET /v1/admin/sessions/stats` | GET | ADMIN, SUPER_ADMIN |
| `GET /v1/admin/sessions/active` | GET | ADMIN, SUPER_ADMIN |
| `POST /v1/admin/sessions/cleanup` | POST | SUPER_ADMIN |

## 6. Role Lifecycle

### 6.1 Creating a Role

```
POST /v1/roles (SUPER_ADMIN)
  ├─ Validate code uniqueness
  ├─ If parentRoleId provided:
  │    ├─ Verify parent role exists
  │    └─ Set level = parent.level + 1
  ├─ If no parent: level = 0 (or provided value)
  ├─ Set defaults: sortOrder=999, active=true
  └─ Save and return
```

### 6.2 Updating a Role

```
PUT/PATCH /v1/roles/{id} (SUPER_ADMIN)
  ├─ Check role exists
  ├─ Reject if system role → 409
  ├─ Check code uniqueness (if changing)
  ├─ Update parent role (if changing):
  │    ├─ Reject circular reference (role cannot be its own parent)
  │    ├─ Look up new parent
  │    └─ Recalculate level
  └─ Save and return
```

### 6.3 Deleting a Role

```
DELETE /v1/roles/{id} (SUPER_ADMIN)
  ├─ Check role exists
  ├─ Reject if system role → 409
  ├─ Reject if users assigned → 409 (with count)
  ├─ Reject if child roles exist → 409 (with count)
  ├─ Delete role
  └─ Log deletion with user ID for audit trail
```
