# Ron AI Agent Wiki

Welcome to the Ron AI Agent project wiki.

## Overview

Ron AI Agent is a Spring Boot 3.5.5 / Java 21 multi-agent AI system using Alibaba DashScope (via Spring AI Alibaba). It implements ReAct (Reason+Act) agent loops, tool-calling, RAG with PgVector, and MCP server/client integration.

## Documentation Index

| Document | Description |
|----------|-------------|
| [Architecture](Architecture.md) | System architecture, modules, and data flow |
| [Agent System](Agent-System.md) | Agent hierarchy, lifecycle, state machine, and execution model |
| [Tool System](Tool-System.md) | ToolResult standard, available tools, and registration |
| [Multi-Agent Coordination](Multi-Agent-Coordination.md) | Collaboration patterns, agent registry, and message bus |
| [API Reference](API-Reference.md) | REST endpoints, SSE streaming, request pipeline |
| [Configuration Guide](Configuration-Guide.md) | Profiles, environment variables, database setup |
| [Development Guide](Development-Guide.md) | Setup, testing, coding standards, contributing |
| [Deployment Guide](Deployment-Guide.md) | Production deployment, health checks, rollback |

## Quick Links

- **Source**: `src/main/java/com/ron/ronaiagent/`
- **Tests**: `src/test/java/com/ron/ronaiagent/`
- **Config**: `src/main/resources/application*.yml`
- **Schema**: `src/main/resources/schema.sql`
- **API Docs**: http://localhost:8081/api/swagger-ui.html
