# Ron AI Agent

[English](README.md) | [简体中文](README.zh-CN.md)

## 项目简介

Ron AI Agent 是一个基于 Spring Boot 的智能代理系统，集成了阿里云的 DashScope AI 服务。该项目实现了基于 ReAct（推理与行动）模式的多代理架构，并包含 MCP（模型上下文协议）服务器功能用于图片搜索。

## 功能特性

- **多代理架构**：包含 BaseAgent、ReActAgent 和 ToolCallAgent 的分层代理系统
- **ReAct 模式**：实现思考-行动循环的高级推理能力
- **工具集成**：丰富的工具生态系统，包括文件操作、网络搜索和 PDF 生成
- **RAG 支持**：检索增强生成与向量数据库集成
- **MCP 服务器**：用于图片搜索的模型上下文协议服务器
- **CORS 配置**：支持开发和生产环境的 CORS 设置

## 技术栈

- **Java 21** with Spring Boot 3.5.5
- **Spring AI Alibaba** 用于 DashScope 集成
- **LangChain4j** 提供额外的 AI 能力
- **PostgreSQL** with PgVector 扩展用于向量存储
- **Maven** 依赖管理

## 快速开始

### 前置要求

- Java 21 或更高版本
- Maven 3.6+
- 安装了 PgVector 扩展的 PostgreSQL 数据库
- DashScope API 密钥

### 配置

1. 克隆仓库：
```bash
git clone https://github.com/your-username/ron-ai-agent.git
cd ron-ai-agent
```

2. 设置环境变量：

**开发环境：**
```bash
# 复制示例配置
cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml

# 设置环境变量
export DASHSCOPE_API_KEY=your-api-key
export DATABASE_URL=jdbc:postgresql://localhost:5432/ron_ai_agent
export DATABASE_USERNAME=your-username
export DATABASE_PASSWORD=your-password
export SEARCH_API_KEY=your-search-api-key
```

**生产环境：**
```bash
export APP_ADMIN_TOKEN=your-secure-token
export APP_CORS_ALLOWED_ORIGINS=https://your-frontend.com
export DASHSCOPE_API_KEY=your-api-key
export DATABASE_URL=your-database-url
export DATABASE_USERNAME=your-db-user
export DATABASE_PASSWORD=your-db-password
export SEARCH_API_KEY=your-search-api-key
```

### 构建和运行

```bash
# 构建项目
mvn clean compile

# 运行测试
mvn test

# 使用开发环境配置运行
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 使用生产环境配置运行
mvn spring-boot:run -Dspring-boot.run.profiles=prod

# 打包应用
mvn clean package
```

应用将在 `http://localhost:8081/api` 启动

## 架构设计

### 代理系统

- **BaseAgent**：管理代理状态和生命周期的抽象基类
- **ReActAgent**：扩展思考-行动循环
- **ToolCallAgent**：通过 Spring AI 集成添加工具调用能力
- **RonManus**：使用 DashScope 聊天模型的主要 AI 代理

### 工具列表

可用工具：
- FileOperationTool：文件系统操作
- WebSearchTool：网络搜索功能
- WebScrapingTool：网页内容提取
- PDFGenerationTool：PDF 文档创建
- ResourceDownloadTool：文件下载功能
- TerminateTool：代理终止控制

### RAG 系统

- 使用 DashScope 嵌入的向量存储配置
- 支持 PgVector 的 PostgreSQL 向量存储
- 查询重写和增强
- 自定义 RAG 顾问

## API 文档

应用启动后，访问 API 文档：

- **Swagger UI**: http://localhost:8081/api/swagger-ui.html
- **Knife4j**: http://localhost:8081/api/doc.html
- **OpenAPI Docs**: http://localhost:8081/api/v3/api-docs

## 配置文件说明

### 开发环境 (dev)
- CORS：允许所有源 (`*`)
- 日志级别：DEBUG
- 管理员令牌：`dev-token-123`（默认值）

### 生产环境 (prod)
- CORS：通过 `APP_CORS_ALLOWED_ORIGINS` 环境变量配置
- 日志级别：INFO
- 所有敏感数据必须通过环境变量设置

## 环境变量

| 变量名 | 描述 | 是否必需 | 默认值 |
|--------|------|----------|--------|
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API 密钥 | 是 | - |
| `DATABASE_URL` | PostgreSQL JDBC URL | 是 | - |
| `DATABASE_USERNAME` | 数据库用户名 | 是 | - |
| `DATABASE_PASSWORD` | 数据库密码 | 是 | - |
| `SEARCH_API_KEY` | 搜索服务 API 密钥 | 是 | - |
| `APP_ADMIN_TOKEN` | 管理员认证令牌 | 是（生产） | `dev-token-123`（开发） |
| `APP_CORS_ALLOWED_ORIGINS` | 允许的 CORS 源 | 是（生产） | `*`（开发） |

## MCP 服务器

项目包含独立的 MCP 服务器模块用于图片搜索：

```bash
cd ron-image-search-mcp-server
mvn spring-boot:run
```

## 贡献指南

欢迎贡献！请随时提交 Pull Request。

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m '添加某个功能'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 开源协议

本项目采用 MIT 协议 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 联系方式

- 项目链接：[https://github.com/your-username/ron-ai-agent](https://github.com/your-username/ron-ai-agent)

## 致谢

- [Spring AI](https://spring.io/projects/spring-ai)
- [阿里云 DashScope](https://dashscope.aliyun.com/)
- [LangChain4j](https://docs.langchain4j.dev/)
