# Auth Module — Database Schema

## 1. Overview

The auth module uses 6 database tables in MySQL 8.x with `utf8mb4_unicode_ci` collation:

```
┌──────────────────┐     ┌──────────────────┐
│   auth_users     │     │   auth_roles     │
│──────────────────│     │──────────────────│
│ id (PK, UUID)    │     │ id (PK, UUID)    │
│ username (UQ)    │     │ code (UQ)        │
│ email (UQ)       │     │ name             │
│ mobile (UQ comp) │     │ parent_role_id   │──┐
│ password_hash    │     │ level            │  │ (self-referencing)
│ account_status   │     │ is_system_role   │  │
│ ...              │     │ ...              │◄─┘
└────────┬─────────┘     └────────┬─────────┘
         │                        │
         │   ┌────────────────┐   │
         └──►│ auth_user_roles│◄──┘
             │────────────────│
             │ user_id (PK)  │
             │ role_id (PK)  │
             │ granted_at    │
             │ expires_at    │
             └───────────────┘

┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐
│ auth_refresh_tokens  │  │ auth_token_blacklist │  │     audit_logs       │
│──────────────────────│  │──────────────────────│  │──────────────────────│
│ id (PK, UUID)        │  │ token_id (PK)        │  │ id (PK, UUID)        │
│ user_id (FK)         │  │ expires_at           │  │ user_id              │
│ token_hash           │  └──────────────────────┘  │ http_method          │
│ expires_at           │                            │ endpoint             │
│ is_revoked           │                            │ result               │
└──────────────────────┘                            │ ip_address           │
                                                    └──────────────────────┘
```

## 2. Table: `auth_users`

Stores user accounts with authentication credentials, status, and security features.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| `id` | CHAR(36) | NO | — | Primary key (UUID) |
| `nickname` | VARCHAR(30) | YES | NULL | Display name |
| `username` | VARCHAR(30) | NO | — | Login username (unique) |
| `email` | VARCHAR(128) | YES | NULL | Email address (unique) |
| `mobile_country_code` | VARCHAR(5) | YES | NULL | E.g., "65" for Singapore |
| `mobile_number` | VARCHAR(15) | YES | NULL | Subscriber number, digits only |
| `password_hash` | VARCHAR(128) | NO | — | BCrypt hash |
| `account_status` | ENUM | NO | `pending_verification` | `active`, `locked`, `suspended`, `pending_verification` |
| `account_locked_until` | TIMESTAMP | YES | NULL | Lock expiry timestamp |
| `last_login_at` | TIMESTAMP | YES | NULL | Last successful login |
| `last_login_ip` | VARCHAR(45) | YES | NULL | Last login IP (IPv4/IPv6) |
| `password_changed_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | Last password change |
| `failed_login_attempts` | SMALLINT UNSIGNED | NO | 0 | Consecutive failures |
| `last_failed_login_at` | TIMESTAMP | YES | NULL | Last failed attempt |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | Record creation time |
| `updated_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | Last update time |
| `created_by` | CHAR(36) | YES | NULL | Creator user ID (FK) |
| `updated_by` | CHAR(36) | YES | NULL | Last updater user ID (FK) |
| `is_active` | BOOLEAN | NO | TRUE | Soft delete flag |

**Unique Constraints:**
- `uk_auth_users_username` on `username`
- `uk_auth_users_email` on `email`
- `uk_auth_users_phone` on `(mobile_country_code, mobile_number)` — composite

**Indexes:**
- `idx_auth_users_account_status` — Filter by status
- `idx_auth_users_last_login` — Sort by recent login
- `idx_auth_users_created_by`, `idx_auth_users_updated_by` — Audit trail lookup
- `idx_roles_active` — Filter active users

**CHECK Constraints:**
- Username format: `^[A-Za-z0-9._-]{3,30}$`
- Email format: standard email regex
- Mobile country code: `^[1-9][0-9]{0,3}$`
- Mobile number: `^[0-9]{4,15}$`
- Contact validation: ensures mobile fields are paired

**Foreign Keys:**
- `created_by` → `auth_users(id)` ON DELETE SET NULL
- `updated_by` → `auth_users(id)` ON DELETE SET NULL

## 3. Table: `auth_roles`

