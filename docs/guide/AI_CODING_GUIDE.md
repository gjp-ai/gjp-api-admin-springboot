# AI Coding Guide — GJP Admin API (Spring Boot)

> Condensed coding rules for AI-assisted code generation.
> For full rationale and examples, see [STYLE_GUIDE.md](./STYLE_GUIDE.md).

## Project Structure

```
org.ganjp.api
├── auth/                    # Auth module
│   ├── role/                # Role entity, service, controller, DTOs
│   ├── user/                # User entity, service, controller, DTOs
│   │   └── profile/         # Self-service profile management
│   ├── token/
│   │   ├── blacklist/       # Token blacklisting (logout/revocation)
│   │   └── refresh/         # Refresh token management
│   └── security/            # SecurityConfig, JwtUtils, filters, properties
├── audit/                   # Audit logging (async)
└── common/
    ├── exception/           # GlobalExceptionHandler, custom exceptions
    └── model/               # ApiResponse, BaseEntity, PaginatedResponse
```

## Class Annotations

### Entities (JPA)
```java
@Slf4j
@Getter @Setter
@ToString(exclude = {"lazyField1", "lazyField2"})  // Exclude bidirectional/lazy
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auth_table_name")
public class MyEntity extends BaseEntity { ... }
```
- NEVER use `@Data` on entities (breaks Hibernate proxies)
- Implement `equals()`/`hashCode()` based on `id` only
- Use `@Builder.Default` for fields with defaults (e.g., `isActive = true`)

### DTOs (Request/Response)
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyEntityResponse { ... }
```

### Services
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class MyService {
    private final MyRepository myRepository;  // final = constructor injection
}
```

### Controllers
```java
@Slf4j
@RestController
@RequestMapping("/v1/my-entities")
@RequiredArgsConstructor
public class MyController { ... }
```

### Configuration
```java
@Slf4j
@Configuration
@EnableWebSecurity  // if security-related
@RequiredArgsConstructor
public class MyConfig { ... }
```

## Transaction Rules

```java
// Read-only methods: get*, find*, is*, count*, check*
@Transactional(readOnly = true)
public MyResponse getById(String id) { ... }

// Write methods: create*, update*, delete*, toggle*
@Transactional
public MyResponse create(MyRequest request, String userId) { ... }

// Independent side-effect (must persist even if caller rolls back)
// MUST be in a SEPARATE @Service class (Spring AOP proxy requirement)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordSideEffect(...) { ... }
```

## Exception Rules

| Scenario | Exception | HTTP |
|----------|-----------|------|
| Entity not found | `ResourceNotFoundException("Entity", "field", value)` | 404 |
| Duplicate entry | `DuplicateResourceException.of("Entity", "field", value)` | 409 |
| Self-service error (profile, password) | `BusinessException("message")` | 400 |
| Admin validation error | `IllegalArgumentException("message")` | 400 |
| Auth failure | `BadCredentialsException("message")` | 401 |
| Invalid state (e.g., system role) | `IllegalStateException("message")` | 409 |

- NEVER use generic `RuntimeException`
- NEVER leak internal details (SQL, stack traces) in error messages

## Response Pattern

```java
// Success (200)
return ResponseEntity.ok(ApiResponse.success(data, "Entity retrieved"));

// Created (201)
return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(data, "Entity created"));

// All endpoints return ResponseEntity<ApiResponse<T>>
```

## Controller Rules

- Every endpoint MUST have `@PreAuthorize`:
  ```java
  @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")  // Admin ops
  @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")                                 // Destructive ops
  @PreAuthorize("isAuthenticated()")                                                 // Self-service
  ```
- Extract user ID: `jwtUtils.extractUserIdFromToken(request)`
- Request DTOs MUST use `@Valid @RequestBody`
- Paginated lists return `PaginatedResponse.of(page)`

## Validation Rules

- Validate on DTOs (Bean Validation), not in service methods
- Cross-field validation: use `@AssertTrue` with descriptive message
- Password policy: `@Size(min=8, max=128)` + `@Pattern(uppercase+lowercase+digit+special+no-whitespace)`

## Repository Rules

- Simple lookups: derived query methods (`findByUsername`, `existsByEmail`)
- Multi-condition queries: `@Query` with JPQL (not long derived names)
- Native SQL: only when JPQL can't express it
- Update/delete queries: `@Modifying(clearAutomatically = true, flushAutomatically = true)`
- Paginated: accept `Pageable`, return `Page<T>`

## Testing Rules

- **Naming:** `should_behavior_when_condition()`
- **Structure:** `// Given` → `// When` → `// Then`
- **Assertions:** AssertJ (`assertThat`, `assertThatThrownBy`)

| Test type | Annotation | Suffix |
|-----------|-----------|--------|
| Service unit test | `@ExtendWith(MockitoExtension.class)` | `*Test` |
| Controller test | `@WebMvcTest(Controller.class)` | `*ControllerTest` |
| Repository test | `@DataJpaTest` | `*IntegrationTest` |
| Full integration | `@SpringBootTest` | `*IntegrationTest` |

- Controller tests MUST verify auth: `@WithMockUser(authorities = "ROLE_ADMIN")`
- Integration tests use H2 (`application-test.yml`)

## Logging Rules

- `@Slf4j` on ALL Spring-managed classes
- NEVER log passwords, tokens, secrets
- Don't repeat MDC fields (requestId, userId) in log messages — they're automatic
- State changes: `log.info("Role '{}' (id={}) deleted by user {}", code, id, userId)`
- Audit data: use `sanitize*()` helpers to strip sensitive fields

## Naming Conventions

| Element | Pattern | Example |
|---------|---------|---------|
| Entity | Singular noun | `User`, `Role` |
| DTO | Entity + purpose + suffix | `UserCreateRequest`, `RoleResponse` |
| Service | Entity + `Service` | `UserService` |
| Controller | Entity + `Controller` | `UserController` |
| Repository | Entity + `Repository` | `UserRepository` |
| Table | `{module}_{plural}` | `auth_users`, `auth_roles` |
| Service methods | `get*`, `find*`, `create*`, `update*`, `delete*`, `toggle*` | |
| Boolean getters | `is*`, `has*` | `isActive()` |

## Import Ordering

```
1. io.*, com.*          (third-party)
2. jakarta.*
3. lombok.*
4. org.ganjp.*          (project)
5. org.springframework.*
6. java.*
```
- No wildcard imports
- No same-package imports

## Database Conventions

- PKs: `CHAR(36)` UUID (application-generated)
- Charset: `utf8mb4_unicode_ci`
- Soft delete: `is_active` flag (prefer over hard delete)
- Audit columns: `created_at`, `updated_at`, `created_by`, `updated_by`
- Constraints: `uk_table_col`, `fk_table_ref`, `idx_table_col`, `chk_table_desc`

## Security Reminders

- Stateless JWT (no server sessions, CSRF disabled)
- Dual tokens: short-lived access + long-lived refresh (hash-only in DB)
- Rate limit on login: 10 attempts/IP/60s
- Use `request.getRemoteAddr()` for IP (not X-Forwarded-For)
- Use `hasAuthority()` not `hasRole()` (roles already have ROLE_ prefix)
