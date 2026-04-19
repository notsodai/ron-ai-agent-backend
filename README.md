# Ron AI Agent

[English](README.md) | [简体中文](README.zh-CN.md)

## Introduction

Ron AI Agent is a Spring Boot 3.5.5 / Java 21 multi-agent AI system using Alibaba DashScope (via Spring AI Alibaba). It implements ReAct (Reason+Act) agent loops, tool-calling with retry, RAG with PgVector, context window management, and MCP server/client integration.

## Features

- **Multi-Agent Architecture**: Hierarchical agent system (BaseAgent → ReActAgent → ToolCallAgent → RonManus)
- **ReAct Pattern**: Think-act cycles with structured error recovery and root cause extraction
- **Tool System**: 7 tools with standardized ToolResult JSON output, security validation, and retry with exponential backoff
- **Context Management**: Automatic token estimation and compaction when approaching limits
- **Memory Persistence**: Profile-based switching between InMemory (dev) and JDBC (prod) chat memory
- **State Persistence**: Agent execution tracking with JDBC-backed listener (prod)
- **Multi-Agent Coordination**: Master-worker, chain, and parallel collaboration patterns
- **Request Pipeline**: Rate limiting, deduplication, and caching via UnifiedRequestProcessor
- **RAG Support**: Retrieval-Augmented Generation with DashScope embeddings and PgVector
- **MCP Integration**: Model Context Protocol server for image search + client connection

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 | Runtime |
| Spring Boot | 3.5.5 | Application framework |
| Spring AI | 1.0.2 | AI model integration |
| Spring AI Alibaba | 1.0.0-M2.1 | DashScope integration |
| PostgreSQL + PgVector | — | Vector storage, production DB |
| H2 | 2.3.x | In-memory DB for tests |
| iText | — | PDF generation |
| Jsoup | — | Web scraping |
| Lombok | 1.18.36 | Boilerplate reduction |

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.6+
- PostgreSQL with PgVector extension (production only)
- DashScope API key

### Configuration

1. Clone the repository:
```bash
git clone https://github.com/your-username/ron-ai-agent.git
cd ron-ai-agent
```

2. Set up configuration:
```bash
cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml
```

3. Set environment variables:
```bash
export DASHSCOPE_API_KEY=your-api-key
export DATABASE_URL=jdbc:postgresql://localhost:5432/ron_ai_agent
export DATABASE_USERNAME=your-username
export DATABASE_PASSWORD=your-password
export SEARCH_API_KEY=your-search-api-key
```

### Build and Run

```bash
mvn clean compile                                              # Build
mvn test                                                       # Run tests (194 tests, no PostgreSQL needed)
mvn spring-boot:run -Dspring-boot.run.profiles=dev             # Run with dev profile
mvn clean package                                              # Package JAR
```

Application starts at `http://localhost:8081/api`

## Architecture

### Agent Hierarchy

```
BaseAgent (abstract)          — lifecycle, state machine (IDLE→RUNNING→FINISHED/ERROR), context compaction
  └─ ReActAgent (abstract)    — step() = think() + act()
       └─ ToolCallAgent        — tool calling with retry, error recovery
            └─ RonManus        — main agent: system prompt, DashScope model, all tools
```

### Specialized Agents

| Agent | ID | Purpose |
|-------|----|---------|
| FileProcessingAgent | file-processor | File read/write operations |
| SearchAgent | search-agent | Web search tasks |
| AnalysisAgent | analysis-agent | Data analysis |
| CoordinatorAgent | coordinator | Multi-agent task delegation |

### Tools

All tools return standardized `ToolResult` JSON with status, summary, nextActions, and artifacts.

| Tool | Description |
|------|-------------|
| FileOperationTool | File system read/write with path traversal protection |
| WebSearchTool | Baidu search via SearchAPI |
| WebScrapingTool | Web page content extraction |
| PDFGenerationTool | PDF generation with iText (CJK support) |
| ResourceDownloadTool | File download with URL validation |
| ImageSearchTool | Image search via Pexels API (MCP) |
| TerminateTool | Agent execution termination |

### Request Pipeline

```
Request → Rate Limit → Deduplication → Cache → Execute → Response
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/ai/book/chat/sync` | Synchronous chat (BookApp) |
| GET | `/ai/book/chat/stream` | Streaming chat (Flux SSE) |
| GET | `/ai/book/chat/stream/emitter` | Streaming chat (SseEmitter) |
| GET | `/ai/RonManus/chat/` | ReAct agent with tool-calling |
| GET | `/ai/stats` | System statistics |
| POST | `/ai/cache/clear` | Clear cache |
| POST | `/ai/rate-limit/reset/{clientId}` | Reset rate limit |

API docs: http://localhost:8081/api/swagger-ui.html

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `DASHSCOPE_API_KEY` | Yes | Alibaba DashScope API key |
| `DATABASE_URL` | Yes (prod) | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | Yes (prod) | Database username |
| `DATABASE_PASSWORD` | Yes (prod) | Database password |
| `SEARCH_API_KEY` | Yes | Web search API key |
| `APP_ADMIN_TOKEN` | Yes (prod) | Admin authentication token |
| `APP_CORS_ALLOWED_ORIGINS` | Yes (prod) | Allowed CORS origins |
| `PEXELS_API_KEY` | No | Image search API key |

## Configuration Profiles

| Profile | Memory | Persistence | CORS | Logging |
|---------|--------|-------------|------|---------|
| dev | InMemory | Disabled | `*` | DEBUG |
| local | InMemory | Disabled | `*` | DEBUG |
| prod | JDBC | JDBC | Env var | INFO |

## Documentation

Full documentation is available in the [docs/wiki/](docs/wiki/) directory:

- [Architecture](docs/wiki/Architecture.md) — System architecture and module structure
- [Agent System](docs/wiki/Agent-System.md) — Agent hierarchy, lifecycle, and execution model
- [Tool System](docs/wiki/Tool-System.md) — ToolResult standard, available tools
- [Multi-Agent Coordination](docs/wiki/Multi-Agent-Coordination.md) — Collaboration patterns
- [API Reference](docs/wiki/API-Reference.md) — REST endpoints and request pipeline
- [Configuration Guide](docs/wiki/Configuration-Guide.md) — Profiles, env vars, database setup
- [Development Guide](docs/wiki/Development-Guide.md) — Setup, testing, contributing
- [Deployment Guide](docs/wiki/Deployment-Guide.md) — Production deployment and monitoring

## MCP Server

Separate module for image search via Pexels API:

```bash
cd ron-image-search-mcp-server
mvn spring-boot:run
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Write tests first (TDD)
4. Implement with minimum necessary code
5. Ensure `mvn test` passes
6. Commit with conventional commits (`feat:`, `fix:`, `refactor:`, etc.)
7. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Spring AI](https://spring.io/projects/spring-ai)
- [Alibaba DashScope](https://dashscope.aliyun.com/)