Defines system roles with hierarchical structure support.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| `id` | CHAR(36) | NO | — | Primary key (UUID) |
| `code` | VARCHAR(50) | NO | — | Unique role code (e.g., `ADMIN`) |
| `name` | VARCHAR(100) | NO | — | Human-readable name |
| `description` | TEXT | YES | NULL | Role description |
| `parent_role_id` | CHAR(36) | YES | NULL | Parent role (self-FK) |
| `level` | INT UNSIGNED | NO | 0 | Hierarchy level (0 = top) |
| `is_system_role` | BOOLEAN | NO | FALSE | Protected from deletion |
| `sort_order` | INT UNSIGNED | NO | 0 | Display ordering |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | |
| `updated_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | |
| `created_by` | CHAR(36) | YES | NULL | |
| `updated_by` | CHAR(36) | YES | NULL | |
| `is_active` | BOOLEAN | NO | TRUE | Soft delete flag |

**Constraints:**
- `uk_auth_roles_code` — Unique role code
- `chk_auth_roles_code_format` — Code must match `^[A-Z][A-Z0-9_]*$`
- `fk_auth_roles_parent` → `auth_roles(id)` ON DELETE SET NULL (self-referencing)

## 4. Table: `auth_user_roles`

Many-to-many association between users and roles with temporal support.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| `user_id` | CHAR(36) | NO | — | PK part 1, FK to auth_users |
| `role_id` | CHAR(36) | NO | — | PK part 2, FK to auth_roles |
| `granted_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | When role was granted |
| `expires_at` | TIMESTAMP | YES | NULL | Optional expiry |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | |
| `updated_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | |
| `created_by` | CHAR(36) | YES | NULL | |
| `updated_by` | CHAR(36) | YES | NULL | |
| `is_active` | BOOLEAN | NO | TRUE | Assignment active flag |

**Key design decisions:**
- **Composite PK** `(user_id, role_id)` — One user cannot have the same role twice
- **Temporal roles** via `expires_at` — Roles can automatically expire
- `ON DELETE CASCADE` for `user_id` — User deletion removes all role assignments
- `ON DELETE RESTRICT` for `role_id` — Cannot delete a role if users have it assigned
- `CHECK (expires_at IS NULL OR expires_at > granted_at)` — Expiry must be after grant

## 5. Table: `auth_refresh_tokens`

Stores hashed refresh tokens for the token rotation mechanism.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| `id` | CHAR(36) | NO | — | Primary key (UUID) |
| `user_id` | CHAR(36) | NO | — | FK to auth_users |
| `token_hash` | VARCHAR(255) | NO | — | SHA-256 hash of token |
| `expires_at` | TIMESTAMP | NO | — | Token expiration |
| `is_revoked` | BOOLEAN | NO | FALSE | Revocation flag |
| `revoked_at` | TIMESTAMP | YES | NULL | When revoked |
| `last_used_at` | TIMESTAMP | YES | NULL | Last refresh time |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | |
| `updated_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | |
| `created_by` | CHAR(36) | YES | NULL | |
| `updated_by` | CHAR(36) | YES | NULL | |

**Indexes:**
- `idx_refresh_tokens_hash` — Token lookup by hash
- `idx_refresh_tokens_user_valid` on `(user_id, expires_at, is_revoked)` — Active tokens for user
- `idx_refresh_tokens_cleanup` on `(expires_at, is_revoked)` — Expired token cleanup

**Foreign Key:**
- `user_id` → `auth_users(id)` ON DELETE CASCADE

**Security note:** The actual token value is never stored. Only the SHA-256 hash is persisted. Even if the database is compromised, the tokens remain secure.

## 6. Table: `auth_token_blacklist`

Persists blacklisted JWT access tokens to survive server restarts.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| `token_id` | VARCHAR(255) | NO | — | JWT `jti` claim (PK) |
| `expires_at` | TIMESTAMP | NO | — | Token's natural expiry |

**Index:**
- `idx_token_blacklist_expires_at` — For cleanup of expired entries

**Design notes:**
- Minimal schema — only the token ID and expiry are needed
- Entries can be purged after `expires_at` since the token is naturally invalid
- The `token_id` corresponds to the `jti` (JWT ID) claim in the access token

## 7. Table: `audit_logs`

Tracks all API operations for security and compliance.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| `id` | CHAR(36) | NO | — | Primary key (UUID) |
| `user_id` | CHAR(36) | YES | NULL | Actor user ID |
| `username` | VARCHAR(30) | YES | NULL | Username for reference |
| `http_method` | VARCHAR(10) | NO | — | POST, PUT, PATCH, DELETE |
| `endpoint` | VARCHAR(255) | NO | — | API endpoint path |
| `request_id` | CHAR(36) | YES | NULL | Correlation ID |
| `result` | VARCHAR(255) | NO | — | Operation result message |
| `status_code` | INT | YES | NULL | HTTP status code |
| `error_message` | TEXT | YES | NULL | Error details on failure |
| `ip_address` | VARCHAR(45) | YES | NULL | Client IP |
| `user_agent` | TEXT | YES | NULL | Browser/client info |
| `session_id` | VARCHAR(100) | YES | NULL | Session ID |
| `duration_ms` | BIGINT | YES | NULL | Operation duration |
| `timestamp` | TIMESTAMP | NO | CURRENT_TIMESTAMP | Event time |

**Design decisions:**
- **No foreign key on `user_id`** — Audit logs are append-only historical records. They must survive user deletion and be writable before user context is available (e.g., login attempts)
- **Comprehensive indexing** for common queries (by user, timestamp, endpoint, IP, request ID)
- **Retention**: 300 days (configurable via `audit.retention-days`)

## 8. Entity Relationships

```
auth_users (1) ──── (N) auth_user_roles (N) ──── (1) auth_roles
auth_users (1) ──── (N) auth_refresh_tokens
auth_roles (1) ──── (N) auth_roles (self: parent→children)
auth_roles (1) ──── (N) auth_user_roles
```

**Cascade rules:**
- User deletion cascades to: user_roles (CASCADE), refresh_tokens (CASCADE)
- Role deletion restricted if: user_roles exist (RESTRICT)
- Parent role deletion: child's parent_role_id SET NULL
- Audit log `created_by`/`updated_by` references: SET NULL on user deletion
