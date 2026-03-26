# Auth Module — Audit Logging & Configuration

## 1. Audit Logging

### 1.1 Overview

The audit system captures all state-changing API operations and authentication events for security monitoring and compliance. Audit logs are stored in the `audit_logs` database table and processed asynchronously to minimize performance impact.

### 1.2 Architecture

```
Request ──► Controller ──► Service ──► Repository
                │                        │
                └───── AOP Aspect ───────┘
                       (intercept)
                          │
                    ┌─────┴─────┐
                    │ Audit     │
                    │ Service   │
                    │ (async)   │
                    └─────┬─────┘
                          │
                    ┌─────┴─────┐
                    │ audit_logs│
                    │  table    │
                    └───────────┘
```

### 1.3 What Gets Logged

**General API operations** (via AOP `@within(RestController)` aspect):
- HTTP method and endpoint
- User ID and username
- Request and response data (sanitized)
- HTTP status code
- Client IP address and user agent
- Operation duration (milliseconds)
- Request ID for correlation

**Authentication-specific events** (via dedicated interceptor):
- Login attempts (successful and failed)
- Token refresh operations
- Logout operations
- Registration attempts

### 1.4 Sensitive Data Handling

Audit logging sanitizes sensitive data before persisting:

| Data | Treatment |
|------|-----------|
| Passwords | Stripped from login/register request data |
| Access tokens | Excluded from response data |
| Refresh tokens | Excluded from response data |
| Token presence | Logged as boolean (`tokenPresent: true`) |

Example sanitized login request:
```json
{
  "username": "john_doe",
  "email": null,
  "mobileCountryCode": null,
  "mobileNumber": null
}
```
*(Password deliberately excluded)*

### 1.5 Async Processing

Audit log persistence is processed asynchronously with a dedicated thread pool:

| Property | Value |
|----------|-------|
| Core pool size | 2 threads |
| Max pool size | 5 threads |
| Queue capacity | 100 |
| Keep-alive | 60 seconds |
| Thread name prefix | `audit-` |

This prevents audit logging from impacting API response times.

### 1.6 Retention

- Default retention: **300 days**
- Configurable via `audit.retention-days`
- No foreign key on `user_id` — audit records survive user deletion

---

## 2. Active Session Tracking

### 2.1 Overview

The system tracks active user sessions in memory via `ActiveUserService`. This provides real-time visibility into who is currently using the system.

### 2.2 How It Works

```
JWT Filter validates token
  ├─ If user not tracked: registerActiveUser(userId, username, userAgent, ip)
  └─ If user already tracked: updateLastActivity(userId)

Logout
  └─ removeActiveUser(userId)

Periodic cleanup
  └─ Remove sessions with no activity beyond timeout threshold
```

### 2.3 Session Information

Each tracked session records:
- User ID
- Username
- User Agent (browser/client)
- IP Address
- Login time
- Last activity time

### 2.4 Limitations

- **In-memory only** — Session data is lost on server restart
- **Single-instance** — Each instance tracks its own sessions independently
- For multi-instance deployments, consider a shared store (Redis)

---

## 3. Exception Handling

### 3.1 GlobalExceptionHandler

The `GlobalExceptionHandler` (`@ControllerAdvice`) provides centralized exception handling:

| Exception | HTTP Status | Message Policy |
|-----------|------------|----------------|
| `MethodArgumentNotValidException` | 400 | Field-specific validation errors |
| `IllegalArgumentException` | 400 | Exception message returned |
| `BadCredentialsException` | 401 | "Unauthorized" |
| `ResourceNotFoundException` | 404 | "Resource not found" with details |
| `DuplicateResourceException` | 409 | "Duplicate resource" with details |
| `IllegalStateException` | 409 | Exception message returned |
| `RuntimeException` | 400 | Exception message (logged as warning) |
| `Exception` (catch-all) | 500 | **Generic message** (details suppressed) |

### 3.2 Security Note

The catch-all handler logs the full error internally but returns only a generic message to the client:

```java
@ExceptionHandler(Exception.class)
log.error("Unhandled exception", ex);  // Full details in server logs
return "An unexpected error occurred";  // Generic message to client
```

This prevents information leakage through error responses.

---

## 4. Configuration Reference

### 4.1 Application Configuration (`application.yml`)

