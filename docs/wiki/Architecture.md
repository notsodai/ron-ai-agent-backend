# Architecture

## System Overview

```
                    ┌─────────────────────────────────────┐
                    │           REST API Layer             │
                    │  AIController (SSE + Sync endpoints) │
                    └──────────────┬──────────────────────┘
                                   │
                    ┌──────────────▼──────────────────────┐
                    │      Request Processing Pipeline     │
                    │  Rate Limit → Dedup → Cache → Exec   │
                    └──────────────┬──────────────────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                     │
    ┌─────────▼─────────┐  ┌──────▼──────┐  ┌──────────▼──────────┐
    │     BookApp       │  │  RonManus   │  │  Multi-Agent System  │
    │  (Simple Chat)    │  │  (ReAct)    │  │  (TaskCoordinator)   │
    └─────────┬─────────┘  └──────┬──────┘  └──────────┬──────────┘
              │                    │                     │
              │           ┌────────▼────────┐    ┌──────▼──────┐
              │           │  Tool System     │    │ Specialized │
              │           │  (6+ tools)      │    │   Agents    │
              │           └─────────────────┘    └─────────────┘
              │
    ┌─────────▼──────────────────────────────────────────────┐
    │                    Infrastructure Layer                  │
    │  Memory (InMemory/JDBC) │ Context Manager │ Persistence │
    └─────────────────────────────────────────────────────────┘
```

## Module Structure

<!-- AUTO-GENERATED:START -->
```
com.ron.ronaiagent/
├── agent/                          # Agent framework
│   ├── BaseAgent.java              # Abstract base: lifecycle, state machine, context compaction
│   ├── ReActAgent.java             # think/act cycle
│   ├── ToolCallAgent.java          # Tool calling with retry
│   ├── RonManus.java               # Main agent implementation
│   ├── AgentState.java             # State enum: IDLE, RUNNING, FINISHED, ERROR
│   ├── ToolExecutionRetryPolicy.java  # Exponential backoff retry
│   ├── ContextWindowManager.java   # Token estimation and compaction
│   ├── AgentExecutionListener.java # Lifecycle event interface
│   ├── JdbcAgentExecutionListener.java
│   ├── communication/              # Inter-agent messaging
│   │   ├── Message.java
│   │   ├── MessageBus.java
│   │   └── InMemoryMessageBus.java
│   ├── coordinator/                # Multi-agent coordination
│   │   ├── TaskCoordinator.java
│   │   ├── TaskCoordinatorImpl.java
│   │   ├── AgentManager.java
│   │   ├── AgentManagerImpl.java
│   │   ├── CollaborationPattern.java
│   │   └── CoordinationResult.java
│   └── specialized/                # Domain-specific agents
│       ├── FileProcessingAgent.java
│       ├── SearchAgent.java
│       ├── AnalysisAgent.java
│       └── CoordinatorAgent.java
├── app/                            # Application-level services
│   └── BookApp.java                # ChatClient wrapper with memory
├── chat/
│   ├── advisor/                    # Chat advisors
│   ├── memory/                     # Chat memory repositories
│   │   ├── BookChatMemory.java     # ChatMemory wrapper
│   │   ├── JdbcChatMemoryRepository.java  # Database-backed
│   │   └── MemoryConfig.java       # Profile-based switching
│   ├── rag/                        # RAG with PgVector
│   └── tools/                      # Tool implementations
│       ├── ToolResult.java         # Standardized response record
│       ├── ToolRegistration.java   # Bean registration
│       ├── ToolSecurityUtils.java  # Path traversal prevention
│       ├── FileOperationTool.java
│       ├── WebSearchTool.java
│       ├── WebScrapingTool.java
│       ├── PDFGenerationTool.java
│       ├── ResourceDownloadTool.java
│       ├── ImageSearchTool.java
│       └── TerminateTool.java
├── config/                         # Spring configuration
│   ├── MultiAgentConfig.java
│   ├── PersistenceConfig.java
│   ├── CorsConfig.java
│   ├── WebMvcConfig.java
│   └── AdminTokenInterceptor.java
├── constant/                       # Constants
├── controller/                     # REST controllers
│   └── AIController.java
├── core/                           # Request pipeline
│   ├── UnifiedRequestProcessor.java
│   ├── CacheManager.java
│   ├── RequestDeduplicationManager.java
│   └── RequestRateLimitManager.java
├── monitoring/                     # Observability
├── persistence/                    # State persistence
│   ├── AgentExecutionRecord.java
│   └── AgentExecutionRepository.java
└── RonAiAgentApplication.java      # Entry point
```
<!-- AUTO-GENERATED:END -->

## Key Design Decisions

1. **Tool execution is manually controlled**: `ToolCallAgent` sets `withInternalToolExecutionEnabled(false)` on DashScopeChatOptions to manage conversation history and retry logic
2. **New agent instance per request**: RonManus is created fresh for each request in AIController
3. **Profile-based infrastructure**: Memory (InMemory/JDBC) and persistence (disabled/JDBC) switch based on Spring profiles
4. **Immutability in ToolResult**: Uses Java records with `List.copyOf()` for thread safety
5. **Context compaction**: Automatic when estimated tokens exceed 80% of the configured limit (default 4000)

## Database Schema

<!-- AUTO-GENERATED:START -->
Defined in `src/main/resources/schema.sql`:

| Table | Purpose |
|-------|---------|
| `chat_memory` | Persistent conversation history (prod profile) |
| `agent_execution` | Agent run state tracking (prod profile) |

### chat_memory
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| conversation_id | VARCHAR(255) | Conversation identifier |
| message_type | VARCHAR(50) | USER, ASSISTANT, SYSTEM |
| content | TEXT | Message content |
| created_at | TIMESTAMP | Creation time |
| metadata | TEXT | Optional metadata |

### agent_execution
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| agent_name | VARCHAR(100) | Agent identifier |
| prompt | TEXT | Original user prompt |
| status | VARCHAR(20) | RUNNING, FINISHED, ERROR |
| current_step | INT | Steps completed |
| max_steps | INT | Step limit |
| result | TEXT | Final result |
| start_time | TIMESTAMP | Execution start |
| end_time | TIMESTAMP | Execution end |
| duration_ms | BIGINT | Duration |
| error_message | TEXT | Error details |
<!-- AUTO-GENERATED:END -->
