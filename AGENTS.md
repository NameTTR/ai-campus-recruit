# AI Development Rules

## Goal

Build an AI-powered campus recruitment platform with a runnable MVP first, then evolve it into a distributed microservice system.

## Engineering Rules

- Keep every service independently runnable.
- Prefer small, testable controllers and service classes.
- All public API responses use `ApiResponse<T>`.
- Never hardcode secret keys. Read AI credentials from environment variables.
- Add or update API documentation whenever an endpoint changes.
- Keep Docker and deployment docs compatible with China-accessible mirrors.

## Iteration Rules

1. Implement the smallest end-to-end flow first.
2. Verify with build or tests before adding infrastructure complexity.
3. When fixing a bug, reproduce it, patch it, and record the verification command.
4. When adding a service, include port, health endpoint, OpenAPI access path, and Docker Compose entry.

## Commit Style

- `docs: ...`
- `feat: ...`
- `fix: ...`
- `test: ...`
- `chore: ...`

