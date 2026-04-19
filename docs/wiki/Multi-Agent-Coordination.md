# Multi-Agent Coordination

## Overview

The multi-agent system enables specialized agents to collaborate on complex tasks through configurable patterns.

## Components

```
TaskCoordinator (interface)
  └── TaskCoordinatorImpl       Thread pool (10 threads), DisposableBean

AgentManager (interface)
  └── AgentManagerImpl          ConcurrentHashMap registry

MessageBus (interface)
  └── InMemoryMessageBus        Pub/sub + direct messaging

Specialized Agents:
  ├── FileProcessingAgent       ID: file-processor
  ├── SearchAgent               ID: search-agent
  ├── AnalysisAgent             ID: analysis-agent
  └── CoordinatorAgent          ID: coordinator
```

## Collaboration Patterns

<!-- AUTO-GENERATED:START -->
| Pattern | Enum | Description |
|---------|------|-------------|
| Master-Worker | `MASTER_WORKER` | Coordinator delegates subtasks to workers |
| Chain | `CHAIN` | Sequential execution, each builds on previous output |
| Parallel | `PARALLEL` | Concurrent execution on different aspects |
| Hierarchy | `HIERARCHY` | High-level planners coordinate low-level executors |
| Round-Robin | `ROUND_ROBIN` | Equal task distribution in rotation |
<!-- AUTO-GENERATED:END -->

## TaskCoordinator API

```java
public interface TaskCoordinator {
    CoordinationResult executeMasterWorker(taskDescription, masterAgentId, workerAgentIds);
    CoordinationResult executeChain(taskDescription, agentIds);
    CoordinationResult executeParallel(taskDescription, agentIds);
    CoordinationResult execute(taskDescription, pattern, agentIds);
}
```

### CoordinationResult

```java
public class CoordinationResult {
    boolean success;
    String finalOutput;
    Map<String, String> agentOutputs;  // agentId → output
    long executionTimeMs;
}
```

## Agent Registration

Agents are auto-registered in `MultiAgentConfig`:

```java
@Bean
public FileProcessingAgent fileProcessingAgent(ChatModel model, ToolCallback[] tools) {
    FileProcessingAgent agent = new FileProcessingAgent(tools, model);
    agentManager.registerAgent("file-processor", agent);
    return agent;
}
```

## Inter-Agent Messaging

`InMemoryMessageBus` supports:
- **Pub/sub**: `publish(topic, message)` → all subscribers
- **Direct**: `sendDirect(agentId, message)` → specific agent
- **Subscribe**: `subscribe(topic, agentId, handler)`

## Thread Pool

`TaskCoordinatorImpl` uses a fixed thread pool (10 threads) for parallel execution. Implements `DisposableBean` for graceful shutdown.

## Configuration

Set in `MultiAgentConfig`:

| Property | Default | Description |
|----------|---------|-------------|
| `agent.file.base-directory` | `/tmp/file-processing` | FileProcessingAgent base dir |
