# Style Guide

> Coding standards and conventions for the GJP Admin API Spring Boot project.
> All contributors **MUST** follow these rules to ensure consistency across the codebase.
>
> **Severity key:** Rules marked **MUST** are mandatory. Rules marked **SHOULD** are strongly recommended but may be relaxed with justification.

## Table of Contents

1. [Logging](#1-logging)
2. [Imports](#2-imports)
3. [Entity Classes (JPA)](#3-entity-classes-jpa)
4. [Exception Handling](#4-exception-handling)
5. [Service Layer](#5-service-layer)
6. [Controller Layer](#6-controller-layer)
7. [Javadoc](#7-javadoc)
8. [Validation](#8-validation)
9. [Naming Conventions](#9-naming-conventions)
10. [Code Formatting](#10-code-formatting)
11. [Constants and Magic Numbers](#11-constants-and-magic-numbers)
12. [Repository Layer](#12-repository-layer)
13. [Testing](#13-testing)
14. [API Design & REST Conventions](#14-api-design--rest-conventions)
15. [Entity Lifecycle & Base Class](#15-entity-lifecycle--base-class)
16. [Configuration & Profiles](#16-configuration--profiles)
17. [Async & MDC Propagation](#17-async--mdc-propagation)
18. [Filter & Interceptor Layer](#18-filter--interceptor-layer)
19. [Known Deviations](#19-known-deviations)
20. [Security](#20-security)
21. [Database & Migrations](#21-database--migrations)
22. [Error Handling Response Format](#22-error-handling-response-format)

---

## 1. Logging

### Rule 1.1: All Spring-managed classes must have `@Slf4j`

**MUST.** Every `@Service`, `@RestController`, `@Component`, `@Configuration`, and `@ControllerAdvice` class must be annotated with `@Slf4j` from Lombok for structured logging.

```java
@Slf4j       // Always present
@Service
@RequiredArgsConstructor
public class UserService { ... }
```

> **Rationale:** Even if the class doesn't log today, adding @Slf4j upfront avoids importing a logger later and ensures every class is ready for debugging.

### Rule 1.2: Logging level guidelines

**MUST.** Use the correct log level for each category:

| Level | Usage |
|-------|-------|
| `log.debug()` | Internal flow tracing (token validation, cache hits, method entry/exit) |
| `log.info()` | Significant state changes (user created, role deleted, password changed) |
| `log.warn()` | Recoverable issues (account locked, rate limit hit, deprecated usage) |
| `log.error()` | Unrecoverable failures (unhandled exceptions, infrastructure errors) |

### Rule 1.3: Never log sensitive data

**MUST.** Never log passwords, tokens (access or refresh), secret keys, or full request bodies containing credentials.

```java
// WRONG
log.info("User logged in with token: {}", accessToken);

// CORRECT
log.info("User '{}' logged in successfully", username);
```

### Rule 1.4: Use MDC for request-scoped context — not per-message parameters

**MUST.** Request-scoped identifiers (`requestId`, `sessionId`, `userId`, `username`, `clientIp`) are propagated via SLF4J MDC through `LoggingConfig`. Do **not** repeat them as message parameters — they appear automatically in every log line via `logback-spring.xml`.

```java
// WRONG — redundant; requestId is already in MDC
log.info("[requestId={}] User created: {}", requestId, username);

// CORRECT — MDC supplies the context automatically
log.info("User '{}' created successfully", username);
```

> **Rationale:** MDC provides consistent, machine-parseable context across all log lines for a request. Repeating it in messages adds noise and risks inconsistency.

### Rule 1.5: Log message structure — include entity identity and actor

**SHOULD.** Log messages for state-changing operations should follow the pattern:
`"Action entity 'identifier' (id=value) by user {}"` or a clear subset.

```java
// CORRECT
log.info("Role '{}' (id={}) deleted by user {}", role.getCode(), id, userId);
log.debug("Generated dual tokens for user {}: access token and refresh token", username);

// WRONG — no entity identity
log.info("Role deleted");
```

### Rule 1.6: Sanitize sensitive data for audit logging

**MUST.** Controllers that pass request/response data to the audit subsystem must use dedicated `sanitize*()` helper methods that strip passwords, tokens, and secrets before logging.

```java
// CORRECT — TokenController pattern
private Map<String, Object> sanitizeLoginRequest(LoginRequest request) {
    Map<String, Object> sanitized = new LinkedHashMap<>();
    sanitized.put("username", request.getUsername());
    // Deliberately exclude password for security
    return sanitized;
}
```

---

## 2. Imports

### Rule 2.1: No wildcard imports

**MUST.** Always use explicit imports. No `import java.util.*` or `import org.springframework.web.bind.annotation.*`.

```java
// WRONG
import java.util.*;
import org.springframework.web.bind.annotation.*;

// CORRECT
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
```

### Rule 2.2: No same-package imports

**MUST.** Do not import classes from the same package — Java resolves them automatically.

### Rule 2.3: Import ordering

**MUST.** Follow this order (alphabetical within each group), separated by blank lines:

1. Third-party libraries (`io.*`, `com.*`, etc. — e.g., `io.jsonwebtoken`)
2. `jakarta.*`
3. `lombok.*`
4. `org.ganjp.*` (project imports)
5. `org.springframework.*`
6. `java.*`

```java
import io.jsonwebtoken.Claims;              // 1. Third-party

import jakarta.servlet.http.HttpServletRequest; // 2. Jakarta

import lombok.RequiredArgsConstructor;       // 3. Lombok

import org.ganjp.api.auth.user.User;         // 4. Project
import org.ganjp.api.common.model.ApiResponse;

import org.springframework.stereotype.Service; // 5. Spring

import java.util.List;                        // 6. Java standard
```

---

## 3. Entity Classes (JPA)

### Rule 3.1: Use `@Getter` / `@Setter` instead of `@Data` on entities

**MUST.** JPA entities must not use `@Data` because it generates `equals()`, `hashCode()`, and `toString()` based on all fields, which causes issues with lazy-loaded relationships and Hibernate proxies.

```java
// CORRECT: Entity
@Getter
@Setter
@ToString(exclude = {"userRoles", "refreshTokens"})  // Exclude bidirectional/lazy relationships
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auth_users")
public class User implements UserDetails { ... }
```

> **Rationale:** `@Data` generates `equals()`/`hashCode()` using ALL fields. When Hibernate loads a proxy, accessing uninitialized lazy fields triggers `LazyInitializationException` or N+1 queries. Using `@Getter`/`@Setter` avoids this and forces explicit, ID-based equality.

### Rule 3.2: Always exclude bidirectional relationships from `@ToString`

**MUST.** Use `@ToString(exclude = {...})` to prevent `LazyInitializationException` and infinite recursion.

### Rule 3.3: Implement `equals()` and `hashCode()` based on ID only

**MUST.** Entity equality must be based on the primary key only:

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    User user = (User) o;
    return id != null && Objects.equals(id, user.id);
}

@Override
public int hashCode() {
    return Objects.hash(id);
}
```

> **Rationale:** ID-based equality works correctly with Hibernate proxies, detached entities, and `Set`/`Map` collections. The `id != null` guard returns `false` for transient (unsaved) entities, which is safer than using a generated hashCode.

### Rule 3.4: DTOs and configuration classes use `@Data`

**SHOULD.** DTOs (Request/Response classes) and `@ConfigurationProperties` classes should use `@Data` since they are simple POJOs without JPA proxying concerns.

```java
// CORRECT: DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse { ... }

// CORRECT: Configuration properties
@Data
@Configuration
@ConfigurationProperties(prefix = "security")
public class SecurityProperties { ... }
```

---

## 4. Exception Handling

### Rule 4.1: Use project-specific exception types

**MUST.** Map exceptions to the correct HTTP status:

| Exception | HTTP Status | Usage |
|-----------|------------|-------|
| `IllegalArgumentException` | 400 | Invalid input, business rule violation on input |
| `BusinessException` | 400 | Business logic violation in self-service operations (e.g., current password mismatch) |
| `BadCredentialsException` | 401 | Authentication failure |
| `ResourceNotFoundException` | 404 | Entity not found by ID/code |
| `DuplicateResourceException` | 409 | Unique constraint violation (use `.of()` factory method) |
| `IllegalStateException` | 409 | Operation not allowed in current state (e.g., system role modification) |

### Rule 4.2: Never use generic `RuntimeException`

**MUST.** Always use a specific exception type from the table above.

### Rule 4.3: Use `BusinessException` for user-facing self-service rule violations

**MUST.** Use `BusinessException` for user-facing self-service violations (profile updates, password changes) where the user needs actionable feedback. Use `IllegalArgumentException` for admin-facing validation in management services.

```java
// Self-service (UserProfileService)
throw new BusinessException("Current password is incorrect");

// Admin management (UserService)
throw new IllegalArgumentException("Username is already taken");
```

### Rule 4.4: Use `ResourceNotFoundException` consistently for entity lookups

**MUST.** All entity-not-found scenarios must use `ResourceNotFoundException`:

```java
// CORRECT
User user = userRepository.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

// WRONG
User user = userRepository.findById(id)
    .orElseThrow(() -> new BusinessException("User not found: " + id));
```

### Rule 4.5: Use `DuplicateResourceException.of()` factory method

**MUST.** Use the static factory for consistent messages:

```java
// CORRECT
throw DuplicateResourceException.of("Role", "code", roleCreateRequest.getCode());

// WRONG
throw new DuplicateResourceException("Role with code already exists");
```

---

## 5. Service Layer

### Rule 5.1: Constructor injection via `@RequiredArgsConstructor`

**MUST.** All service and configuration classes must use Lombok's `@RequiredArgsConstructor` with `private final` fields. No manual constructors for dependency injection.

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;  // Injected via constructor
}
```

> **Rationale:** Lombok generates the constructor from `final` fields, which guarantees immutability and makes it impossible to forget a dependency. Manual constructors drift out of sync when dependencies are added/removed.

### Rule 5.2: `@Transactional` on all data-modifying methods

**MUST.** Every service method that creates, updates, or deletes data must be annotated with `@Transactional`.

### Rule 5.3: `@Transactional(readOnly = true)` on read-only methods

**MUST.** Every service method that only reads data must be annotated with `@Transactional(readOnly = true)`. This applies to all `get*`, `find*`, `is*`, `count*`, and `check*` methods that issue database queries.

```java
// CORRECT
@Transactional(readOnly = true)
public UserResponse getUserById(String id) {
    return userRepository.findById(id)
            .map(this::mapToUserResponse)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
}

// WRONG — missing readOnly
public UserResponse getUserById(String id) { ... }
```

> **Rationale:** `readOnly = true` provides three benefits:
> 1. **Performance:** Hibernate skips dirty-checking on managed entities (no snapshot comparison at flush time)
> 2. **Database optimization:** Some databases (e.g., MySQL with InnoDB) can optimize read-only transactions by avoiding undo log overhead
> 3. **Intent documentation:** Makes it clear this method does not modify data, which helps reviewers and prevents accidental writes

### Rule 5.4: Use `REQUIRES_NEW` for independent side-effect recording

When a side effect (e.g., recording a failed login attempt) must persist even if the calling transaction rolls back, extract it into a separate `@Service` class and use `@Transactional(propagation = Propagation.REQUIRES_NEW)`.

```java
// LoginFailureService.java — separate bean so Spring AOP can proxy it
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordFailedLogin(User user) {
    // This persists even if the caller's auth transaction rolls back
    userRepository.updateLoginFailureByIdNative(user.getId(), LocalDateTime.now());
}
```

> **Rationale:** `REQUIRES_NEW` opens an independent transaction. If the outer transaction (e.g., `authenticate()`) throws an exception and rolls back, the failure counter still commits. This pattern must live in a separate bean because Spring's proxy-based AOP cannot intercept `private` or `this.method()` calls within the same class.

### Rule 5.5: Consistent audit parameter naming

**MUST.** Service methods that need to record the acting user must use `userId` (not `username`) as the parameter name, since the value comes from the JWT token's `userId` claim.

```java
// CORRECT
public void deleteRole(String id, String userId) { ... }

// WRONG
public void deleteRole(String id, String username) { ... }
```

---

## 6. Controller Layer

### Rule 6.1: Every endpoint must have `@PreAuthorize`

**MUST.** No controller method should lack an authorization annotation:

```java
@PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")  // Admin ops
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")                                 // Destructive ops
@PreAuthorize("isAuthenticated()")                                                 // Self-service ops
```

### Rule 6.2: Consistent response pattern

**MUST.** All endpoints must return `ResponseEntity<ApiResponse<T>>` using the `ApiResponse` envelope:

```java
return ResponseEntity.ok(ApiResponse.success(data, "Message"));
return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, "Message"));
```

### Rule 6.3: Extract `userId` from JWT consistently

**MUST.** Always use `jwtUtils.extractUserIdFromToken(request)` — never duplicate extraction logic.

### Rule 6.4: Avoid manual JSON construction

**MUST.** Never construct JSON responses via `String.format()` or string concatenation. Use `ObjectMapper` or the `ApiResponse` envelope for all JSON responses, including error handlers and filters.

```java
// WRONG
String json = String.format("{\"status\":{\"code\":401,\"message\":\"%s\"}}", errorMsg);
response.getWriter().write(json);

// CORRECT
ObjectMapper mapper = new ObjectMapper();
ApiResponse<?> apiResponse = ApiResponse.error(401, "Unauthorized", errors);
response.getWriter().write(mapper.writeValueAsString(apiResponse));
```

> **Rationale:** Manual JSON construction is fragile, error-prone (escaping issues), and violates the single-source-of-truth principle for the API response format. If the `ApiResponse` envelope changes, manual JSON strings silently diverge.

> **Exception:** Servlet filters (e.g., `LoginRateLimitFilter`, `AuthenticationEntryPoint`) run outside Spring MVC and cannot inject beans easily. In these cases, inject a shared `ObjectMapper` bean or use a static helper. If that is not feasible, document the manual JSON as a known deviation with a `// TODO: migrate to ObjectMapper` comment.

---

## 7. Javadoc

### Rule 7.1: All public methods must have Javadoc

**MUST.** Every public method in controllers, services, and utility classes must have a Javadoc comment with at minimum a one-line description.

```java
/**
 * Get all roles sorted by sort order and name.
 *
 * @return list of all roles
 */
public List<RoleResponse> getAllRoles() { ... }
```

### Rule 7.2: Controller Javadoc should describe the endpoint

**SHOULD.** Controller Javadoc should describe the HTTP semantics:

```java
/**
 * Retrieve all roles in the system.
 *
 * @return list of roles sorted by sort order
 */
@PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")
@GetMapping
public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() { ... }
```

### Rule 7.3: Entity fields should have Javadoc for non-obvious fields

**SHOULD.** Standard audit fields (`createdAt`, `updatedAt`) do not need Javadoc. Domain-specific fields should.

---

## 8. Validation

### Rule 8.1: Unified password policy across all DTOs

**MUST.** All DTOs accepting passwords must use the same constraints:

```java
@Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^+=])(?=\\S+$).+$",
         message = "Password must contain at least one uppercase, one lowercase, one digit, one special character, and no whitespace")
```

### Rule 8.2: Use Bean Validation on DTOs, not in service methods

**SHOULD.** Prefer declarative validation on DTOs; service methods should focus on business rules (uniqueness, authorization).

### Rule 8.3: Use `@AssertTrue` for cross-field validation

**MUST.** When a DTO has mutually exclusive or co-dependent fields, use `@AssertTrue` with a descriptive `message` rather than custom validators.

```java
// CORRECT — LoginRequest: exactly one of username/email/mobile must be provided
@AssertTrue(message = "Exactly one login method (username, email, or mobile) must be provided")
public boolean isValidLoginMethod() {
    int count = 0;
    if (username != null && !username.isBlank()) count++;
    if (email != null && !email.isBlank()) count++;
    if (mobile != null && !mobile.isBlank()) count++;
    return count == 1;
}
```

> **Rationale:** `@AssertTrue` is evaluated by the Bean Validation framework alongside field-level annotations, keeping all validation declarative and discoverable in one place.

### Rule 8.4: Always use `@Valid` on request body parameters

**MUST.** Controller methods accepting request DTOs must annotate the parameter with `@Valid` to trigger Bean Validation.

```java
// CORRECT
@PostMapping
public ResponseEntity<ApiResponse<RoleResponse>> createRole(
        @Valid @RequestBody RoleCreateRequest request, HttpServletRequest httpRequest) { ... }

// WRONG — validation annotations on DTO are silently ignored
@PostMapping
public ResponseEntity<ApiResponse<RoleResponse>> createRole(
        @RequestBody RoleCreateRequest request, HttpServletRequest httpRequest) { ... }
```

---

## 9. Naming Conventions

**MUST.** Follow these naming conventions throughout the project:

| Element | Convention | Example |
|---------|-----------|---------|
| Entity class | Singular noun | `User`, `Role` |
| DTO class | Entity + purpose + suffix | `UserCreateRequest`, `UserResponse` |
| Service class | Entity + `Service` | `UserService`, `AuthService` |
| Controller class | Entity + `Controller` | `UserController` |
| Repository | Entity + `Repository` | `UserRepository` |
| Service methods | `get*`, `find*`, `create*`, `update*`, `delete*`, `toggle*` | `getUserById()` |
| Boolean getters | `is*`, `has*` | `isActive()`, `hasChildren()` |
| Private mappers | `mapTo*Response` or `buildTo*Response` | `mapToRoleResponse()` |

---

## 10. Code Formatting

### Rule 10.1: Class-level annotation ordering

**MUST.** Follow this annotation order:

```java
@Slf4j                    // 1. Lombok utility
@Getter @Setter           // 2. Lombok data (or @Data for DTOs)
@Builder                  // 3. Lombok builder
@NoArgsConstructor        // 4. Lombok constructors
@AllArgsConstructor
@Entity / @Service / ...  // 5. Spring/JPA annotations
@Table(...)               // 6. JPA mapping
@RequiredArgsConstructor  // 7. Spring DI (for services/controllers)
public class Foo { ... }
```

### Rule 10.2: Method ordering in classes

**SHOULD.** Follow this order:

1. Static fields / constants
2. Instance fields (injected dependencies)
3. Public methods (CRUD order: create, read, update, delete)
4. Private helper methods

### Rule 10.3: Blank lines

**MUST.**

- One blank line between methods
- One blank line between logical sections within a method
- No trailing blank lines at end of file

---

## 11. Constants and Magic Numbers

### Rule 11.1: Extract magic numbers into named constants

**MUST.** All numeric literals with domain meaning must be extracted into `private static final` constants with descriptive names.

```java
// CORRECT
private static final int MAX_FAILED_ATTEMPTS = 5;
private static final int LOCK_DURATION_MINUTES = 30;

// WRONG — inline magic number
if (user.getFailedLoginAttempts() >= 5) { ... }
```

### Rule 11.2: `@Scheduled` rate/delay values should reference constants or config

**SHOULD.** Externalize scheduled task intervals:

```java
// ACCEPTABLE — clearly named constant
private static final long CLEANUP_INTERVAL_MS = 1_800_000; // 30 minutes
@Scheduled(fixedRate = CLEANUP_INTERVAL_MS)

// PREFERRED — externalized to application.yml
@Scheduled(fixedRateString = "${cleanup.interval-ms:1800000}")
```

> **Rationale:** Named constants make the intent clear ("what does 1800000 mean?") and allow changing values without reading the code. Config-based values allow environment-specific tuning without recompilation.

---

## 12. Repository Layer

### Rule 12.1: Prefer derived query methods for simple lookups

**SHOULD.** For single-condition lookups and existence checks, use Spring Data derived method names.

```java
// CORRECT — simple, self-documenting
Optional<User> findByUsername(String username);
boolean existsByEmail(String email);
List<UserRole> findByUserAndActiveTrue(User user);
```

### Rule 12.2: Use `@Query` with JPQL for multi-condition queries

**MUST.** When a query has multiple conditions, joins, or temporal logic, use `@Query` with JPQL rather than long derived method names.

```java
// CORRECT — readable JPQL
@Query("SELECT ur FROM UserRole ur WHERE ur.user = :user AND ur.active = true " +
       "AND (ur.expiresAt IS NULL OR ur.expiresAt > :now)")
List<UserRole> findActiveNonExpiredRoles(@Param("user") User user, @Param("now") LocalDateTime now);

// WRONG — derived name is unreadable
List<UserRole> findByUserAndActiveTrueAndExpiresAtIsNullOrExpiresAtAfter(User user, LocalDateTime now);
```

### Rule 12.3: Use `nativeQuery = true` only when JPQL cannot express the query

**SHOULD.** Reserve native SQL for database-specific features (e.g., MySQL enum handling, JSON functions, window functions).

```java
// ACCEPTABLE — native query needed for direct column update with enum
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query(value = "UPDATE auth_users SET failed_login_attempts = failed_login_attempts + 1, " +
               "last_failed_login_at = :now WHERE id = :userId", nativeQuery = true)
void updateLoginFailureByIdNative(@Param("userId") String userId, @Param("now") LocalDateTime now);
```

### Rule 12.4: `@Modifying` on all update/delete queries

**MUST.** All `@Query`-based update and delete operations must include `@Modifying(clearAutomatically = true, flushAutomatically = true)` to keep the persistence context in sync.

```java
// CORRECT
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
int revokeAllByUserId(@Param("userId") String userId);

// WRONG — stale persistence context after bulk update
@Modifying
@Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId")
int revokeAllByUserId(@Param("userId") String userId);
```

### Rule 12.5: Use `Page<T>` for paginated endpoints

**MUST.** Repository methods backing paginated API endpoints must accept `Pageable` and return `Page<T>`.

```java
// CORRECT
@Query("SELECT u FROM User u WHERE u.username LIKE %:keyword%")
Page<User> findByUsernameContaining(@Param("keyword") String keyword, Pageable pageable);
```

> **Rationale:** `Page<T>` provides total count and pagination metadata needed by `PaginatedResponse.of(page)` without a separate count query method.

---

## 13. Testing

### Rule 13.1: Test class naming conventions

**MUST.** Use the following suffixes:

| Test type | Suffix | Example |
|-----------|--------|---------|
| Unit test (no Spring context) | `*Test` | `UserServiceTest` |
| Integration test (Spring context) | `*IntegrationTest` | `UserRepositoryIntegrationTest` |
| Controller test (MockMvc) | `*ControllerTest` | `RoleControllerTest` |

### Rule 13.2: Test method naming — describe behavior

**MUST.** Test method names must describe the expected behavior and condition using underscores:

```java
// CORRECT
@Test
void should_returnUser_when_validIdProvided() { ... }

@Test
void should_throwResourceNotFoundException_when_userNotFound() { ... }

@Test
void should_lockAccount_when_maxFailedAttemptsExceeded() { ... }

// WRONG — describes implementation, not behavior
@Test
void testGetUserById() { ... }

@Test
void test1() { ... }
```

### Rule 13.3: Given-When-Then structure (Arrange-Act-Assert)

**MUST.** Every test method must follow the three-phase structure with comment separators:

```java
@Test
void should_createRole_when_codeIsUnique() {
    // Given
    RoleCreateRequest request = RoleCreateRequest.builder()
            .code("ROLE_EDITOR")
            .name("Editor")
            .build();
    when(roleRepository.existsByCode("ROLE_EDITOR")).thenReturn(false);
    when(roleRepository.save(any(Role.class))).thenReturn(mockRole);

    // When
    RoleResponse response = roleService.createRole(request, "user-123");

    // Then
    assertThat(response.getCode()).isEqualTo("ROLE_EDITOR");
    verify(roleRepository).save(any(Role.class));
}
```

### Rule 13.4: Choose the right test slice

**MUST.** Use the narrowest Spring test slice that covers the code under test:

| Annotation | When to use | Loads |
|------------|------------|-------|
| `@ExtendWith(MockitoExtension.class)` | Pure unit tests — services, utilities | No Spring context |
| `@WebMvcTest(FooController.class)` | Controller request mapping, serialization, security | Web layer only |
| `@DataJpaTest` | Repository custom queries, JPQL correctness | JPA + H2 |
| `@SpringBootTest` | Full integration / end-to-end | Entire context |

```java
// CORRECT — unit test for service logic, no Spring context needed
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {
    @Mock private RoleRepository roleRepository;
    @InjectMocks private RoleService roleService;
    ...
}

// CORRECT — repository integration test with H2
@DataJpaTest
class UserRepositoryIntegrationTest {
    @Autowired private UserRepository userRepository;
    ...
}
```

> **Rationale:** Narrow slices run faster and isolate failures. `@SpringBootTest` should be reserved for tests that genuinely need the full application context (e.g., testing filter chains end-to-end).

### Rule 13.5: Use AssertJ for assertions

**SHOULD.** Prefer AssertJ's fluent API over JUnit's `assertEquals`/`assertTrue` for readability.

```java
// CORRECT — AssertJ
assertThat(roles).hasSize(3);
assertThat(response.getCode()).isEqualTo("ROLE_ADMIN");
assertThatThrownBy(() -> service.getUserById("bad-id"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("User");

// WRONG — JUnit assertions (less readable for complex checks)
assertEquals(3, roles.size());
assertTrue(response.getCode().equals("ROLE_ADMIN"));
```

### Rule 13.6: Security testing with `@WithMockUser`

**MUST.** Controller tests must verify authorization rules using Spring Security test annotations:

```java
@WebMvcTest(RoleController.class)
class RoleControllerTest {

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void should_returnRoles_when_adminUser() throws Exception {
        mockMvc.perform(get("/v1/roles"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void should_return403_when_nonAdminUser() throws Exception {
        mockMvc.perform(get("/v1/roles"))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/v1/roles"))
                .andExpect(status().isUnauthorized());
    }
}
```

### Rule 13.7: H2 for integration tests, Mockito for unit tests

**MUST.** Integration tests (`@DataJpaTest`, `@SpringBootTest`) use the H2 in-memory database configured in `application-test.yml`. Unit tests mock repositories with Mockito — never hit a real database.

### Rule 13.8: What to test

**SHOULD.** Prioritize tests by risk:

| Priority | What to test | How |
|----------|-------------|-----|
| **High** | Service business logic (create, update, delete, edge cases) | Unit test with mocked repos |
| **High** | Controller authorization (`@PreAuthorize` rules) | `@WebMvcTest` + `@WithMockUser` |
| **High** | Custom `@Query` correctness (JPQL, native) | `@DataJpaTest` + H2 |
| **Medium** | Request validation (`@Valid`, `@AssertTrue`) | `@WebMvcTest` with invalid payloads |
| **Medium** | Exception handler mapping (status codes, error format) | `@WebMvcTest` |
| **Low** | Derived query methods (Spring Data generates them) | Skip unless complex |

---

## 14. API Design & REST Conventions

### Rule 14.1: URL versioning with `/v1/` prefix

**MUST.** All API endpoints must include a version prefix. Current version is `v1`.

```java
@RequestMapping("/v1/roles")      // Resource endpoints
@RequestMapping("/v1/auth/tokens") // Auth endpoints
@RequestMapping("/v1/audit")       // Audit endpoints
```

### Rule 14.2: RESTful HTTP method mapping

**MUST.** Use the correct HTTP method for each operation:

| HTTP Method | Operation | Success Status | Example |
|-------------|----------|----------------|---------|
| `POST` | Create resource | `201 Created` | `POST /v1/roles` |
| `GET` | Read resource(s) | `200 OK` | `GET /v1/roles/{id}` |
| `PUT` | Full update | `200 OK` | `PUT /v1/roles/{id}` |
| `PATCH` | Partial update | `200 OK` | `PATCH /v1/roles/{id}/toggle-active` |
| `DELETE` | Delete resource | `200 OK` | `DELETE /v1/roles/{id}` |

### Rule 14.3: Paginated list endpoints

**MUST.** List endpoints that may return large datasets must support pagination via Spring's `Pageable`:

```java
@GetMapping
public ResponseEntity<ApiResponse<PaginatedResponse<UserResponse>>> getAllUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {
    Page<User> users = userRepository.findAll(PageRequest.of(page, size));
    return ResponseEntity.ok(ApiResponse.success(PaginatedResponse.of(users), "Users retrieved"));
}
```

### Rule 14.4: Consistent response envelope

**MUST.** All responses must use `ApiResponse<T>` which provides:

```json
{
  "status": { "code": 200, "message": "Success" },
  "data": { ... },
  "meta": { "serverDateTime": "...", "requestId": "...", "sessionId": "..." }
}
```

- Success: `ApiResponse.success(data, message)`
- Error: `ApiResponse.error(code, message, errors)`

---

## 15. Entity Lifecycle & Base Class

### Rule 15.1: Extend `BaseEntity` for audit fields

**MUST.** All JPA entities must extend `BaseEntity` which provides `createdAt`, `updatedAt`, `createdBy`, and `updatedBy` with `@PrePersist` / `@PreUpdate` lifecycle hooks.

```java
// CORRECT
@Entity
@Table(name = "auth_roles")
public class Role extends BaseEntity { ... }

// WRONG — duplicating audit fields manually
@Entity
public class Role {
    private LocalDateTime createdAt;  // Already in BaseEntity
    private LocalDateTime updatedAt;  // Already in BaseEntity
}
```

### Rule 15.2: UUID primary key as `CHAR(36)`

**MUST.** All entities use a `String` UUID primary key, stored as `CHAR(36)` in MySQL. The UUID is assigned in `@PrePersist`, not auto-generated by the database.

```java
@Id
@Column(columnDefinition = "CHAR(36)")
private String id;

@PrePersist
protected void onCreate() {
    if (this.id == null) {
        this.id = UUID.randomUUID().toString();
    }
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
}
```

> **Rationale:** Application-generated UUIDs are portable across databases, avoid auto-increment locking, and can be generated before `persist()` (useful for setting up bidirectional relationships).

### Rule 15.3: Soft delete via `isActive` flag

**SHOULD.** Prefer soft delete over hard delete. Entities should include an `isActive` boolean defaulting to `true`.

```java
@Builder.Default
@Column(name = "is_active")
private boolean isActive = true;
```

Delete operations toggle the flag rather than removing the row:

```java
// CORRECT — soft delete
role.setActive(false);
roleRepository.save(role);

// WRONG — hard delete (data loss, breaks audit trail)
roleRepository.delete(role);
```

> **Rationale:** Soft delete preserves audit trail, allows recovery, and avoids cascading foreign key deletions.

### Rule 15.4: Use `@Builder.Default` for field defaults

**MUST.** When using `@Builder`, fields with non-null defaults must use `@Builder.Default` — otherwise the builder ignores the field initializer.

```java
// CORRECT
@Builder.Default
@Column(name = "is_active")
private boolean isActive = true;

@Builder.Default
@OneToMany(mappedBy = "parentRole")
private List<Role> childRoles = new ArrayList<>();

// WRONG — builder will set isActive to false (default for boolean)
@Column(name = "is_active")
private boolean isActive = true;
```

---

## 16. Configuration & Profiles

### Rule 16.1: Never hardcode secrets

**MUST.** Database credentials, JWT secrets, API keys, and all sensitive values must come from environment variables or a `.env` file (loaded via `spring-dotenv`).

```yaml
# CORRECT — application-dev.yml
spring:
  datasource:
    username: ${MYSQL_USERNAME}
    password: ${MYSQL_PASSWORD}

# WRONG — hardcoded credentials
spring:
  datasource:
    username: root
    password: mypassword123
```

### Rule 16.2: Profile structure

**MUST.** Configuration is split across profile-specific files:

| File | Purpose |
|------|---------|
| `application.yml` | Shared defaults (server port, Jackson, JWT expiry, CORS, audit) |
| `application-dev.yml` | Dev database, `show-sql: true`, DEBUG logging |
| `application-prod.yml` | Prod database, `ddl-auto: none`, WARN logging, file appender |
| `application-test.yml` | H2 in-memory database, test-specific overrides |

### Rule 16.3: Use `@ConfigurationProperties` for typed config

**MUST.** Configuration groups with multiple related properties must be bound to a typed class, not read via `@Value`.

```java
// CORRECT — typed, validated, IDE-supported
@Data
@Configuration
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {
    private Jwt jwt;
    private List<String> publicEndpoints;
    private Cors cors;
    ...
}

// WRONG — scattered @Value annotations
@Value("${security.jwt.secret}") private String jwtSecret;
@Value("${security.jwt.expiration}") private int jwtExpiration;
```

> **Rationale:** `@ConfigurationProperties` provides type safety, validation (`@Validated`), IDE auto-completion, and groups related settings into a single injectable bean.

### Rule 16.4: Hibernate DDL policy

**MUST.** Only `dev` profile may use `ddl-auto: update`. Production must use `ddl-auto: none` with Flyway or Liquibase for schema migrations.

---

## 17. Async & MDC Propagation

### Rule 17.1: Use `@Async` for non-blocking side effects

**SHOULD.** Operations that should not block the HTTP response (e.g., audit logging, notifications) must be annotated with `@Async` and run on a dedicated thread pool.

```java
// CORRECT — AuditService
@Async("auditTaskExecutor")
public void logSuccess(String userId, String username, String httpMethod,
                       String endpoint, int statusCode, long durationMs) { ... }
```

### Rule 17.2: Propagate MDC context to async threads

**MUST.** MDC context is thread-local and lost when switching threads. Before dispatching async work, capture the current MDC context and re-apply it in the async thread.

```java
// CORRECT — capture before, apply inside
Map<String, String> mdcContext = LoggingConfig.captureCurrentMdcContext();

executor.submit(() -> {
    LoggingConfig.applyMdcContext(mdcContext);
    try {
        // async work — logs will include requestId, userId, etc.
    } finally {
        LoggingConfig.clearMdcContext();
    }
});
```

> **Rationale:** Without MDC propagation, async log lines lose `requestId`, `userId`, and other context, making correlation with the originating request impossible.

### Rule 17.3: Configure async thread pools as Spring beans

**MUST.** Async executors must be defined as `@Bean` in a `@Configuration` class with bounded queue and pool sizes, not created ad-hoc via `new ThreadPoolExecutor()`.

```java
// CORRECT — AuditConfig
@Bean(name = "auditTaskExecutor")
public Executor auditTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(auditProperties.getThreadPool().getCoreSize());
    executor.setMaxPoolSize(auditProperties.getThreadPool().getMaxSize());
    executor.setQueueCapacity(auditProperties.getThreadPool().getQueueCapacity());
    executor.setThreadNamePrefix("audit-");
    executor.initialize();
    return executor;
}
```

---

## 18. Filter & Interceptor Layer

### Rule 18.1: Extend `OncePerRequestFilter`

**MUST.** All servlet filters must extend `OncePerRequestFilter` to guarantee single execution per request (even with forward/include dispatches).

```java
// CORRECT
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain) { ... }
}

// WRONG — raw Filter interface may execute multiple times
public class RequestIdFilter implements Filter { ... }
```

### Rule 18.2: Use `@Order` for filter priority

**MUST.** Filters must declare their execution order explicitly:

| Order | Filter | Purpose |
|-------|--------|---------|
| `HIGHEST_PRECEDENCE` | `RequestIdFilter` | Assign requestId & MDC context |
| `HIGHEST_PRECEDENCE + 1` | `LoginRateLimitFilter` | Rate-limit login attempts |
| (default) | `JwtAuthenticationFilter` | Authenticate JWT tokens |

### Rule 18.3: Always clean up MDC in `finally` block

**MUST.** Any filter that sets MDC keys must clear them in a `finally` block to prevent context leaking to subsequent requests on the same thread.

```java
@Override
protected void doFilterInternal(HttpServletRequest request,
        HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
    try {
        LoggingConfig.setMdcContext(request);
        chain.doFilter(request, response);
    } finally {
        LoggingConfig.clearMdcContext();  // MUST — prevents thread-local leak
    }
}
```

---

## 19. Known Deviations

> This section tracks intentional departures from the style guide rules with justification and migration plans.

| Location | Rule Violated | Deviation | Tracking |
|----------|--------------|-----------|----------|
| `LoginRateLimitFilter` | Rule 6.4 (no manual JSON) | Constructs JSON via `String.format()` because filter runs outside Spring MVC and cannot inject `ObjectMapper` easily | `// TODO: migrate to ObjectMapper (Rule 6.4)` |
| `SecurityConfig.authenticationEntryPoint` | Rule 6.4 (no manual JSON) | Same reason — lambda-based entry point outside MVC | `// TODO: migrate to ObjectMapper (Rule 6.4)` |

> When adding a new deviation, always include a `// TODO` comment in the source code referencing the rule number, and add a row to this table.

---

## 20. Security

### Rule 20.1: Stateless session — no server-side session state

**MUST.** The API is stateless. Spring Security must be configured with `SessionCreationPolicy.STATELESS`. Never store user state in `HttpSession`.

```java
// CORRECT — SecurityConfig
http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
```

### Rule 20.2: CSRF disabled for stateless JWT APIs

**MUST.** CSRF protection must be explicitly disabled since the API uses JWT bearer tokens (not cookies) for authentication. Document the reason in the security config.

```java
// CORRECT — justified by stateless JWT design
http.csrf(AbstractHttpConfigurer::disable);
```

> **Rationale:** CSRF attacks exploit cookie-based authentication. Since this API authenticates via `Authorization: Bearer <token>` headers (not cookies), CSRF protection is unnecessary and would block legitimate API calls.

### Rule 20.3: CORS — whitelist origins from configuration

**MUST.** Allowed origins must come from `SecurityProperties.cors.allowedOrigins`, never hardcoded. Production must not allow `*` as an origin.

```java
// CORRECT — from config
CorsConfiguration config = new CorsConfiguration();
config.setAllowedOrigins(securityProperties.getCors().getAllowedOrigins());
config.setAllowCredentials(true);

// WRONG — hardcoded wildcard
config.addAllowedOrigin("*");
```

### Rule 20.4: Dual token lifecycle (access + refresh)

**MUST.** The API uses a dual token system:

| Token | Lifetime | Contains | Storage |
|-------|----------|----------|---------|
| Access token | Short (configurable, default 1h) | `sub` (username), `authorities`, `userId`, `jti` | Client memory only |
| Refresh token | Long (configurable, default 30d) | `sub` (username), `type: refresh`, `jti` | DB (`auth_refresh_tokens`) — **hash only** |

Rules:
- Access tokens are validated statelessly via signature + expiration
- Refresh tokens are validated against the database (revocation check)
- Token ID (`jti` claim) is a UUID that enables blacklisting
- On refresh, the old refresh token is revoked and a new pair is issued (rotation)

### Rule 20.5: Token blacklisting for logout and revocation

**MUST.** When a user logs out or an admin revokes access, the access token's `jti` must be added to `auth_token_blacklist`. The `JwtAuthenticationFilter` checks the blacklist on every request.

```java
// CORRECT — JwtAuthenticationFilter checks blacklist before validation
if (tokenBlacklistService != null && tokenBlacklistService.isTokenBlacklisted(tokenId)) {
    log.debug("Token {} is blacklisted, skipping authentication", tokenId);
    chain.doFilter(request, response);
    return;
}
```

### Rule 20.6: Never store plain tokens — hash before persisting

**MUST.** Refresh tokens stored in the database must be hashed (SHA-256). Never store plain JWT strings in `auth_refresh_tokens`.

> **Rationale:** If the database is compromised, hashed tokens cannot be used directly. The attacker would need the original JWT string, which is only held by the client.

### Rule 20.7: Rate limiting on authentication endpoints

**MUST.** The login endpoint (`POST /v1/auth/tokens`) must be protected by `LoginRateLimitFilter` with configurable limits (default: 10 attempts per IP per 60s window). Exceeding the limit returns HTTP 429.

> **Limitation:** The current implementation uses in-memory `ConcurrentHashMap` — not shared across clustered deployments. For multi-instance deployments, migrate to Redis-based rate limiting.

### Rule 20.8: `@PreAuthorize` expression patterns

**MUST.** Use these standard expressions consistently:

```java
// Admin operations (user management, role CRUD)
@PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")

// Destructive operations (hard delete, system config changes)
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")

// Self-service operations (profile update, password change)
@PreAuthorize("isAuthenticated()")
```

> **Rationale:** Using `hasAuthority()` (not `hasRole()`) matches the way authorities are stored in the JWT `authorities` claim. `hasRole()` prepends `ROLE_` automatically, which causes double-prefix issues since our roles already include the `ROLE_` prefix.

### Rule 20.9: Security headers

**MUST.** The security filter chain must set these headers:

| Header | Value | Purpose |
|--------|-------|---------|
| `X-Frame-Options` | `SAMEORIGIN` | Prevent clickjacking |
| `Content-Security-Policy` | `default-src 'self'` | Restrict resource loading |

```java
// CORRECT — SecurityConfig
http.headers(headers -> {
    headers.frameOptions(frame -> frame.sameOrigin());
    headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"));
});
```

### Rule 20.10: Use TCP remote address for IP detection — not headers

**MUST.** Use `request.getRemoteAddr()` to identify client IPs. Do **not** trust `X-Forwarded-For` or `X-Real-IP` headers, as they can be spoofed.

```java
// CORRECT
String clientIp = request.getRemoteAddr();

// WRONG — spoofable
String clientIp = request.getHeader("X-Forwarded-For");
```

> **Exception:** If the application runs behind a trusted reverse proxy (Nginx, AWS ALB), configure Spring's `ForwardedHeaderFilter` and document the trusted proxy IPs.

---

## 21. Database & Migrations

### Rule 21.1: Table naming — module prefix + plural snake_case

**MUST.** All tables must follow the pattern `{module}_{entity_plural}`:

| Module | Table | Entity |
|--------|-------|--------|
| `auth` | `auth_users` | `User` |
| `auth` | `auth_roles` | `Role` |
| `auth` | `auth_user_roles` | `UserRole` |
| `auth` | `auth_refresh_tokens` | `RefreshToken` |
| `auth` | `auth_token_blacklist` | `BlacklistedToken` |
| `audit` | `audit_logs` | `AuditLog` |

Future modules (e.g., `cms`, `master`) must follow the same prefix convention.

### Rule 21.2: Primary key — `CHAR(36)` UUID

**MUST.** All primary keys must be `CHAR(36)` UUIDs. See [Rule 15.2](#rule-152-uuid-primary-key-as-char36) for the Java implementation.

```sql
-- CORRECT
id CHAR(36) NOT NULL PRIMARY KEY

-- WRONG — auto-increment
id BIGINT AUTO_INCREMENT PRIMARY KEY
```

### Rule 21.3: Character set — `utf8mb4_unicode_ci`

**MUST.** All tables and string columns must use `utf8mb4` charset with `unicode_ci` collation for full Unicode support (including emoji).

```sql
CREATE TABLE auth_users (
    ...
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### Rule 21.4: Standard audit columns on every table

**MUST.** Every table must include these audit columns (provided by `BaseEntity` in Java):

```sql
created_at DATETIME NOT NULL,
updated_at DATETIME NOT NULL,
created_by CHAR(36),
updated_by CHAR(36)
```

### Rule 21.5: Constraint naming conventions

**MUST.** Follow these naming patterns for database constraints:

| Constraint | Pattern | Example |
|------------|---------|---------|
| Primary key | `pk_{table}` | `pk_auth_users` |
| Unique | `uk_{table}_{column}` | `uk_auth_users_username` |
| Foreign key | `fk_{table}_{referenced_table}` | `fk_auth_user_roles_user` |
| Index | `idx_{table}_{column(s)}` | `idx_audit_logs_user_id` |
| Check | `chk_{table}_{description}` | `chk_auth_users_username_format` |

### Rule 21.6: Database-level validation for critical fields

**SHOULD.** Use `CHECK` constraints for format validation on critical fields (username, email, phone) as a defense-in-depth layer alongside Java Bean Validation.

```sql
-- CORRECT — database enforces format even for direct SQL inserts
CONSTRAINT chk_auth_users_username_format CHECK (username REGEXP '^[a-zA-Z0-9._-]{3,50}$'),
CONSTRAINT chk_auth_users_email_format CHECK (email REGEXP '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$')
```

### Rule 21.7: Foreign key strategy

**MUST.** Foreign keys must specify `ON DELETE` and `ON UPDATE` behavior explicitly:

| Relationship | ON DELETE | ON UPDATE | Example |
|-------------|-----------|-----------|---------|
| Parent-child (composition) | `CASCADE` | `CASCADE` | `auth_user_roles` → `auth_users` |
| Reference (association) | `SET NULL` | `CASCADE` | `audit_logs.user_id` → `auth_users` |
| Append-only logs | No FK | — | `audit_logs` (no FK on `user_id` for performance) |

### Rule 21.8: Migration file conventions

**MUST.** SQL scripts in `scripts/database/mysql/` must follow:

| Pattern | Example | Purpose |
|---------|---------|---------|
| `{NN}-{module}-{description}.sql` | `01-gjp-auth.sql` | Initial schema for a module |
| `{NN}-{module}-{migration}.sql` | `02-gjp-auth-add-mfa-columns.sql` | Schema changes |

- Scripts must be idempotent where possible (`CREATE TABLE IF NOT EXISTS`, `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`)
- Include `DROP TABLE IF EXISTS` only in dev/test scripts, never in production migrations
- Each script must include a header comment with author, date, and purpose

### Rule 21.9: Index strategy

**SHOULD.** Create indexes for:
- All foreign key columns (MySQL does this automatically for InnoDB)
- Columns used in `WHERE` clauses of frequent queries
- Columns used in `ORDER BY` on large tables
- Composite indexes for multi-column lookups (most selective column first)

```sql
-- CORRECT — composite index matching query patterns
CREATE INDEX idx_audit_logs_user_action ON audit_logs(user_id, action_type, created_at);
```

---

## 22. Error Handling Response Format

### Rule 22.1: All errors go through `GlobalExceptionHandler`

**MUST.** All exceptions thrown from controllers and services are caught by `GlobalExceptionHandler` (`@RestControllerAdvice`) and mapped to a consistent `ApiResponse` error envelope.

### Rule 22.2: Exception-to-HTTP status mapping

**MUST.** `GlobalExceptionHandler` maps exceptions to HTTP status codes as follows:

| Exception | HTTP Status | Error Key | Example Message |
|-----------|------------|-----------|-----------------|
| `MethodArgumentNotValidException` | 400 | Field name from `FieldError` | `"Password must be between 8 and 128 characters"` |
| `IllegalArgumentException` | 400 | `"error"` | `"Username is already taken"` |
| `BusinessException` | 400 | `"error"` | `"Current password is incorrect"` |
| `BadCredentialsException` | 401 | `"error"` | `"Invalid username or password"` |
| `AccessDeniedException` | 403 | `"error"` | `"Access denied"` |
| `ResourceNotFoundException` | 404 | `"error"` | `"User not found with id: abc-123"` |
| `DuplicateResourceException` | 409 | `"error"` | `"Role already exists with code: ROLE_ADMIN"` |
| `IllegalStateException` | 409 | `"error"` | `"Cannot modify system role"` |
| `RuntimeException` | 400 | `"error"` | Exception message |
| `Exception` (catch-all) | 500 | `"error"` | `"An unexpected error occurred"` |

### Rule 22.3: Error response structure

**MUST.** All error responses follow this JSON structure:

```json
{
  "status": {
    "code": 400,
    "message": "Validation failed",
    "errors": {
      "username": "Username must be between 3 and 50 characters",
      "password": "Password must contain at least one uppercase letter"
    }
  },
  "data": null,
  "meta": {
    "serverDateTime": "2026-03-27 10:30:45",
    "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "sessionId": "sess-xyz"
  }
}
```

Key rules:
- `status.errors` is a `Map<String, String>` — field name to error message for validation errors, or `"error"` key for single errors
- `data` is always `null` on errors
- `meta` is always present (populated by `ApiResponse` constructor from MDC)

### Rule 22.4: Never leak internal details in error messages

**MUST.** The catch-all `Exception` handler must return a generic message. Never expose stack traces, SQL errors, class names, or internal paths to the client.

```java
// CORRECT — generic message, details logged server-side
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<?>> handleGenericException(Exception ex) {
    log.error("Unhandled exception", ex);  // Full stack trace in server logs
    Map<String, String> errors = Map.of("error", "An unexpected error occurred");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(500, "Internal server error", errors));
}

// WRONG — leaks SQL and class names
return ResponseEntity.status(500)
        .body(ApiResponse.error(500, ex.getMessage(), errors));
```

> **Rationale:** Error messages in API responses are visible to attackers. Internal details (table names, query structure, class paths) help them map the system. Log full details server-side; return generic messages to clients.

### Rule 22.5: Validation errors — one entry per invalid field

**MUST.** When `@Valid` fails, `GlobalExceptionHandler` must extract each `FieldError` and return a map with field names as keys:

```java
// CORRECT — GlobalExceptionHandler
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiResponse<?>> handleValidationException(MethodArgumentNotValidException ex) {
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult().getFieldErrors()
            .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
    return ResponseEntity.badRequest()
            .body(ApiResponse.error(400, "Validation failed", errors));
}
```

This allows the client to highlight specific form fields, rather than showing a single generic error.