```yaml
server:
  port: 8082
  servlet:
    context-path: /api/

spring:
  application:
    name: GJP-API-ADMIN
  profiles:
    active: dev
  jackson:
    serialization:
      write-dates-as-timestamps: false
    time-zone: UTC
    date-format: "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
```

### 4.2 Security Configuration

```yaml
security:
  jwt:
    secret-key: <base64-encoded-secret>     # HMAC-SHA256 signing key
    expiration: 1800000                      # Access token: 30 minutes (ms)
    refresh-expiration: 2592000000           # Refresh token: 30 days (ms)
    issuer: gjp-api-admin                    # JWT issuer claim

  public-endpoints:                          # No authentication required
    - "/"
    - "/v1/register"
    - "/v1/auth/**"
    - "/actuator/health"
    - "/swagger-ui/**"
    - "/v3/api-docs/**"

  authorized-endpoints:                      # URL-level role requirements
    - pattern: "/v1/roles/**"
      roles:
        - "ROLE_SUPER_ADMIN"

  cors:
    allowed-origins:
      - https://ganjianping.com
      - https://www.ganjianping.com
      - http://localhost:8082
      - http://127.0.0.1:8082
      - http://localhost:3000
```

### 4.3 Security Properties Java Mapping

```java
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {
    private List<String> publicEndpoints;
    private List<AuthorizedEndpoint> authorizedEndpoints;
    private Cors cors;
    private Jwt jwt;

    @Data
    public static class AuthorizedEndpoint {
        private String pattern;
        private List<String> roles;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins;
    }

    @Data
    public static class Jwt {
        private String secretKey;
        private long expiration;
        private long refreshExpiration = 2592000000L;  // 30 days default
        private String issuer = "gjp-api-admin";
    }
}
```

### 4.4 Audit Configuration

```yaml
audit:
  enabled: true                              # Master switch
  log-request-data: true                     # Log request bodies
  log-response-data: true                    # Log response bodies
  max-data-length: 10000                     # Max chars per logged data
  log-successful-operations: true            # Log successful ops
  log-failed-operations: true                # Log failed ops
  retention-days: 300                        # Log retention period
  async-processing: true                     # Async persistence
  audit-authentication-events: true          # Log auth events
  max-failed-attempts-per-minute: 10         # Rate limit threshold
  include-sensitive-data: false              # Strip sensitive data

  exclude-patterns:                          # Skip audit for these patterns
    - "/actuator/.*"
    - "/swagger-.*"
    - "/v3/api-docs.*"
    - "/favicon.ico"

  thread-pool:                               # Async thread pool config
    core-pool-size: 2
    max-pool-size: 5
    queue-capacity: 100
    keep-alive-seconds: 60
    thread-name-prefix: "audit-"
```

### 4.5 Database Configuration

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    # URL, username, password in profile-specific config (application-dev.yml)
  jpa:
    properties:
      hibernate:
        format_sql: true
```

---

## 5. Environment-Specific Configuration

| Property | Dev | Production Recommendation |
|----------|-----|--------------------------|
| `security.jwt.secret-key` | Dev key | Strong random key (env var) |
| `security.jwt.expiration` | 1800000 (30 min) | 900000 (15 min) |
| `audit.include-sensitive-data` | false | false |
| `audit.async-processing` | true | true |
| `spring.jpa.properties.hibernate.format_sql` | true | false |
| `server.port` | 8082 | Environment-specific |
| CORS origins | localhost + domain | Production domains only |

---

## 6. Cleanup & Maintenance

### 6.1 Refresh Token Cleanup

The `RefreshTokenService.cleanupExpiredTokens()` method removes tokens that have been expired for more than 7 days:

```java
LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7);
refreshTokenRepository.deleteExpiredTokens(cutoffTime);
```

This should be scheduled as a periodic job (e.g., daily cron).

### 6.2 Token Blacklist Cleanup

Blacklist entries can be purged after the token's natural expiration time:

```sql
DELETE FROM auth_token_blacklist WHERE expires_at < NOW();
```

### 6.3 Audit Log Cleanup

Based on `audit.retention-days` (default: 300 days):

```sql
DELETE FROM audit_logs WHERE timestamp < DATE_SUB(NOW(), INTERVAL 300 DAY);
```

### 6.4 Session Cleanup

Active sessions are cleaned up automatically when they exceed the timeout. A manual cleanup can be triggered via:

```
POST /v1/admin/sessions/cleanup (SUPER_ADMIN)
```
