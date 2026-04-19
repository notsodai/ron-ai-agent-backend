# Deployment Guide

## Production Deployment

### Prerequisites

- Java 21 runtime
- PostgreSQL 14+ with PgVector extension
- All environment variables configured

### Build

```bash
mvn clean package -DskipTests
# Output: target/ron-ai-agent-*.jar
```

### Run

```bash
java -jar target/ron-ai-agent-*.jar --spring.profiles.active=prod
```

Or with environment variables:

```bash
export DASHSCOPE_API_KEY=sk-xxxxxxxx
export DATABASE_URL=jdbc:postgresql://db-host:5432/ron_ai_agent
export DATABASE_USERNAME=ron_agent
export DATABASE_PASSWORD=secure-password
export SEARCH_API_KEY=xxxxxxxx
export APP_ADMIN_TOKEN=secure-random-token
export APP_CORS_ALLOWED_ORIGINS=https://your-frontend.com
export PEXELS_API_KEY=xxxxxxxx

java -jar target/ron-ai-agent-*.jar --spring.profiles.active=prod
```

### Database Initialization

Tables are auto-created from `schema.sql` using `CREATE TABLE IF NOT EXISTS`. First startup will create:

- `chat_memory` — Conversation history persistence
- `agent_execution` — Agent run state tracking

Ensure PgVector extension is installed:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

## Configuration Checklist

<!-- AUTO-GENERATED:START -->
| Item | Config | Required |
|------|--------|----------|
| DashScope API Key | `DASHSCOPE_API_KEY` | Yes |
| Database URL | `DATABASE_URL` | Yes |
| Database User | `DATABASE_USERNAME` | Yes |
| Database Password | `DATABASE_PASSWORD` | Yes |
| Search API Key | `SEARCH_API_KEY` | Yes |
| Admin Token | `APP_ADMIN_TOKEN` | Yes |
| CORS Origins | `APP_CORS_ALLOWED_ORIGINS` | Yes |
| Pexels API Key | `PEXELS_API_KEY` | No |
<!-- AUTO-GENERATED:END -->

## Health Monitoring

### Health Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/ai/stats` | System statistics (dedup, rate limit, cache) |
| Swagger UI | `GET /api/swagger-ui.html` |

### Key Metrics

Monitor via `GET /api/ai/stats`:
- Cache hit rate
- Rate limit rejections
- Deduplication statistics
- Active request count

## Rollback

Each feature is a single commit. Rollback with:

```bash
git revert <commit-hash>
mvn clean package -DskipTests
# Redeploy the new JAR
```

All tables use `CREATE TABLE IF NOT EXISTS` — no destructive schema changes.

## Profile Differences

| Feature | Dev | Prod |
|---------|-----|------|
| Memory | InMemoryChatMemoryRepository | JdbcChatMemoryRepository |
| State Tracking | Disabled | JDBC-backed |
| CORS | `*` | Configured via env var |
| Logging | DEBUG | INFO |
| Admin Token | `dev-token-123` | Env var required |

## MCP Server

Run the image search MCP server separately:

```bash
cd ron-image-search-mcp-server
mvn spring-boot:run
```

Supports stdio and SSE transports.
