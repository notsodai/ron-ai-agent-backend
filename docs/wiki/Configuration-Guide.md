# Configuration Guide

## Spring Profiles

<!-- AUTO-GENERATED:START -->
| Profile | Memory | Persistence | CORS | Logging | Admin Token |
|---------|--------|-------------|------|---------|-------------|
| dev | InMemory | Disabled | `*` | DEBUG | `dev-token-123` |
| local | InMemory | Disabled | `*` | DEBUG | `dev-token-123` |
| test | InMemory | Disabled | `*` | DEBUG | `test-admin-token` |
| prod | JDBC | JDBC | Env var | INFO | Env var |
<!-- AUTO-GENERATED:END -->

## Environment Variables

<!-- AUTO-GENERATED:START -->
| Variable | Required | Profile | Description | Example |
|----------|----------|---------|-------------|---------|
| `DASHSCOPE_API_KEY` | Yes | All | Alibaba DashScope LLM API key | `sk-xxxxxxxx` |
| `DATABASE_URL` | Yes (prod) | prod | PostgreSQL JDBC URL | `jdbc:postgresql://host:5432/db` |
| `DATABASE_USERNAME` | Yes (prod) | prod | PostgreSQL user | `ron_agent` |
| `DATABASE_PASSWORD` | Yes (prod) | prod | PostgreSQL password | `********` |
| `SEARCH_API_KEY` | Yes | All | SearchAPI.io key for web search | `xxxxxxxx` |
| `APP_ADMIN_TOKEN` | Yes (prod) | prod | Admin auth token | `secure-random-token` |
| `APP_CORS_ALLOWED_ORIGINS` | Yes (prod) | prod | Allowed CORS origins | `https://app.example.com` |
| `PEXELS_API_KEY` | No | All | Pexels API key for image search | `xxxxxxxx` |
<!-- AUTO-GENERATED:END -->

## Application Properties

### Server

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8081` | HTTP port |
| `server.servlet.context-path` | `/api` | Context path |

### Spring AI DashScope

| Property | Default | Description |
|----------|---------|-------------|
| `spring.ai.dashscope.api-key` | `${DASHSCOPE_API_KEY}` | DashScope API key |
| `spring.ai.dashscope.chat.options.model` | — | Chat model name |

### Vector Store (PgVector)

| Property | Default | Description |
|----------|---------|-------------|
| `spring.ai.vectorstore.pgvector.index-type` | `HNSW` | Index type |
| `spring.ai.vectorstore.pgvector.dimensions` | `1536` | Embedding dimensions |
| `spring.ai.vectorstore.pgvector.distance-type` | `COSINE_DISTANCE` | Distance metric |

### MCP Client

| Property | Default | Description |
|----------|---------|-------------|
| `spring.ai.mcp.client.config-location` | `classpath:mcp-servers.json` | MCP server config |

## Database Setup (Production)

### PostgreSQL with PgVector

```sql
CREATE EXTENSION IF NOT EXISTS pgvector;
```

Tables are auto-created from `schema.sql` using `CREATE TABLE IF NOT EXISTS`.

### H2 (Tests)

Tests use H2 in-memory database — no PostgreSQL needed for `mvn test`.

## Memory Configuration

`MemoryConfig` provides profile-based switching:

```java
@Profile({"dev", "local", "test"})
@Bean ChatMemoryRepository inMemoryRepository();  // InMemoryChatMemoryRepository

@Profile("prod")
@Bean ChatMemoryRepository jdbcRepository(DataSource ds);  // JdbcChatMemoryRepository
```

### JdbcChatMemoryRepository

- Persists messages to `chat_memory` table
- Configurable `maxMessagesPerConversation` (default: 100)
- Automatic eviction of oldest messages when limit exceeded

## MCP Server

The image search MCP server is configured in `mcp-servers.json`:

```json
{
  "mcpServers": {
    "ron-image-search": {
      "command": "java",
      "args": ["-jar", "ron-image-search-mcp-server.jar"]
    }
  }
}
```

Run separately:
```bash
cd ron-image-search-mcp-server && mvn spring-boot:run
```
