# Auth Module — Architecture Overview

## 1. Module Purpose

The Auth module provides a complete authentication and authorization system for the GJP Admin API. It handles user registration, login/logout with dual JWT tokens, role-based access control (RBAC), user profile management, session tracking, and comprehensive audit logging.

## 2. Technology Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.x |
| Security | Spring Security 6.x |
| Token | JWT (jjwt library) with HMAC-SHA256 |
| Persistence | Spring Data JPA / Hibernate |
| Database | MySQL 8.x |
| Password Hashing | BCrypt (via Spring Security) |
| Validation | Jakarta Bean Validation |
| Logging | SLF4J + Logback |
| Build | Lombok for boilerplate reduction |

## 3. Package Structure

The auth module follows a **feature-based package structure**, where each sub-package encapsulates a complete vertical slice of functionality.

```
org.ganjp.api.auth
├── blacklist/                  # Token blacklisting (logout support)
│   ├── BlacklistedToken.java          # Entity: blacklisted JWT access tokens
│   ├── BlacklistedTokenRepository.java # Repository
│   └── TokenBlacklistService.java     # Service: blacklist & check tokens
│
├── config/                     # Security configuration
│   ├── SecurityConfig.java            # Spring Security filter chain config
│   └── SecurityProperties.java        # Configuration properties (JWT, CORS, endpoints)
│
├── refresh/                    # Refresh token management
│   ├── RefreshToken.java              # Entity: refresh tokens (hash stored)
│   ├── RefreshTokenRepository.java    # Repository with custom queries
│   ├── RefreshTokenRequest.java       # DTO: refresh token request
│   ├── RefreshTokenService.java       # Service: create, validate, rotate, revoke
│   └── TokenRefreshResponse.java      # DTO: refresh response
│
├── register/                   # User registration
│   ├── RegisterController.java        # POST /v1/register
│   ├── RegisterRequest.java           # DTO: registration input
│   ├── RegisterResponse.java          # DTO: registration output
│   └── RegisterService.java           # Service: registration logic
│
├── role/                       # Role management (RBAC)
│   ├── Role.java                      # Entity: auth_roles
│   ├── RoleController.java            # CRUD /v1/roles
│   ├── RoleCreateRequest.java         # DTO: create/full-update role
│   ├── RoleRepository.java            # Repository
│   ├── RoleResponse.java              # DTO: role output
│   ├── RoleService.java               # Service: CRUD + hierarchy + system role protection
│   ├── RoleUpdateRequest.java         # DTO: partial update role
│   ├── UserRole.java                  # Entity: auth_user_roles (join table)
│   ├── UserRoleId.java                # Composite key for UserRole
│   └── UserRoleRepository.java        # Repository
│
├── security/                   # JWT & authentication infrastructure
│   ├── CustomAuthenticationProvider.java  # Multi-method auth (username/email/mobile)
│   ├── JwtAuthenticationFilter.java       # OncePerRequestFilter for JWT validation
│   └── JwtUtils.java                     # JWT generation, parsing, validation
│
├── session/                    # Active session tracking
│   ├── ActiveUserController.java      # GET /v1/admin/sessions
│   └── ActiveUserService.java         # In-memory session tracking
│
├── token/                      # Authentication (login/logout/refresh)
│   ├── AuthService.java               # Core auth logic (dual tokens, logout)
│   ├── AuthTokenResponse.java         # DTO: login response
│   ├── LoginRequest.java              # DTO: login input
│   ├── LogoutRequest.java             # DTO: logout input
│   └── TokenController.java           # POST/PUT/DELETE /v1/auth/tokens
│
└── user/                       # User management
    ├── AccountStatus.java             # Enum: active, locked, suspended, pending_verification
    ├── User.java                      # Entity: auth_users
    ├── UserController.java            # CRUD /v1/users
    ├── UserCreateRequest.java         # DTO: create/full-update user
    ├── UserRepository.java            # Repository with custom queries
    ├── UserResponse.java              # DTO: user output
    ├── UserService.java               # Service: CRUD + search + dashboard
    ├── UserUpdateRequest.java         # DTO: partial update user
    └── profile/                       # Self-service profile management
        ├── AdminResetPasswordRequest.java   # DTO: admin password reset
        ├── ChangePasswordRequest.java       # DTO: user password change
        ├── UpdateProfileRequest.java        # DTO: profile update
        ├── UserProfileController.java       # /v1/profile endpoints
        ├── UserProfileResponse.java         # DTO: profile output
        └── UserProfileService.java          # Service: profile operations
```

## 4. Key Design Principles

### 4.1 Feature-Based Organization
Each sub-package is a self-contained feature with its own Controller → Service → Repository → Entity/DTO layers. This promotes modularity and makes it easy to navigate the codebase.

### 4.2 Separation of Concerns
- **Controllers** handle HTTP request/response mapping and authorization annotations
- **Services** contain business logic and transaction management
- **Repositories** handle data access with Spring Data JPA
- **DTOs** (Request/Response) decouple API contracts from internal entities

### 4.3 Security by Default
- All endpoints require authentication unless explicitly listed in `security.public-endpoints`
- Method-level authorization with `@PreAuthorize` on every controller endpoint
- Sensitive data (passwords, tokens) is never logged or returned in responses
- Audit logging captures all state-changing operations

### 4.4 RESTful API Design
- Proper HTTP methods: GET (read), POST (create), PUT (full update), PATCH (partial update), DELETE
- Consistent response envelope via `ApiResponse<T>`
- Standard HTTP status codes (200, 201, 400, 401, 404, 409, 500)
- Token operations modeled as REST resources (`/v1/auth/tokens`)

## 5. Dependency Graph

```
TokenController ──► AuthService ──► JwtUtils
                                 ──► RefreshTokenService
                                 ──► TokenBlacklistService
                                 ──► UserRepository
                                 ──► UserRoleRepository

RegisterController ──► RegisterService ──► UserRepository
                                        ──► RoleRepository
                                        ──► PasswordEncoder

UserController ──► UserService ──► UserRepository
                               ──► RoleRepository
                               ──► UserRoleRepository
                               ──► PasswordEncoder
                               ──► ActiveUserService
                               ──► RefreshTokenRepository

RoleController ──► RoleService ──► RoleRepository
                               ──► UserRoleRepository

UserProfileController ──► UserProfileService ──► UserRepository
                                              ──► PasswordEncoder

ActiveUserController ──► ActiveUserService

JwtAuthenticationFilter ──► JwtUtils
                        ──► UserDetailsService
                        ──► TokenBlacklistService
                        ──► ActiveUserService
```

## 6. Cross-Cutting Concerns

| Concern | Implementation |
|---------|---------------|
| Exception Handling | `GlobalExceptionHandler` with specific handlers for each exception type |
| Audit Logging | AOP aspect (`@within(RestController)`) + authentication-specific interceptor |
| Transaction Management | `@Transactional` on service methods; `REQUIRES_NEW` for failure recording |
| Input Validation | Jakarta Bean Validation annotations on all DTOs |
| Rate Limiting | In-memory rate limiter (10 attempts/60s per IP) |
| IP Extraction | Shared `IpAddressUtils` (supports X-Forwarded-For, X-Real-IP) |
