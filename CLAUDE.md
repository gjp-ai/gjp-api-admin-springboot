# CLAUDE.md — Project Instructions for Claude Code

## Project Overview

GJP Admin API — a Spring Boot 3.x REST API with JWT authentication, role-based authorization, async audit logging, and modular architecture.

## Coding Rules

Follow the rules in [docs/guide/AI_CODING_GUIDE.md](docs/guide/AI_CODING_GUIDE.md). For full rationale and examples, see [docs/guide/STYLE_GUIDE.md](docs/guide/STYLE_GUIDE.md).

## Build & Test Commands

```bash
# Compile
./mvnw compile

# Run tests
./mvnw test

# Run application (dev profile)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Package
./mvnw package -DskipTests
```

## Key Directories

- `src/main/java/org/ganjp/api/` — Main source code
- `src/main/resources/` — Configuration files (application*.yml)
- `scripts/database/mysql/` — SQL schema scripts
- `docs/guide/` — Style guide, AI coding guide
- `docs/design/` — Module design documentation (auth, etc.)

## Important Patterns

- All responses: `ResponseEntity<ApiResponse<T>>`
- All entities extend `BaseEntity` (audit fields + UUID)
- All exceptions handled by `GlobalExceptionHandler`
- Async audit via `@Async("auditTaskExecutor")`
- MDC context (requestId, userId) propagated automatically — don't repeat in log messages

## Git Conventions

- Commit messages: imperative mood, describe the "why"
- Don't commit `.env`, credentials, or secrets
