# API Reference

Base URL: `http://localhost:8081/api`

## Authentication

All endpoints require the `X-Admin-Token` header matching the configured `APP_ADMIN_TOKEN` value.

---

## Chat Endpoints

### Sync Chat (BookApp)

```
GET /ai/book/chat/sync
```

Synchronous chat with BookApp (simple ChatClient with memory).

| Parameter | Location | Required | Description |
|-----------|----------|----------|-------------|
| message | query | Yes | User message |
| conversationId | query | Yes | Conversation ID for context |
| X-Client-ID | header | No | Client identifier for rate limiting |

**Response:** `200 OK` — Plain text reply

**Error Responses:**
- `400` — Blank message or conversationId
- `409` — Duplicate request detected
- `429` — Rate limit exceeded (includes `Retry-After` header)
- `500` — Internal error

---

### Stream Chat (Flux)

```
GET /ai/book/chat/stream
```

Streaming chat via Server-Sent Events (Flux).

| Parameter | Location | Required | Description |
|-----------|----------|----------|-------------|
| message | query | Yes | User message |
| conversationId | query | Yes | Conversation ID |
| X-Client-ID | header | No | Client identifier |

**Response:** `200 OK` — `text/event-stream`

```
data:{"id":"...","event":"message","data":"chunk text"}
```

---

### Stream Chat (SseEmitter)

```
GET /ai/book/chat/stream/emitter
```

Streaming chat via SseEmitter (3-minute timeout).

| Parameter | Location | Required | Description |
|-----------|----------|----------|-------------|
| message | query | Yes | User message |
| conversationId | query | Yes | Conversation ID |
| X-Client-ID | header | No | Client identifier |

**Response:** `200 OK` — SSE events, ending with `data:[DONE]`

---

### RonManus Agent Chat

```
GET /ai/RonManus/chat/
```

Full ReAct agent with tool-calling. Uses strict rate limiting.

| Parameter | Location | Required | Description |
|-----------|----------|----------|-------------|
| message | query | Yes | User prompt |
| X-Client-ID | header | No | Client identifier |

**Response:** `200 OK` — SSE events via SseEmitter

Each step outputs: `data:StepN: tool results`

---

## Admin Endpoints

### System Statistics

```
GET /ai/stats
```

Returns deduplication, rate limit, and cache statistics.

**Response:** `200 OK`
```json
{
  "deduplication": { "totalProcessed": 100, ... },
  "rateLimit": { "globalStats": { ... } },
  "cache": { "hitRate": 0.85, ... },
  "timestamp": "2026-04-19T12:00:00"
}
```

### Clear Cache

```
POST /ai/cache/clear
```

Clears all cached responses.

**Response:** `200 OK` — `"Cache cleared successfully"`

### Reset Rate Limit

```
POST /ai/rate-limit/reset/{clientId}
```

Resets rate limit counters for a specific client.

| Parameter | Location | Required | Description |
|-----------|----------|----------|-------------|
| clientId | path | Yes | Client identifier |

**Response:** `200 OK` — `"Rate limit counters reset for client: {clientId}"`

---

## Request Processing Pipeline

All requests in AIController flow through `UnifiedRequestProcessor`:

```
Request → Rate Limit → Deduplication → Cache → Execute → Response
              │              │             │
              ▼              ▼             ▼
         429 Reject    409 Reject    200 Cached
```

### Pipeline Stages

1. **Rate Limiting** (`RequestRateLimitManager`) — Per-client configurable strictness
2. **Deduplication** (`RequestDeduplicationManager`) — Prevents duplicate concurrent requests
3. **Caching** (`CacheManager`) — In-memory TTL cache

### Rate Limit Configurations

| Config | Strictness | Use Case |
|--------|-----------|----------|
| `defaultConfig()` | Standard | General use |
| `strictConfig()` | Strict | RonManus (tool-calling) |
| `lenientConfig()` | Lenient | Streaming endpoints |

## API Documentation

When running, access interactive docs at:
- **Swagger UI**: http://localhost:8081/api/swagger-ui.html
- **Knife4j**: http://localhost:8081/api/doc.html
- **OpenAPI JSON**: http://localhost:8081/api/v3/api-docs
