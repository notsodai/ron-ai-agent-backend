# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.5.5 / Java 21 multi-agent AI system using Alibaba DashScope (via Spring AI Alibaba 1.0.0-M2.1, Spring AI 1.0.2).

## Development Commands

```bash
mvn clean compile                                        # Build
mvn test                                                 # Run all tests (H2, no PostgreSQL needed)
mvn test -Dtest=RonManusTest                             # Single test class
mvn test -Dtest=RonManusTest#testMethod                  # Single test method
mvn spring-boot:run -Dspring-boot.run.profiles=dev       # Run (requires PostgreSQL + API keys)
mvn clean package                                        # Package JAR
cd ron-image-search-mcp-server && mvn spring-boot:run    # MCP server
```

## Architecture Principles

- **ToolCallAgent disables internal tool execution** (`withInternalToolExecutionEnabled(false)`) to manually control conversation history and retry logic
- **New agent instance per request** — RonManus is created fresh in AIController, not reused
- **Profile-based infrastructure** — Memory (InMemory/JDBC) and persistence (off/JDBC) switch on Spring profiles (dev/local/test → InMemory, prod → JDBC)
- **All tools return ToolResult JSON** — standardized `status/summary/errorMessage/nextActions/artifacts` record
- **Context compaction** — automatic when estimated tokens exceed 80% of limit (default 4000)
- **Tool execution retry** — exponential backoff via `ToolExecutionRetryPolicy` (default: 2 retries, 500ms initial)

## Key Conventions

- **Lombok** throughout (`@Data`, `@Slf4j`, `@Builder`)
- **Spring AI `ToolCallback`** for all tools, registered via `ToolCallbacks.from()` in `ToolRegistration`
- **English for new code** — existing code has mixed Chinese/English comments
- **Tests use H2** — no external services needed; mock DashScope where required
- **Conventional commits** — `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`
- **Never commit secrets** — `application-local.yml` is gitignored

## Documentation Index

Detailed project documentation is in `docs/wiki/`:

- [Architecture](docs/wiki/Architecture.md) — System overview, module structure, database schema, design decisions
- [Agent System](docs/wiki/Agent-System.md) — Agent hierarchy, lifecycle state machine, think/act cycle, retry, context compaction
- [Tool System](docs/wiki/Tool-System.md) — ToolResult standard, all 7 tools, registration, security utilities
- [Multi-Agent Coordination](docs/wiki/Multi-Agent-Coordination.md) — Collaboration patterns (master-worker/chain/parallel), agent registry, message bus
- [API Reference](docs/wiki/API-Reference.md) — REST endpoints, SSE streaming, request processing pipeline
- [Configuration Guide](docs/wiki/Configuration-Guide.md) — Profiles, environment variables, database setup, memory configuration
- [Development Guide](docs/wiki/Development-Guide.md) — Setup, testing conventions, coding standards, contributing workflow
- [Deployment Guide](docs/wiki/Deployment-Guide.md) — Production deployment, health monitoring, rollback procedures
