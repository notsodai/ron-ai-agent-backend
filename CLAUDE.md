# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot-based AI agent system called "ron-ai-agent" that integrates with Alibaba's DashScope AI services. The project implements a multi-agent architecture with ReAct (Reasoning and Acting) patterns and includes MCP (Model Context Protocol) server capabilities for image search functionality.

## Development Commands

### Build and Run
```bash
# Build the project
mvn clean compile

# Run tests
mvn test

# Run the application
mvn spring-boot:run

# Package the application
mvn clean package

# Run a specific test class
mvn test -Dtest=RonManusTest

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### MCP Server
The project includes a separate MCP server module for image search:
```bash
# Navigate to MCP server directory
cd ron-image-search-mcp-server

# Build and run MCP server
mvn spring-boot:run
```

## Architecture Overview

### Agent System Architecture
The project implements a hierarchical agent architecture:

1. **BaseAgent** (`src/main/java/com/ron/ronaiagent/agent/BaseAgent.java`)
   - Abstract base class for all agents
   - Manages agent state, messaging, and execution lifecycle
   - Implements step-based execution with configurable max steps

2. **ReActAgent** (`src/main/java/com/ron/ronaiagent/agent/ReActAgent.java`)
   - Extends BaseAgent with Reasoning and Acting pattern
   - Implements think-act cycle in the `step()` method
   - Abstract methods: `think()` and `act()`

3. **ToolCallAgent** (`src/main/java/com/ron/ronaiagent/agent/ToolCallAgent.java`)
   - Extends ReActAgent with tool-calling capabilities
   - Integrates with Spring AI's tool callback system
   - Manages tool execution via ToolCallingManager

4. **RonManus** (`src/main/java/com/ron/ronaiagent/agent/RonManus.java`)
   - Main AI agent implementation
   - Configured with system prompts and tool callbacks
   - Uses DashScope chat model with custom advisors

### Tool System
Tools are centrally registered in `ToolRegistration.java` and include:
- FileOperationTool: File system operations
- WebSearchTool: Web search functionality
- WebScrapingTool: Web content extraction
- PDFGenerationTool: PDF document creation
- ResourceDownloadTool: File download capabilities
- TerminateTool: Agent termination control

### RAG (Retrieval-Augmented Generation) System
Located in `src/main/java/com/ron/ronaiagent/chat/rag/`:
- **BookAppRagConfig**: Vector store configuration using DashScope embeddings
- **BookAppDocumentLoader**: Markdown document loading for knowledge base
- **BookAppRagPgVectorConfig**: PostgreSQL vector store integration
- **BookAppQueryRewriter**: Query enhancement and rewriting
- **BookAppRagCustomAdvisorFactory**: Custom RAG advisors

### MCP Server Integration
Separate module `ron-image-search-mcp-server/` provides:
- Image search capabilities via Pexels API
- MCP protocol support for tool integration
- Spring AI MCP server configuration

## Configuration

### Application Configuration
- Main config: `src/main/resources/application-local.yml`
- Contains DashScope API key, database connection, and search API keys
- Database: PostgreSQL with PgVector extension for vector storage

### Dependencies
- **Spring Boot 3.5.5** with Java 21
- **Spring AI Alibaba** for DashScope integration
- **LangChain4j** for additional AI capabilities
- **PgVector** for vector database storage
- **HuTool** for utility functions
- **Knife4j** for API documentation

## Key Components

### Chat Memory System
- `BookChatMemory`: Chat history management
- `FileBasedChatMemory`: Persistent chat storage

### Agent States
Agents cycle through states defined in `AgentState.java`:
- IDLE → RUNNING → FINISHED/ERROR

### Tool Callback Pattern
All tools follow Spring AI's `ToolCallback` interface and are automatically discovered by the agent system.

## Testing

The project includes comprehensive test coverage:
- Unit tests for all tools in `src/test/java/com/ron/ronaiagent/chat/tools/`
- Agent tests in `src/test/java/com/ron/ronaiagent/agent/`
- RAG configuration tests

## API Integration

### External Services
- **DashScope**: Alibaba's AI service for chat and embeddings
- **Pexels API**: Image search functionality
- **PostgreSQL**: Vector database for RAG functionality

### Health Check
Simple health endpoint available at `/health` for monitoring application status.

## Deep Architecture Analysis

### Design Patterns in Use

1. **Template Method Pattern**: BaseAgent defines execution skeleton, subclasses implement specific steps
2. **Strategy Pattern**: ReAct's think/act separation allows different implementation strategies
3. **Factory Pattern**: ToolRegistration centralizes tool creation and management
4. **Observer Pattern**: Advisor pattern intercepts requests/responses for logging and customization

### Key Architectural Decisions

1. **Step-based Execution**: Prevents infinite loops with configurable max steps (default: 10)
2. **Tool-First Approach**: Extends capabilities through tools rather than hardcoded features
3. **Streaming Support**: Real-time feedback via SseEmitter for better UX
4. **Security-First**: Path traversal protection, scoped file operations, parameter validation

### Agent Lifecycle Details

```
Initialization → Validation → Execution Loop → Completion
     ↓              ↓              ↓              ↓
  Setup          Check         Step()          Finalize
  State         Cancel        Think/Act        Stats
