# Agent System

## Agent Hierarchy

```
BaseAgent (abstract)
│   Lifecycle: run(), runStream(), cancel()
│   State machine: IDLE → RUNNING → FINISHED/ERROR
│   Context compaction, execution listener hooks
│
└── ReActAgent (abstract)
    │   step() = think() + act()
    │
    └── ToolCallAgent (abstract)
        │   think(): calls LLM with tools
        │   act(): executes tools with retry
        │   retryPolicy, chatOptions
        │
        └── RonManus
            System prompt, DashScope model, all tools
            maxSteps=10
```

## Agent Lifecycle

### State Machine

```
    ┌──────┐   run()    ┌─────────┐   step() ok  ┌──────────┐
    │ IDLE │ ──────────→ │ RUNNING │ ────────────→│ FINISHED │
    └──────┘             └────┬────┘              └──────────┘
                              │
                              │ exception
                              ▼
                         ┌────────┐
                         │ ERROR  │
                         └────────┘
```

### Execution Flow (BaseAgent.run)

```
1. Validate state (must be IDLE)
2. Set RUNNING, record startTime
3. Fire executionListener.onExecutionStart()
4. Loop while currentStep < maxSteps:
   a. Check cancel flag
   b. Call step() → delegates to think() + act()
   c. Fire executionListener.onStepComplete()
   d. Compact context if over 80% token threshold
   e. Increment currentStep
5. Fire executionListener.onExecutionComplete()
6. cleanup() in finally block
```

### Streaming (BaseAgent.runStream)

- Returns `SseEmitter` with 3-minute timeout
- Executes in `CompletableFuture.runAsync()`
- Each step result sent as SSE event
- Sends `[DONE]` marker on completion

## Think/Act Cycle

### think() (ToolCallAgent)

1. Adds `nextStepPrompt` as UserMessage
2. Calls LLM via ChatClient with `systemPrompt` + `availableTools`
3. LLM returns tool calls or plain text
4. If tool calls: returns `true` (proceed to act)
5. If no tool calls: adds AssistantMessage, returns `false` (end step)
6. On error: extracts root cause via `extractRootCause()`, adds error message

### act() (ToolCallAgent)

```
attempt = 0
while retryPolicy.shouldRetry(attempt):
    attempt++
    try:
        executeToolCalls() via ToolCallingManager
        update conversation history
        check for TerminateTool → set FINISHED
        return aggregated results
    catch:
        log warning with attempt number
        sleep with exponential backoff
        continue if retries remain
return error message after all attempts exhausted
```

## Context Window Management

The `ContextWindowManager` prevents context overflow:

| Method | Description |
|--------|-------------|
| `estimateTokenCount(messages)` | Char count / 4 heuristic |
| `shouldCompact(messages)` | `estimated > maxTokens * 0.8` |
| `compact(messages, keepRecentPairs)` | Keeps system messages + recent pairs |

**Compaction strategy**:
1. Preserve all `SystemMessage` entries
2. Insert compaction notice
3. Keep the last N user/assistant pairs

Default: `maxTokens=4000`, `compactionThreshold=0.8`, `keepRecentPairs=3`

## Retry Policy

`ToolExecutionRetryPolicy` controls tool execution retries:

| Setting | Default | Description |
|---------|---------|-------------|
| maxRetries | 2 | Maximum retry attempts |
| initialBackoffMs | 500 | Initial backoff delay |

**Backoff formula**: `initialBackoffMs * 2^(attempt-1)`
- Attempt 1: 500ms
- Attempt 2: 1000ms

Use `ToolExecutionRetryPolicy.noRetry()` for tools that should never retry.

## Execution Persistence

`AgentExecutionListener` interface provides lifecycle hooks:

```java
void onExecutionStart(agentName, prompt, maxSteps);
void onStepComplete(agentName, step, result);
void onExecutionComplete(agentName, finalState, result, durationMs);
```

`JdbcAgentExecutionListener` persists state to the `agent_execution` table (active only in `prod` profile).

## Specialized Agents

All extend `ToolCallAgent` and are registered via `MultiAgentConfig`:

| Agent | ID | Purpose |
|-------|----|---------|
| FileProcessingAgent | file-processor | File read/write operations |
| SearchAgent | search-agent | Web search tasks |
| AnalysisAgent | analysis-agent | Data analysis |
| CoordinatorAgent | coordinator | Delegates to other agents |

Agents are managed by `AgentManager` (ConcurrentHashMap registry) and coordinated by `TaskCoordinator`.
