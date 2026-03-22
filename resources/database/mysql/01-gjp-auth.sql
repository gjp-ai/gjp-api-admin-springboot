-- Use the new database
USE gjp_db;

-- Table: auth_users
-- Purpose: Store user accounts with authentication credentials, status, and security features
CREATE TABLE IF NOT EXISTS auth_users (
    id CHAR(36) NOT NULL COMMENT 'Primary Key (UUID)',
    nickname VARCHAR(30) DEFAULT NULL,

    -- Login credentials
    username       VARCHAR(30)   NOT NULL COMMENT 'Chosen username; must be ≥3 chars',
    email          VARCHAR(128)  NULL COMMENT 'Email address; validated by regex',
    mobile_country_code VARCHAR(5)  NULL COMMENT 'Country code, e.g. +65',
    mobile_number  VARCHAR(15)   NULL COMMENT 'Subscriber number, digits only',
    password_hash VARCHAR(128) NOT NULL COMMENT 'BCrypt or similar strong hash',

    -- Account Status Management
    account_status ENUM(
        'active',           -- User can log in
        'locked',           -- Temporarily locked (e.g., too many failed attempts)
        'suspended',        -- Administratively disabled
        'pending_verification' -- Awaiting email/SMS verification
    ) NOT NULL DEFAULT 'pending_verification',
    account_locked_until TIMESTAMP NULL DEFAULT NULL COMMENT 'Timestamp until which the account is locked',

    -- Login Tracking & Security
    last_login_at TIMESTAMP NULL DEFAULT NULL,
    last_login_ip VARCHAR(45) DEFAULT NULL COMMENT 'Last known IP address (IPv4/IPv6)',
    password_changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Timestamp of last password change',
    failed_login_attempts SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Counter for consecutive failed login attempts',
    last_failed_login_at TIMESTAMP NULL DEFAULT NULL COMMENT 'Timestamp of the last failed login attempt',

    -- Audit Trail
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by CHAR(36) DEFAULT NULL COMMENT 'UUID of the user who created this record',
    updated_by CHAR(36) DEFAULT NULL COMMENT 'UUID of the user who last updated this record',

    -- Soft Delete
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Logical deletion flag',

    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_users_username (username),
    UNIQUE KEY uk_auth_users_email (email),
    UNIQUE KEY uk_auth_users_phone (mobile_country_code, mobile_number),

    KEY idx_auth_users_account_status (account_status),
    KEY idx_auth_users_last_login (last_login_at),
    KEY idx_auth_users_created_by (created_by),
    KEY idx_auth_users_updated_by (updated_by),
    KEY idx_roles_active (is_active),

    CONSTRAINT fk_auth_users_created_by FOREIGN KEY (created_by) REFERENCES auth_users (id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_auth_users_updated_by FOREIGN KEY (updated_by) REFERENCES auth_users (id) ON DELETE SET NULL ON UPDATE CASCADE,

    CONSTRAINT chk_auth_users_username_fmt CHECK (
        username REGEXP '^[A-Za-z0-9._-]{3,30}$'
    ),
    CONSTRAINT chk_users_email_fmt CHECK (
        email IS NULL OR email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'
    ),
    CONSTRAINT chk_auth_users_mobile_country_code_fmt CHECK (
        mobile_country_code IS NULL OR mobile_country_code REGEXP '^[1-9][0-9]{0,3}$'
    ),
    CONSTRAINT chk_auth_users_mobile_number_fmt CHECK (
        mobile_number IS NULL OR mobile_number REGEXP '^[0-9]{4,15}$'
    ),
    CONSTRAINT chk_contact_required CHECK (
        username REGEXP '^[A-Za-z0-9._-]{3,30}$'
        AND (
            email IS NULL OR email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'
        )
        AND (
            mobile_country_code IS NULL
            OR (
                mobile_country_code IS NOT NULL
                AND mobile_number IS NOT NULL
                AND mobile_country_code REGEXP '^[1-9][0-9]{0,3}$'
                AND mobile_number REGEXP '^[0-9]{4,15}$'
            )
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User accounts with authentication credentials, status, and security features';

-- Table: auth_roles
-- Purpose: Define system roles with hierarchical structure support
CREATE TABLE IF NOT EXISTS auth_roles (
    id CHAR(36) NOT NULL COMMENT 'Primary Key (UUID)',
    code VARCHAR(50) NOT NULL COMMENT 'Unique role code (e.g., ADMIN, EDITOR)',
    name VARCHAR(100) NOT NULL COMMENT 'Human-readable role name',
    description TEXT DEFAULT NULL,
    parent_role_id CHAR(36) DEFAULT NULL COMMENT 'For hierarchical roles (parent role)',
    level INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Hierarchy level (0 = top level)',
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'TRUE if role is essential and cannot be deleted',
    sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Order for display or processing purposes',

    -- Audit Trail
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by CHAR(36) DEFAULT NULL,
    updated_by CHAR(36) DEFAULT NULL,

    -- Soft Delete / Activation
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'TRUE if the role can be assigned',

    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_roles_code (code),
    KEY idx_auth_roles_parent_id (parent_role_id),
    KEY idx_auth_roles_level (level),
    KEY idx_auth_roles_is_active (is_active),
    KEY idx_auth_roles_created_by (created_by),
    KEY idx_auth_roles_updated_by (updated_by),

    CONSTRAINT fk_auth_roles_parent FOREIGN KEY (parent_role_id) REFERENCES auth_roles (id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_auth_roles_created_by FOREIGN KEY (created_by) REFERENCES auth_users (id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_auth_roles_updated_by FOREIGN KEY (updated_by) REFERENCES auth_users (id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_auth_roles_code_format CHECK (code REGEXP '^[A-Z][A-Z0-9_]*$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System roles with optional parent → child hierarchy';

-- Table: auth_user_roles
-- Purpose: Associate users with roles (many-to-many relationship)
CREATE TABLE IF NOT EXISTS auth_user_roles (
    user_id CHAR(36) NOT NULL,
    role_id CHAR(36) NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'When the role was granted',
    expires_at TIMESTAMP NULL DEFAULT NULL COMMENT 'Optional: When the role grant expires',

    -- Audit Trail
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by CHAR(36) DEFAULT NULL,
    updated_by CHAR(36) DEFAULT NULL,

    -- Soft Delete / Activation
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'TRUE if this specific role assignment is active',

    PRIMARY KEY (user_id, role_id),
    KEY idx_auth_user_roles_user (user_id),
    KEY idx_auth_user_roles_role (role_id),
    KEY idx_auth_user_roles_active_expiry (is_active, expires_at), -- For finding current, valid roles
    KEY idx_auth_user_roles_created_by (created_by),
    KEY idx_auth_user_roles_updated_by (updated_by),

    CONSTRAINT fk_auth_user_roles_user FOREIGN KEY (user_id) REFERENCES auth_users (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_auth_user_roles_role FOREIGN KEY (role_id) REFERENCES auth_roles (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_auth_user_roles_created_by FOREIGN KEY (created_by) REFERENCES auth_users (id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_auth_user_roles_updated_by FOREIGN KEY (updated_by) REFERENCES auth_users (id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_auth_user_roles_expiry CHECK (expires_at IS NULL OR expires_at > granted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Links users to their assigned roles (M-M).';

-- Table: audit_logs

-- Purpose: Track all API operations for security and compliance
CREATE TABLE IF NOT EXISTS audit_logs (
    id CHAR(36) NOT NULL COMMENT 'Primary Key (UUID)',
    user_id CHAR(36) DEFAULT NULL COMMENT 'ID of user who performed the action',
    username VARCHAR(30) DEFAULT NULL COMMENT 'Username for quick reference',

    -- Request information
    http_method VARCHAR(10) NOT NULL COMMENT 'HTTP method (POST, PUT, PATCH, DELETE)',
    endpoint VARCHAR(255) NOT NULL COMMENT 'API endpoint that was called',
    request_id CHAR(36) DEFAULT NULL COMMENT 'Request ID from meta.requestId',

    -- Operation result (changed from ENUM to VARCHAR to store status.message)
    result VARCHAR(255) NOT NULL COMMENT 'Result message from response status.message',
    status_code INT DEFAULT NULL COMMENT 'HTTP status code',
    error_message TEXT DEFAULT NULL COMMENT 'Error details if operation failed',

    -- Client information
    ip_address VARCHAR(45) DEFAULT NULL COMMENT 'Client IP address (IPv4/IPv6)',
    user_agent TEXT DEFAULT NULL COMMENT 'Client user agent string',
    session_id VARCHAR(100) DEFAULT NULL COMMENT 'Session identifier',

    -- Performance tracking
    duration_ms BIGINT DEFAULT NULL COMMENT 'Operation duration in milliseconds',

    -- Timestamp
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'When the action occurred',

    PRIMARY KEY (id),

    -- Updated indexes for common queries
    KEY idx_audit_user_id (user_id),
    KEY idx_audit_timestamp (timestamp),
    KEY idx_audit_result (result),
    KEY idx_audit_endpoint (endpoint),
    KEY idx_audit_request_id (request_id),
    KEY idx_audit_user_timestamp (user_id, timestamp),
    KEY idx_audit_ip_address (ip_address),
    KEY idx_audit_status_code (status_code),

    -- Foreign key constraints
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES auth_users (id) ON DELETE SET NULL ON UPDATE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Track all API operations for security and compliance';
USE gjp_db;

-- Table: auth_refresh_tokens
-- Purpose: Store refresh tokens for JWT token rotation with secure hash storage
CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
    id CHAR(36) NOT NULL COMMENT 'Primary Key (UUID)',
    user_id CHAR(36) NOT NULL COMMENT 'Foreign key to auth_users.id',
    token_hash VARCHAR(255) NOT NULL COMMENT 'SHA-256 hash of the refresh token for secure storage',
    expires_at TIMESTAMP NOT NULL COMMENT 'Expiration timestamp of the refresh token',
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Whether this token has been manually revoked',
    revoked_at TIMESTAMP NULL DEFAULT NULL COMMENT 'Timestamp when the token was revoked',
    last_used_at TIMESTAMP NULL DEFAULT NULL COMMENT 'Timestamp when this token was last used for refresh',
    
    -- Audit Trail
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by CHAR(36) DEFAULT NULL COMMENT 'UUID of the user who created this record',
    updated_by CHAR(36) DEFAULT NULL COMMENT 'UUID of the user who last updated this record',

    PRIMARY KEY (id),
    INDEX idx_refresh_tokens_user_id (user_id),
    INDEX idx_refresh_tokens_hash (token_hash),
    INDEX idx_refresh_tokens_expires_at (expires_at),
    INDEX idx_refresh_tokens_user_valid (user_id, expires_at, is_revoked),
    
    CONSTRAINT fk_refresh_tokens_user_id 
        FOREIGN KEY (user_id) 
        REFERENCES auth_users(id) 
        ON DELETE CASCADE ON UPDATE CASCADE
) 
ENGINE=InnoDB 
DEFAULT CHARSET=utf8mb4 
COLLATE=utf8mb4_unicode_ci 
COMMENT='Refresh tokens for JWT authentication with token rotation support';

-- Create index for efficient cleanup of expired tokens
CREATE INDEX idx_refresh_tokens_cleanup ON auth_refresh_tokens (expires_at, is_revoked);

-- Add comment explaining the security approach
-- Note: The actual refresh token value is never stored in the database.
-- Only a SHA-256 hash is stored for verification purposes.
-- This ensures that even if the database is compromised, the actual tokens remain secure.

-- Insert super admin user: superadmin, password: 123456
INSERT INTO auth_users (id, nickname, username, email, mobile_country_code, mobile_number, password_hash, account_status, password_changed_at, created_by,updated_by)
VALUES ('f47ac10b-58cc-4372-a567-0e02b2c3d479', 'SA', 'superadmin', 'admin@gmail.com', '65', '89765432', '$2a$10$PAvGvs85PZwxlV.u4c8q.u96smuyMlpcPFAXNKTlidf3F65gOfdbi', 'active', CURRENT_TIMESTAMP, NULL, NULL);

-- Insert predefined roles with hierarchical structure
INSERT INTO auth_roles (id, code, name, description, parent_role_id, level, is_system_role, sort_order, created_by, updated_by) VALUES 
-- Level 0 (Top-level roles)
('550e8400-e29b-41d4-a716-446655440001', 'SUPER_ADMIN', 'Super Administrator', 'Root-level access with all system privileges including user management and system configuration', NULL, 0, TRUE, 1, NULL, NULL),
('550e8400-e29b-41d4-a716-446655440002', 'ADMIN', 'System Administrator', 'Full administrative access to content, users, and most system features', NULL, 0, TRUE, 2, NULL, NULL),
-- Level 1 (Sub-administrative roles)
('550e8400-e29b-41d4-a716-446655440003', 'CONTENT_MANAGER', 'Content Manager', 'Manages all content categories, publication workflows, and content organization', '550e8400-e29b-41d4-a716-446655440002', 1, TRUE, 3, NULL, NULL),
('550e8400-e29b-41d4-a716-446655440004', 'USER_MANAGER', 'User Manager', 'Manages user accounts, roles, and permissions (except super admin functions)', '550e8400-e29b-41d4-a716-446655440002', 1, TRUE, 4, NULL, NULL),
-- Level 1 (Content creation and editing roles)
('550e8400-e29b-41d4-a716-446655440005', 'EDITOR', 'Senior Editor', 'Creates, edits, publishes, and manages all content with advanced editorial privileges', '550e8400-e29b-41d4-a716-446655440003', 2, FALSE, 5, NULL, NULL),
('550e8400-e29b-41d4-a716-446655440006', 'AUTHOR', 'Content Author', 'Creates and edits own content, can publish with approval workflow', '550e8400-e29b-41d4-a716-446655440005', 3, FALSE, 6, NULL, NULL),
-- Level 1 (Moderation and support roles)
('550e8400-e29b-41d4-a716-446655440007', 'MODERATOR', 'Content Moderator', 'Reviews, moderates, and manages user-generated content and comments', '550e8400-e29b-41d4-a716-446655440003', 2, FALSE, 7, NULL, NULL),
('550e8400-e29b-41d4-a716-446655440008', 'SUPPORT_AGENT', 'Customer Support Agent', 'Handles user inquiries, provides technical support, and manages customer relations', '550e8400-e29b-41d4-a716-446655440004', 2, FALSE, 8, NULL, NULL),
-- Level 0 (Special access roles)
('550e8400-e29b-41d4-a716-446655440009', 'API_CLIENT', 'API Integration Client', 'External system integration access with programmatic API privileges', NULL, 0, FALSE, 9, NULL, NULL),
-- Level 0 (Basic user role)
('550e8400-e29b-41d4-a716-446655440010', 'USER', 'Regular User', 'Standard authenticated user with basic reading, commenting, and profile management privileges', NULL, 0, TRUE, 10, NULL, NULL);

-- Assign SUPER_ADMIN role to superadmin user
INSERT INTO auth_user_roles (user_id, role_id, granted_at, created_by, updated_by) 
VALUES ('f47ac10b-58cc-4372-a567-0e02b2c3d479', '550e8400-e29b-41d4-a716-446655440001', CURRENT_TIMESTAMP, NULL, NULL);