```

**Execution Flow**:
1. Validate state and set cancel flag
2. Loop with step counter and cancel checks
3. Execute `step()` method (think → act cycle)
4. Collect results and update state
5. Handle completion/error states

### Extension Points

1. **New Agent Types**: Extend BaseAgent or ReActAgent
2. **Custom Tools**: Implement ToolCallback interface
3. **Custom Advisors**: Extend CallAdvisor/StreamAdvisor
4. **RAG Extensions**: Custom DocumentRetriever and QueryAugmenter
5. **Memory Systems**: Implement custom ChatMemory

### Current Limitations

1. **Single-Agent Architecture**: No formal inter-agent collaboration mechanism
2. **Synchronous Messaging**: Message passing confined within individual agents
3. **Global Tool Namespace**: Tool names must be globally unique
4. **Simple State Management**: Lacks complex state persistence and recovery

## Multi-Agent Architecture Extension

### Extension Strategy

The current architecture provides a solid foundation for multi-agent expansion. Key components to add:

#### 1. Agent Management Layer
```java
public interface AgentManager {
    void registerAgent(String name, BaseAgent agent);
    BaseAgent getAgent(String name);
    List<BaseAgent> getAllAgents();
    void coordinateAgents(List<String> agentNames, String task);
}
```

#### 2. Message Bus for Inter-Agent Communication
```java
public interface MessageBus {
    void publish(String topic, Message message);
    Message subscribe(String topic, String agentId);
    void broadcast(Message message);
}
```

#### 3. Task Coordinator for Collaboration Patterns
```java
public class TaskCoordinator {
    // Master-Worker pattern
    void executeMasterWorker(String task, String masterAgent, List<String> workerAgents);

    // Chain pattern (sequential)
    void executeChain(String task, List<String> agentIds);

    // Parallel pattern
    void executeParallel(String task, List<String> agentIds);
}
```

### Recommended Specialized Agents

1. **FileProcessingAgent**: Specialized in file operations and document processing
2. **SearchAgent**: Handles web search and content retrieval
3. **AnalysisAgent**: Data analysis and insights generation
4. **CoordinatorAgent**: Task distribution and result aggregation
5. **RAGAgent**: Knowledge retrieval and augmented generation

### Collaboration Patterns

1. **Master-Worker**: Coordinator delegates subtasks to specialized agents
2. **Chain**: Sequential processing where each agent builds on previous output
3. **Parallel**: Multiple agents work independently on different aspects
4. **Hierarchy**: High-level planning agents coordinate lower-level execution agents

### Migration Path

**Phase 1: Backward Compatibility**
- Extend existing RonManus with proxy call capabilities
- Enhance tool registration for cross-agent tool sharing

**Phase 2: Agent Management**
- Create AgentManager interface and implementation
- Add agent lifecycle management
- Implement basic inter-agent communication

**Phase 3: Full Collaboration**
- Implement task coordinator
- Add distributed message bus
- Complete state management and error handling

### File Structure for Multi-Agent

```
src/main/java/com/ron/ronaiagent/
├── agent/
│   ├── BaseAgent.java (existing)
│   ├── ReActAgent.java (existing)
│   ├── ToolCallAgent.java (existing)
│   ├── RonManus.java (existing)
│   ├── coordinator/
│   │   ├── AgentManager.java
│   │   ├── AgentManagerImpl.java
│   │   ├── TaskCoordinator.java
│   │   └── CollaborationPattern.java (enum)
│   ├── communication/
│   │   ├── Message.java
│   │   ├── MessageBus.java
│   │   ├── MessageBusImpl.java
│   │   └── AgentMessage.java
│   └── specialized/
│       ├── FileProcessingAgent.java
│       ├── SearchAgent.java
│       ├── AnalysisAgent.java
│       └── CoordinatorAgent.java
└── config/
    └── MultiAgentConfig.java
```

## Multi-Agent System

### Configuration
Multi-agent system is auto-configured via `MultiAgentConfig.java`:
- Specialized agents are automatically registered
- MessageBus is available for inter-agent communication
- TaskCoordinator provides collaboration patterns

### Usage Example

```java
@Autowired
private TaskCoordinator taskCoordinator;

@Autowired
private AgentManager agentManager;

// Execute chain pattern
CoordinationResult result = taskCoordinator.executeChain(
    "Research topic and write report",
    List.of("search-agent", "analysis-agent", "file-processor")
);

// Execute parallel pattern
result = taskCoordinator.executeParallel(
    "Analyze data from different sources",
    List.of("search-agent", "analysis-agent")
);
```

### Available Agents
- **file-processor**: File operations and PDF generation
- **search-agent**: Web search and content retrieval
- **analysis-agent**: Data analysis and insights
- **coordinator**: Task coordination and result aggregation

### Message Bus Example

```java
@Autowired
private MessageBus messageBus;

// Subscribe to messages
messageBus.subscribe("task-updates", "my-agent", message -> {
    System.out.println("Received: " + message.getContent());
});

// Send direct message
Message message = new Message.Builder()
    .from("agent1")
    .to("agent2")
    .topic("task-updates")
    .content("Task completed")
    .build();

messageBus.sendDirect(message);
```