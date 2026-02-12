# Ron AI Agent

[English](README.md) | [简体中文](README.zh-CN.md)

## Introduction

Ron AI Agent is a Spring Boot-based intelligent agent system that integrates with Alibaba's DashScope AI services. The project implements a multi-agent architecture with ReAct (Reasoning and Acting) patterns and includes MCP (Model Context Protocol) server capabilities for image search functionality.

## Features

- **Multi-Agent Architecture**: Hierarchical agent system with BaseAgent, ReActAgent, and ToolCallAgent
- **ReAct Pattern**: Implements think-act cycles for advanced reasoning
- **Tool Integration**: Extensive tool ecosystem including file operations, web search, and PDF generation
- **RAG Support**: Retrieval-Augmented Generation with vector database integration
- **MCP Server**: Model Context Protocol server for image search
- **CORS Configuration**: Environment-aware CORS settings for development and production

## Tech Stack

- **Java 21** with Spring Boot 3.5.5
- **Spring AI Alibaba** for DashScope integration
- **LangChain4j** for additional AI capabilities
- **PostgreSQL** with PgVector extension for vector storage
- **Maven** for dependency management

## Quick Start

### Prerequisites

- Java 21 or higher
- Maven 3.6+
- PostgreSQL database with PgVector extension
- DashScope API key

### Configuration

1. Clone the repository:
```bash
git clone https://github.com/your-username/ron-ai-agent.git
cd ron-ai-agent
```

2. Set up environment variables:

**Development Environment:**
```bash
# Copy example configuration
cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml

# Set environment variables
export DASHSCOPE_API_KEY=your-api-key
export DATABASE_URL=jdbc:postgresql://localhost:5432/ron_ai_agent
export DATABASE_USERNAME=your-username
export DATABASE_PASSWORD=your-password
export SEARCH_API_KEY=your-search-api-key
```

**Production Environment:**
```bash
export APP_ADMIN_TOKEN=your-secure-token
export APP_CORS_ALLOWED_ORIGINS=https://your-frontend.com
export DASHSCOPE_API_KEY=your-api-key
export DATABASE_URL=your-database-url
export DATABASE_USERNAME=your-db-user
export DATABASE_PASSWORD=your-db-password
export SEARCH_API_KEY=your-search-api-key
```

### Build and Run

```bash
# Build the project
mvn clean compile

# Run tests
mvn test

# Run with development profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run with production profile
mvn spring-boot:run -Dspring-boot.run.profiles=prod

# Package the application
mvn clean package
```

The application will start on `http://localhost:8081/api`

## Architecture

### Agent System

- **BaseAgent**: Abstract base class managing agent state and lifecycle
- **ReActAgent**: Extends BaseAgent with think-act cycles
- **ToolCallAgent**: Adds tool-calling capabilities with Spring AI integration
- **RonManus**: Main AI agent with DashScope chat model

### Tools

Available tools:
- FileOperationTool: File system operations
- WebSearchTool: Web search functionality
- WebScrapingTool: Web content extraction
- PDFGenerationTool: PDF document creation
- ResourceDownloadTool: File download capabilities
- TerminateTool: Agent termination control

### RAG System

- Vector store configuration using DashScope embeddings
- PostgreSQL with PgVector for vector storage
- Query rewriting and enhancement
- Custom RAG advisors

## API Documentation

Once the application is running, access the API documentation:

- **Swagger UI**: http://localhost:8081/api/swagger-ui.html
- **Knife4j**: http://localhost:8081/api/doc.html
- **OpenAPI Docs**: http://localhost:8081/api/v3/api-docs

## Configuration Profiles

### Development (dev)
- CORS: Allows all origins (`*`)
- Logging: DEBUG level
- Admin Token: `dev-token-123` (default)

### Production (prod)
- CORS: Configured via `APP_CORS_ALLOWED_ORIGINS` environment variable
- Logging: INFO level
- All sensitive data must be set via environment variables

## Environment Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `DASHSCOPE_API_KEY` | Alibaba DashScope API key | Yes | - |
| `DATABASE_URL` | PostgreSQL JDBC URL | Yes | - |
| `DATABASE_USERNAME` | Database username | Yes | - |
| `DATABASE_PASSWORD` | Database password | Yes | - |
| `SEARCH_API_KEY` | Search service API key | Yes | - |
| `APP_ADMIN_TOKEN` | Admin authentication token | Yes (prod) | `dev-token-123` (dev) |
| `APP_CORS_ALLOWED_ORIGINS` | Allowed CORS origins | Yes (prod) | `*` (dev) |

## MCP Server

The project includes a separate MCP server module for image search:

```bash
cd ron-image-search-mcp-server
mvn spring-boot:run
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contact

- Project Link: [https://github.com/your-username/ron-ai-agent](https://github.com/your-username/ron-ai-agent)

## Acknowledgments

- [Spring AI](https://spring.io/projects/spring-ai)
- [Alibaba DashScope](https://dashscope.aliyun.com/)
- [LangChain4j](https://docs.langchain4j.dev/)
