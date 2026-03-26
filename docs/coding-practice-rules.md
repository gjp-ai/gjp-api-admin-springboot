# Coding Practice Rules

> Coding standards and conventions for the GJP Admin API Spring Boot project.
> All contributors must follow these rules to ensure consistency across the codebase.

---

## 1. Logging

### Rule 1.1: All Spring-managed classes must have `@Slf4j`

Every `@Service`, `@RestController`, `@Component`, and `@Configuration` class must be annotated with `@Slf4j` from Lombok for structured logging.

```java
@Slf4j       // Always present
@Service
@RequiredArgsConstructor
public class UserService { ... }
```

### Rule 1.2: Logging level guidelines

| Level | Usage |
|-------|-------|
| `log.debug()` | Internal flow tracing (token validation, cache hits, method entry/exit) |
| `log.info()` | Significant state changes (user created, role deleted, password changed) |
| `log.warn()` | Recoverable issues (account locked, rate limit hit, deprecated usage) |
| `log.error()` | Unrecoverable failures (unhandled exceptions, infrastructure errors) |

### Rule 1.3: Never log sensitive data

Never log passwords, tokens (access or refresh), secret keys, or full request bodies containing credentials.

```java
// WRONG
log.info("User logged in with token: {}", accessToken);

// CORRECT
log.info("User '{}' logged in successfully", username);
```

---

## 2. Imports

### Rule 2.1: No wildcard imports

Always use explicit imports. No `import java.util.*` or `import org.springframework.web.bind.annotation.*`.

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

Do not import classes from the same package — Java resolves them automatically.

### Rule 2.3: Import ordering

Follow this order (alphabetical within each group), separated by blank lines:

1. `jakarta.*`
2. `lombok.*`
3. `org.ganjp.*` (project imports)
4. `org.springframework.*`
5. `java.*`

---

## 3. Entity Classes (JPA)

### Rule 3.1: Use `@Getter` / `@Setter` instead of `@Data` on entities

JPA entities must not use `@Data` because it generates `equals()`, `hashCode()`, and `toString()` based on all fields, which causes issues with lazy-loaded relationships and Hibernate proxies.

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

### Rule 3.2: Always exclude bidirectional relationships from `@ToString`

Use `@ToString(exclude = {...})` to prevent `LazyInitializationException` and infinite recursion.

### Rule 3.3: Implement `equals()` and `hashCode()` based on ID only

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

### Rule 3.4: DTOs use `@Data`

DTOs (Request/Response classes) should use `@Data` since they are simple POJOs without JPA proxying concerns.

```java
// CORRECT: DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse { ... }
```

---

## 4. Exception Handling

### Rule 4.1: Use project-specific exception types

| Exception | HTTP Status | Usage |
|-----------|------------|-------|
| `IllegalArgumentException` | 400 | Invalid input, business rule violation on input |
| `BusinessException` | 400 | Business logic violation (e.g., current password mismatch) |
| `BadCredentialsException` | 401 | Authentication failure |
| `ResourceNotFoundException` | 404 | Entity not found by ID/code |
| `DuplicateResourceException` | 409 | Unique constraint violation |
| `IllegalStateException` | 409 | Operation not allowed in current state |

### Rule 4.2: Never use generic `RuntimeException`

Always use a specific exception type from the table above.

### Rule 4.3: Use `BusinessException` for user-facing business rule violations in profile/self-service operations

Use `IllegalArgumentException` for admin-facing validation in management services.

### Rule 4.4: Use `ResourceNotFoundException` consistently for entity lookups

```java
// CORRECT
User user = userRepository.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

// WRONG
User user = userRepository.findById(id)
    .orElseThrow(() -> new BusinessException("User not found: " + id));
```

---

## 5. Service Layer

### Rule 5.1: Constructor injection via `@RequiredArgsConstructor`

All service classes must use Lombok's `@RequiredArgsConstructor` with `private final` fields.

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;  // Injected via constructor
}
```

### Rule 5.2: `@Transactional` on all data-modifying methods

Every service method that creates, updates, or deletes data must be annotated with `@Transactional`.
Read-only methods should use `@Transactional(readOnly = true)` when containing multiple queries.

### Rule 5.3: Consistent audit parameter naming

Service methods that need to record the acting user must use `userId` (not `username`) as the parameter name, since the value comes from the JWT token's `userId` claim.

```java
// CORRECT
public void deleteRole(String id, String userId) { ... }

// WRONG
public void deleteRole(String id, String username) { ... }
```

---

## 6. Controller Layer

### Rule 6.1: Every endpoint must have `@PreAuthorize`

No controller method should lack an authorization annotation:

```java
@PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_SUPER_ADMIN')")  // Admin ops
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")                                 // Destructive ops
@PreAuthorize("isAuthenticated()")                                                 // Self-service ops
```

### Rule 6.2: Consistent response pattern

All endpoints must return `ResponseEntity<ApiResponse<T>>` using the `ApiResponse` envelope:

```java
return ResponseEntity.ok(ApiResponse.success(data, "Message"));
return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, "Message"));
```

### Rule 6.3: Extract `userId` from JWT consistently

Always use `jwtUtils.extractUserIdFromToken(request)` — never duplicate extraction logic.

---

## 7. Javadoc

### Rule 7.1: All public methods must have Javadoc

Every public method in controllers, services, and utility classes must have a Javadoc comment with at minimum a one-line description.

```java
/**
 * Get all roles sorted by sort order and name.
 *
 * @return list of all roles
 */
public List<RoleResponse> getAllRoles() { ... }
```

### Rule 7.2: Controller Javadoc should describe the endpoint

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

Standard audit fields (`createdAt`, `updatedAt`) do not need Javadoc. Domain-specific fields should.

---

## 8. Validation

### Rule 8.1: Unified password policy across all DTOs

All DTOs accepting passwords must use the same constraints:

```java
@Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^+=])(?=\\S+$).+$",
         message = "Password must contain at least one uppercase, one lowercase, one digit, one special character, and no whitespace")
```

### Rule 8.2: Use Bean Validation on DTOs, not in service methods

Prefer declarative validation on DTOs; service methods should focus on business rules (uniqueness, authorization).

---

## 9. Naming Conventions

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

1. Static fields / constants
2. Instance fields (injected dependencies)
3. Public methods (CRUD order: create, read, update, delete)
4. Private helper methods

### Rule 10.3: Blank lines

- One blank line between methods
- One blank line between logical sections within a method
- No trailing blank lines at end of file
