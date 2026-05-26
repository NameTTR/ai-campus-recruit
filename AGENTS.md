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

## AI Orchestration Rules

- Treat the primary Codex session as the caller, planner, reviewer, and release owner.
- Delegate implementation to a separate Codex worker when the user asks for AI-driven development.
- Give every worker a bounded write scope and a concrete verification command.
- The caller reviews worker changes, runs final tests, fixes integration bugs, commits, and pushes.
- Workers must not revert user edits or unrelated changes.

## Commit Style

- `docs: ...`
- `feat: ...`
- `fix: ...`
- `test: ...`
- `chore: ...`
