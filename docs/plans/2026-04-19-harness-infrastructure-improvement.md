# Harness Infrastructure Improvement Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Strengthen the 5 core Harness infrastructure modules (Agentic Loop, Tool System, Memory & Context, Permission Control, State Persistence) to production-grade quality.

**Architecture:** Incremental improvements across 5 phases, each phase independent and committable. TDD-first: write test, verify RED, implement, verify GREEN, refactor. Every task requires code review before commit.

**Tech Stack:** Java 21, Spring Boot 3.5.5, Spring AI 1.0.2, Spring AI Alibaba 1.0.0-M2.1, H2 (tests), PostgreSQL (prod), JUnit 5, Mockito

---

## Review Summary

### Module Ratings

| Module | Rating | Key Gaps |
|--------|--------|----------|
| Agentic Loop | ★★★★☆ | No retry, no context compaction, weak error recovery |
| Tool System | ★★★★☆ | No standardized output, no observability metrics |
| Memory & Context | ★★★☆☆ | No persistence, no token management, no eviction limits |
| Permission Control | ★★★☆☆ | No user auth, no API endpoint protection, no agent isolation |
| State Persistence | ★★☆☆☆ | No DB persistence, no checkpoint, all in-memory only |

### Priority Order (by impact on production readiness)

1. **Phase 1 — Tool Output Standardization** (low risk, high value)
2. **Phase 2 — Agentic Loop Error Recovery & Retry** (medium risk, high value)
3. **Phase 3 — Context Window Management** (medium risk, high value)
4. **Phase 4 — Memory Persistence & Eviction** (high risk, high value)
5. **Phase 5 — State Persistence Layer** (high risk, essential for production)

---

## Quality Gates

Every task must pass these gates before commit:

- [ ] `mvn test` passes (all existing + new tests green)
- [ ] Code review by `code-reviewer` agent (CRITICAL/HIGH issues resolved)
- [ ] No new security vulnerabilities (check for hardcoded secrets, injection)
- [ ] Documentation updated in this plan's progress section
- [ ] Commit message follows conventional commits format

---

## Phase 1: Tool Output Standardization

### Task 1.1: Create ToolResult Standard Response Record

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/chat/tools/ToolResult.java`
- Test: `src/test/java/com/ron/ronaiagent/chat/tools/ToolResultTest.java`

**Step 1: Write the failing test**

```java
package com.ron.ronaiagent.chat.tools;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ToolResultTest {

    @Test
    void success_shouldCreateValidResult() {
        ToolResult result = ToolResult.success("File written to /tmp/test.txt");
        assertEquals("success", result.status());
        assertEquals("File written to /tmp/test.txt", result.summary());
        assertTrue(result.nextActions().isEmpty());
        assertTrue(result.artifacts().isEmpty());
        assertNull(result.errorMessage());
    }

    @Test
    void error_shouldCreateErrorResult() {
        ToolResult result = ToolResult.error("File not found: missing.txt");
        assertEquals("error", result.status());
        assertEquals("File not found: missing.txt", result.errorMessage());
        assertNull(result.summary());
    }

    @Test
    void withArtifacts_shouldIncludePaths() {
        ToolResult result = ToolResult.success("PDF generated")
                .withArtifacts(List.of("/tmp/output/report.pdf"));
        assertEquals(List.of("/tmp/output/report.pdf"), result.artifacts());
    }

    @Test
    void withNextActions_shouldIncludeSuggestions() {
        ToolResult result = ToolResult.success("Search completed")
                .withNextActions(List.of("Read the first result", "Refine search terms"));
        assertEquals(2, result.nextActions().size());
    }

    @Test
    void toJson_shouldReturnStructuredString() {
        ToolResult result = ToolResult.success("Done")
                .withArtifacts(List.of("/tmp/a.txt"));
        String json = result.toJson();
        assertTrue(json.contains("\"status\":\"success\""));
        assertTrue(json.contains("/tmp/a.txt"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ToolResultTest -pl .`
Expected: FAIL — `ToolResult` class does not exist

**Step 3: Write minimal implementation**

```java
package com.ron.ronaiagent.chat.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ToolResult(
    String status,
    String summary,
    String errorMessage,
    List<String> nextActions,
    List<String> artifacts
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ToolResult {
        nextActions = nextActions == null ? Collections.emptyList() : List.copyOf(nextActions);
        artifacts = artifacts == null ? Collections.emptyList() : List.copyOf(artifacts);
    }

    public static ToolResult success(String summary) {
        return new ToolResult("success", summary, null, List.of(), List.of());
    }

    public static ToolResult error(String errorMessage) {
        return new ToolResult("error", null, errorMessage, List.of(), List.of());
    }

    public static ToolResult warning(String summary) {
        return new ToolResult("warning", summary, null, List.of(), List.of());
    }

    public ToolResult withNextActions(List<String> actions) {
        return new ToolResult(status, summary, errorMessage, new ArrayList<>(actions), artifacts);
    }

    public ToolResult withArtifacts(List<String> paths) {
        return new ToolResult(status, summary, errorMessage, nextActions, new ArrayList<>(paths));
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"error\",\"errorMessage\":\"JSON serialization failed\"}";
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ToolResultTest -pl .`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/chat/tools/ToolResult.java src/test/java/com/ron/ronaiagent/chat/tools/ToolResultTest.java
git commit -m "feat(tools): add standardized ToolResult response record"
```

---

### Task 1.2: Update FileOperationTool to use ToolResult

**Files:**
- Modify: `src/main/java/com/ron/ronaiagent/chat/tools/FileOperationTool.java`
- Modify: `src/test/java/com/ron/ronaiagent/chat/tools/FileOperationToolTest.java`

**Step 1: Write the failing test**

Add to existing `FileOperationToolTest.java`:

```java
@Test
void readFile_success_shouldReturnToolResultJson() {
    // Setup: create a temp file
    String fileName = "test-read.txt";
    Path filePath = ToolSecurityUtils.resolveSafePath(
            System.getProperty("user.dir") + "/temp/file", fileName);
    FileUtil.writeUtf8String("hello world", filePath.toFile());

    String result = fileOperationTool.readFile(fileName);
    assertTrue(result.contains("\"status\":\"success\""));
    assertTrue(result.contains("hello world"));
}

@Test
void readFile_error_shouldReturnToolResultError() {
    String result = fileOperationTool.readFile("nonexistent.txt");
    assertTrue(result.contains("\"status\":\"error\""));
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=FileOperationToolTest -pl .`
Expected: FAIL — readFile still returns raw string

**Step 3: Update FileOperationTool.readFile**

Change the return format in `FileOperationTool.java`:

```java
@Tool(description = "Read content from a file")
public String readFile(@ToolParam(description = "Name of the file to read") String fileName) {
    try {
        Path filePath = ToolSecurityUtils.resolveSafePath(FILE_DIR, fileName);
        if (!Files.exists(filePath)) {
            return ToolResult.error("File not found: " + fileName).toJson();
        }
        String content = FileUtil.readUtf8String(filePath.toFile());
        return ToolResult.success(content)
                .withArtifacts(List.of(filePath.toString()))
                .withNextActions(List.of("Summarize the file content", "Search within the file"))
                .toJson();
    } catch (Exception e) {
        return ToolResult.error("Error reading file: " + e.getMessage()).toJson();
    }
}
```

Do the same for `writeFile()`.

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=FileOperationToolTest -pl .`
Expected: PASS

**Step 5: Code review**

Run code-reviewer agent on `FileOperationTool.java` changes.

**Step 6: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/chat/tools/FileOperationTool.java src/test/java/com/ron/ronaiagent/chat/tools/FileOperationToolTest.java
git commit -m "feat(tools): migrate FileOperationTool to standardized ToolResult output"
```

---

### Task 1.3: Update Remaining Tools to use ToolResult

**Files:**
- Modify: `src/main/java/com/ron/ronaiagent/chat/tools/WebSearchTool.java`
- Modify: `src/main/java/com/ron/ronaiagent/chat/tools/WebScrapingTool.java`
- Modify: `src/main/java/com/ron/ronaiagent/chat/tools/PDFGenerationTool.java`
- Modify: `src/main/java/com/ron/ronaiagent/chat/tools/ResourceDownloadTool.java`
- Modify: `src/main/java/com/ron/ronaiagent/chat/tools/ImageSearchTool.java`
- Modify: `src/test/java/com/ron/ronaiagent/chat/tools/WebSearchToolTest.java`
- Modify: `src/test/java/com/ron/ronaiagent/chat/tools/WebScrapingToolTest.java`
- Modify: `src/test/java/com/ron/ronaiagent/chat/tools/PDFGenerationToolTest.java`
- Modify: `src/test/java/com/ron/ronaiagent/chat/tools/ResourceDownloadToolTest.java`

**Pattern for each tool:**

1. Write test that asserts `"status":"success"` or `"status":"error"` in output
2. Verify RED
3. Wrap return values in `ToolResult.success(...).toJson()` / `ToolResult.error(...).toJson()`
4. Add `nextActions` relevant to each tool (e.g., WebSearchTool: "Read a search result", "Refine search terms")
5. Add `artifacts` where applicable (e.g., PDFGenerationTool: path to generated PDF)
6. Verify GREEN
7. Code review
8. Commit (one per tool)

Example for WebSearchTool:

```java
@Tool(description = "Search the web for information")
public String searchWeb(@ToolParam(description = "Search query") String query) {
    try {
        // ... existing search logic ...
        return ToolResult.success("Found " + results.size() + " results for: " + query)
                .withNextActions(List.of("Read a specific result", "Refine search terms", "Scrape a result page"))
                .toJson();
    } catch (Exception e) {
        return ToolResult.error("Search failed: " + e.getMessage()).toJson();
    }
}
```

**Commit:** one per tool, e.g.:
```
feat(tools): migrate WebSearchTool to standardized ToolResult output
feat(tools): migrate WebScrapingTool to standardized ToolResult output
feat(tools): migrate PDFGenerationTool to standardized ToolResult output
feat(tools): migrate ResourceDownloadTool to standardized ToolResult output
feat(tools): migrate ImageSearchTool to standardized ToolResult output
```

---

## Phase 2: Agentic Loop Error Recovery & Retry

### Task 2.1: Add ToolExecutionRetryPolicy

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/agent/ToolExecutionRetryPolicy.java`
- Test: `src/test/java/com/ron/ronaiagent/agent/ToolExecutionRetryPolicyTest.java`

**Step 1: Write the failing test**

```java
package com.ron.ronaiagent.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionRetryPolicyTest {

    @Test
    void shouldRetry_onFirstFailure() {
        ToolExecutionRetryPolicy policy = new ToolExecutionRetryPolicy(3, 100);
        assertTrue(policy.shouldRetry(1));
    }

    @Test
    void shouldNotRetry_whenMaxAttemptsExceeded() {
        ToolExecutionRetryPolicy policy = new ToolExecutionRetryPolicy(3, 100);
        assertFalse(policy.shouldRetry(3));
        assertFalse(policy.shouldRetry(4));
    }

    @Test
    void getBackoffMs_shouldIncreaseExponentially() {
        ToolExecutionRetryPolicy policy = new ToolExecutionRetryPolicy(3, 100);
        assertEquals(100, policy.getBackoffMs(1));
        assertEquals(200, policy.getBackoffMs(2));
        assertEquals(400, policy.getBackoffMs(3));
    }

    @Test
    void defaultPolicy_shouldHaveSaneDefaults() {
        ToolExecutionRetryPolicy policy = ToolExecutionRetryPolicy.defaultPolicy();
        assertTrue(policy.shouldRetry(1));
        assertTrue(policy.shouldRetry(2));
        assertFalse(policy.shouldRetry(3));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ToolExecutionRetryPolicyTest -pl .`
Expected: FAIL

**Step 3: Write implementation**

```java
package com.ron.ronaiagent.agent;

public class ToolExecutionRetryPolicy {
    private final int maxRetries;
    private final long initialBackoffMs;

    public ToolExecutionRetryPolicy(int maxRetries, long initialBackoffMs) {
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
    }

    public static ToolExecutionRetryPolicy defaultPolicy() {
        return new ToolExecutionRetryPolicy(2, 500);
    }

    public static ToolExecutionRetryPolicy noRetry() {
        return new ToolExecutionRetryPolicy(0, 0);
    }

    public boolean shouldRetry(int attemptNumber) {
        return attemptNumber <= maxRetries;
    }

    public long getBackoffMs(int attemptNumber) {
        return initialBackoffMs * (1L << (attemptNumber - 1));
    }
}
```

**Step 4: Run test**

Run: `mvn test -Dtest=ToolExecutionRetryPolicyTest -pl .`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/agent/ToolExecutionRetryPolicy.java src/test/java/com/ron/ronaiagent/agent/ToolExecutionRetryPolicyTest.java
git commit -m "feat(agent): add ToolExecutionRetryPolicy with exponential backoff"
```

---

### Task 2.2: Integrate Retry into ToolCallAgent.act()

**Files:**
- Modify: `src/main/java/com/ron/ronaiagent/agent/ToolCallAgent.java:102-146`
- Modify: `src/test/java/com/ron/ronaiagent/agent/RonManusTest.java`

**Step 1: Write the failing test**

Add to `RonManusTest.java` (or create `ToolCallAgentTest.java` if not exists):

```java
@Test
void act_withRetry_shouldAttemptMultipleTimesOnFailure() {
    // Use a spy or mock to simulate first call failing, second succeeding
    // This tests that the retry logic is wired into the act() method
}
```

**Step 2: Verify RED**

**Step 3: Modify ToolCallAgent.act()**

Replace the current `act()` method in `ToolCallAgent.java` (lines 102-146) with retry-aware logic:

```java
private ToolExecutionRetryPolicy retryPolicy = ToolExecutionRetryPolicy.defaultPolicy();

public void setRetryPolicy(ToolExecutionRetryPolicy policy) {
    this.retryPolicy = policy;
}

@Override
public String act() {
    if (!toolCallResponse.hasToolCalls()) {
        return "No tool calls requested";
    }

    int attempt = 0;
    Exception lastError = null;

    while (retryPolicy.shouldRetry(attempt)) {
        attempt++;
        try {
            Prompt prompt = new Prompt(getMessages(), chatOptions);
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallResponse);
            setMessages(toolExecutionResult.conversationHistory());

            List<Message> history = toolExecutionResult.conversationHistory();
            if (!history.isEmpty() && history.getLast() instanceof ToolResponseMessage toolResponseMessage) {
                String results = toolResponseMessage.getResponses().stream()
                        .filter(Objects::nonNull)
                        .map(response -> "Tool " + response.name() + " completed. Result: " + response.responseData())
                        .collect(Collectors.joining("\n"));

                boolean hasTerminateTool = toolResponseMessage.getResponses().stream()
                        .anyMatch(response -> response != null &&
                                ("doTerminate".equals(response.name()) ||
                                 response.name().toLowerCase().contains("terminate")));

                if (hasTerminateTool) {
                    logger.info("{} terminated via TerminateTool", getName());
                    setAgentState(AgentState.FINISHED);
                }
                return results;
            }
        } catch (Exception e) {
            lastError = e;
            logger.warn("{}: tool execution attempt {}/{} failed: {}",
                    getName(), attempt, retryPolicy.shouldRetry(attempt + 1) ? attempt + 1 : attempt,
                    e.getMessage());
            if (retryPolicy.shouldRetry(attempt + 1)) {
                try {
                    Thread.sleep(retryPolicy.getBackoffMs(attempt));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    logger.error("{}: tool execution failed after {} attempts", getName(), attempt, lastError);
    getMessages().add(new AssistantMessage("Tool execution failed after " + attempt + " attempts: " +
            (lastError != null ? lastError.getMessage() : "unknown error")));
    return "Tool execution failed: " + (lastError != null ? lastError.getMessage() : "unknown error");
}
```

**Step 4: Run all tests**

Run: `mvn test -pl .`
Expected: ALL PASS

**Step 5: Code review**

**Step 6: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/agent/ToolCallAgent.java src/test/java/com/ron/ronaiagent/agent/RonManusTest.java
git commit -m "feat(agent): integrate retry policy with exponential backoff into ToolCallAgent.act()"
```

---

### Task 2.3: Add Structured Error Recovery to think()

**Files:**
- Modify: `src/main/java/com/ron/ronaiagent/agent/ToolCallAgent.java:57-100`
- Test: `src/test/java/com/ron/ronaiagent/agent/RonManusTest.java`

**Step 1: Write the failing test**

```java
@Test
void think_onLLMError_shouldRecordRootCause() {
    // Simulate LLM call failure and verify error message contains root cause hint
}
```

**Step 2: Modify think() error handling**

Replace the catch block at lines 94-98 with structured error recovery:

```java
} catch (Exception e) {
    String rootCause = extractRootCause(e);
    logger.error("{}: think phase error — root cause: {}", getName(), rootCause, e);

    // Structured error message with recovery hints
    String errorMsg = String.format(
        "Thinking phase error. Root cause: %s. Safe retry: rephrase the query. Stop condition: if error persists after 2 retries.",
        rootCause);
    getMessages().add(new AssistantMessage(errorMsg));
    return false;
}
```

Add helper:

```java
private String extractRootCause(Throwable t) {
    Throwable cause = t;
    int depth = 0;
    while (cause.getCause() != null && depth < 5) {
        cause = cause.getCause();
        depth++;
    }
    return cause.getClass().getSimpleName() + ": " + cause.getMessage();
}
```

**Step 3: Verify tests pass**

**Step 4: Code review + Commit**

```bash
git commit -m "feat(agent): add structured error recovery with root cause extraction in think()"
```

---

## Phase 3: Context Window Management

### Task 3.1: Create ContextWindowManager

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/agent/ContextWindowManager.java`
- Test: `src/test/java/com/ron/ronaiagent/agent/ContextWindowManagerTest.java`

**Step 1: Write the failing test**

```java
package com.ron.ronaiagent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ContextWindowManagerTest {

    @Test
    void estimateTokenCount_shouldReturnNonZero() {
        ContextWindowManager manager = new ContextWindowManager(4000, 8);
        List<Message> messages = List.of(new UserMessage("Hello, how are you?"));
        int tokens = manager.estimateTokenCount(messages);
        assertTrue(tokens > 0);
    }

    @Test
    void shouldCompact_shouldReturnTrue_whenOverThreshold() {
        ContextWindowManager manager = new ContextWindowManager(100, 8);
        List<Message> messages = new ArrayList<>();
        // Add enough messages to exceed 80% of 100 tokens
        for (int i = 0; i < 50; i++) {
            messages.add(new UserMessage("This is a somewhat longer message to consume tokens number " + i));
        }
        assertTrue(manager.shouldCompact(messages));
    }

    @Test
    void shouldCompact_shouldReturnFalse_whenUnderThreshold() {
        ContextWindowManager manager = new ContextWindowManager(10000, 8);
        List<Message> messages = List.of(new UserMessage("Short message"));
        assertFalse(manager.shouldCompact(messages));
    }

    @Test
    void compact_shouldKeepSystemAndRecentMessages() {
        ContextWindowManager manager = new ContextWindowManager(100, 8);
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            messages.add(new UserMessage("Message " + i));
            messages.add(new AssistantMessage("Response " + i));
        }
        List<Message> compacted = manager.compact(messages, 4);
        // Should keep first system/user + last 4 exchanges
        assertTrue(compacted.size() < messages.size());
        // Should keep recent messages
        assertTrue(compacted.getLast() instanceof AssistantMessage);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ContextWindowManagerTest -pl .`
Expected: FAIL

**Step 3: Write implementation**

```java
package com.ron.ronaiagent.agent;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import java.util.ArrayList;
import java.util.List;

public class ContextWindowManager {
    private final int maxTokens;
    private final double compactionThreshold;
    private static final int CHARS_PER_TOKEN = 4;

    public ContextWindowManager(int maxTokens, double compactionThreshold) {
        this.maxTokens = maxTokens;
        this.compactionThreshold = compactionThreshold;
    }

    public static ContextWindowManager defaultManager() {
        return new ContextWindowManager(4000, 0.8);
    }

    public int estimateTokenCount(List<Message> messages) {
        return messages.stream()
                .mapToInt(msg -> {
                    String text = msg.getText() != null ? msg.getText() : "";
                    return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
                })
                .sum();
    }

    public boolean shouldCompact(List<Message> messages) {
        int estimated = estimateTokenCount(messages);
        return estimated > maxTokens * compactionThreshold;
    }

    public List<Message> compact(List<Message> messages, int keepRecentPairs) {
        if (messages.size() <= keepRecentPairs * 2 + 1) {
            return messages;
        }

        List<Message> compacted = new ArrayList<>();

        // Keep system messages at the front
        for (Message msg : messages) {
            if (msg instanceof SystemMessage) {
                compacted.add(msg);
            }
        }

        // Add a compaction notice
        compacted.add(new SystemMessage(
            "[Context was compacted. Earlier conversation history has been summarized.]"));

        // Keep the most recent N message pairs
        int recentStart = messages.size() - keepRecentPairs * 2;
        if (recentStart < 0) recentStart = 0;
        for (int i = recentStart; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (!(msg instanceof SystemMessage)) {
                compacted.add(msg);
            }
        }

        return compacted;
    }
}
```

**Step 4: Run test**

Run: `mvn test -Dtest=ContextWindowManagerTest -pl .`
Expected: PASS

**Step 5: Commit**

```bash
git commit -m "feat(agent): add ContextWindowManager for token estimation and compaction"
```

---

### Task 3.2: Integrate ContextWindowManager into BaseAgent

**Files:**
- Modify: `src/main/java/com/ron/ronaiagent/agent/BaseAgent.java:86-89`
- Modify: `src/main/java/com/ron/ronaiagent/agent/ToolCallAgent.java`
- Test: update `src/test/java/com/ron/ronaiagent/agent/RonManusTest.java`

**Step 1: Add field to BaseAgent**

```java
// In BaseAgent.java, add field:
private ContextWindowManager contextWindowManager = ContextWindowManager.defaultManager();
```

**Step 2: Add compaction check in the step loop**

In `BaseAgent.java`, after line 104 (`currentStep++`), add:

```java
// Compact context if approaching token limit
if (contextWindowManager.shouldCompact(messages)) {
    messages = contextWindowManager.compact(messages, 3);
    log.info("Agent {} compacted context at step {}", name, currentStep);
}
```

**Step 3: Write test**

```java
@Test
void run_withLongContext_shouldCompactMessages() {
    // Create agent with very small context window
    // Feed many messages to trigger compaction
    // Verify message count decreased after compaction
}
```

**Step 4: Verify all tests pass**

Run: `mvn test -pl .`

**Step 5: Code review + Commit**

```bash
git commit -m "feat(agent): integrate context window compaction into agent loop"
```

---

## Phase 4: Memory Persistence & Eviction

### Task 4.1: Create Database-Backed ChatMemoryRepository

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/chat/memory/JdbcChatMemoryRepository.java`
- Create: `src/main/resources/schema.sql` (H2-compatible DDL for tests)
- Test: `src/test/java/com/ron/ronaiagent/chat/memory/JdbcChatMemoryRepositoryTest.java`

**Step 1: Write the failing test**

```java
package com.ron.ronaiagent.chat.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import javax.sql.DataSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JdbcChatMemoryRepositoryTest {

    private JdbcChatMemoryRepository repository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new JdbcChatMemoryRepository(jdbcTemplate);
    }

    @Test
    void saveAndRetrieve_messages_shouldPersistAcrossInstances() {
        List<Message> messages = List.of(
                new UserMessage("Hello"),
                new AssistantMessage("Hi there!")
        );
        repository.saveAll("conv-1", messages);

        JdbcChatMemoryRepository newRepo = new JdbcChatMemoryRepository(jdbcTemplate);
        List<Message> retrieved = newRepo.findByConversationId("conv-1");
        assertEquals(2, retrieved.size());
        assertEquals("Hello", retrieved.get(0).getText());
    }

    @Test
    void deleteByConversationId_shouldRemoveAllMessages() {
        repository.saveAll("conv-2", List.of(new UserMessage("Test")));
        repository.deleteByConversationId("conv-2");
        assertTrue(repository.findByConversationId("conv-2").isEmpty());
    }

    @Test
    void findByConversationId_nonExistent_shouldReturnEmpty() {
        assertTrue(repository.findByConversationId("nonexistent").isEmpty());
    }

    @Test
    void getMaxMessagesPerConversation_shouldEnforceLimit() {
        JdbcChatMemoryRepository limitedRepo = new JdbcChatMemoryRepository(jdbcTemplate, 5);
        for (int i = 0; i < 10; i++) {
            limitedRepo.saveAll("conv-limited", List.of(new UserMessage("Msg " + i)));
        }
        List<Message> messages = limitedRepo.findByConversationId("conv-limited");
        assertTrue(messages.size() <= 5);
    }
}
```

**Step 2: Create schema.sql**

```sql
CREATE TABLE IF NOT EXISTS chat_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metadata TEXT
);

CREATE INDEX IF NOT EXISTS idx_chat_memory_conv_id ON chat_memory(conversation_id);
CREATE INDEX IF NOT EXISTS idx_chat_memory_created_at ON chat_memory(created_at);
```

**Step 3: Write implementation**

```java
package com.ron.ronaiagent.chat.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JdbcChatMemoryRepository implements ChatMemoryRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxMessagesPerConversation;

    public JdbcChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, 100);
    }

    public JdbcChatMemoryRepository(JdbcTemplate jdbcTemplate, int maxMessagesPerConversation) {
        this.jdbcTemplate = jdbcTemplate;
        this.maxMessagesPerConversation = maxMessagesPerConversation;
    }

    @Override
    public @NotNull List<Message> findByConversationId(@NotNull String conversationId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT message_type, content FROM chat_memory WHERE conversation_id = ? ORDER BY created_at ASC",
                conversationId);

        List<Message> messages = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String type = (String) row.get("message_type");
            String content = (String) row.get("content");
            messages.add(createMessage(type, content));
        }
        return messages;
    }

    @Override
    public void saveAll(@NotNull String conversationId, @NotNull List<Message> messages) {
        for (Message message : messages) {
            jdbcTemplate.update(
                    "INSERT INTO chat_memory (conversation_id, message_type, content) VALUES (?, ?, ?)",
                    conversationId, getMessageType(message), message.getText());
        }
        evictOldMessages(conversationId);
    }

    @Override
    public void deleteByConversationId(@NotNull String conversationId) {
        jdbcTemplate.update("DELETE FROM chat_memory WHERE conversation_id = ?", conversationId);
    }

    private void evictOldMessages(String conversationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_memory WHERE conversation_id = ?", Integer.class, conversationId);
        if (count != null && count > maxMessagesPerConversation) {
            int toDelete = count - maxMessagesPerConversation;
            jdbcTemplate.update(
                    "DELETE FROM chat_memory WHERE conversation_id = ? AND id IN " +
                    "(SELECT id FROM chat_memory WHERE conversation_id = ? ORDER BY created_at ASC LIMIT ?)",
                    conversationId, conversationId, toDelete);
        }
    }

    private String getMessageType(Message message) {
        if (message instanceof UserMessage) return "USER";
        if (message instanceof AssistantMessage) return "ASSISTANT";
        if (message instanceof SystemMessage) return "SYSTEM";
        return "UNKNOWN";
    }

    private Message createMessage(String type, String content) {
        return switch (type) {
            case "USER" -> new UserMessage(content);
            case "ASSISTANT" -> new AssistantMessage(content);
            case "SYSTEM" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
    }
}
```

**Step 4: Run tests**

Run: `mvn test -Dtest=JdbcChatMemoryRepositoryTest -pl .`
Expected: PASS

**Step 5: Code review + Commit**

```bash
git commit -m "feat(memory): add JDBC-backed ChatMemoryRepository with eviction"
```

---

### Task 4.2: Create Memory Configuration with Profile-Based Switching

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/chat/memory/MemoryConfig.java`
- Modify: `src/main/java/com/ron/ronaiagent/app/BookApp.java` (inject new repository)

**Step 1: Write failing test**

```java
@Test
void memoryConfig_withH2Profile_shouldUseJdbcRepository() {
    // Spring context test with H2 profile
}
```

**Step 2: Create MemoryConfig**

```java
package com.ron.ronaiagent.chat.memory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import javax.sql.DataSource;

@Configuration
public class MemoryConfig {

    @Bean
    @Profile({"dev", "local"})
    public ChatMemoryRepository inMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    @Profile("prod")
    public ChatMemoryRepository jdbcRepository(DataSource dataSource) {
        return new JdbcChatMemoryRepository(
                new org.springframework.jdbc.core.JdbcTemplate(dataSource));
    }
}
```

**Step 3: Update BookApp to use injected repository**

**Step 4: Verify all tests pass**

**Step 5: Code review + Commit**

```bash
git commit -m "feat(memory): add profile-based memory repository switching (in-memory vs JDBC)"
```

---

## Phase 5: State Persistence Layer

### Task 5.1: Create AgentExecution Entity and Repository

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/persistence/AgentExecutionRecord.java`
- Create: `src/main/java/com/ron/ronaiagent/persistence/AgentExecutionRepository.java`
- Test: `src/test/java/com/ron/ronaiagent/persistence/AgentExecutionRepositoryTest.java`

**Step 1: Write the failing test**

```java
package com.ron.ronaiagent.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgentExecutionRepositoryTest {

    private AgentExecutionRepository repository;

    @BeforeEach
    void setUp() {
        DataSource ds = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
        repository = new AgentExecutionRepository(new JdbcTemplate(ds));
    }

    @Test
    void save_andFindById_shouldRoundTrip() {
        AgentExecutionRecord record = AgentExecutionRecord.builder()
                .agentName("RonManus")
                .prompt("What is Java?")
                .status("RUNNING")
                .currentStep(3)
                .maxSteps(10)
                .startTime(LocalDateTime.now())
                .build();

        Long id = repository.save(record);
        assertNotNull(id);

        AgentExecutionRecord loaded = repository.findById(id);
        assertNotNull(loaded);
        assertEquals("RonManus", loaded.getAgentName());
        assertEquals(3, loaded.getCurrentStep());
    }

    @Test
    void findByStatus_shouldReturnMatchingRecords() {
        repository.save(AgentExecutionRecord.builder()
                .agentName("test").prompt("p").status("RUNNING")
                .currentStep(1).maxSteps(10).startTime(LocalDateTime.now()).build());
        repository.save(AgentExecutionRecord.builder()
                .agentName("test2").prompt("p2").status("FINISHED")
                .currentStep(5).maxSteps(10).startTime(LocalDateTime.now()).build());

        List<AgentExecutionRecord> running = repository.findByStatus("RUNNING");
        assertEquals(1, running.size());
        assertEquals("test", running.getFirst().getAgentName());
    }

    @Test
    void updateStatus_shouldModifyRecord() {
        Long id = repository.save(AgentExecutionRecord.builder()
                .agentName("test").prompt("p").status("RUNNING")
                .currentStep(1).maxSteps(10).startTime(LocalDateTime.now()).build());

        repository.updateStatus(id, "FINISHED", 5, "Task completed successfully");
        AgentExecutionRecord updated = repository.findById(id);
        assertEquals("FINISHED", updated.getStatus());
        assertEquals(5, updated.getCurrentStep());
    }
}
```

**Step 2: Add to schema.sql**

```sql
CREATE TABLE IF NOT EXISTS agent_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_name VARCHAR(100) NOT NULL,
    prompt TEXT,
    status VARCHAR(20) NOT NULL,
    current_step INT DEFAULT 0,
    max_steps INT DEFAULT 10,
    result TEXT,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_exec_status ON agent_execution(status);
CREATE INDEX IF NOT EXISTS idx_agent_exec_name ON agent_execution(agent_name);
```

**Step 3: Write implementation**

```java
// AgentExecutionRecord.java — Lombok @Builder @Data
package com.ron.ronaiagent.persistence;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AgentExecutionRecord {
    private Long id;
    private String agentName;
    private String prompt;
    private String status;
    private int currentStep;
    private int maxSteps;
    private String result;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String errorMessage;
}
```

```java
// AgentExecutionRepository.java
package com.ron.ronaiagent.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.time.LocalDateTime;
import java.util.List;

public class AgentExecutionRepository {
    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<AgentExecutionRecord> ROW_MAPPER = (rs, rowNum) ->
            AgentExecutionRecord.builder()
                    .id(rs.getLong("id"))
                    .agentName(rs.getString("agent_name"))
                    .prompt(rs.getString("prompt"))
                    .status(rs.getString("status"))
                    .currentStep(rs.getInt("current_step"))
                    .maxSteps(rs.getInt("max_steps"))
                    .result(rs.getString("result"))
                    .startTime(rs.getTimestamp("start_time") != null ?
                            rs.getTimestamp("start_time").toLocalDateTime() : null)
                    .endTime(rs.getTimestamp("end_time") != null ?
                            rs.getTimestamp("end_time").toLocalDateTime() : null)
                    .durationMs(rs.getLong("duration_ms"))
                    .errorMessage(rs.getString("error_message"))
                    .build();

    public AgentExecutionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long save(AgentExecutionRecord record) {
        jdbcTemplate.update(
                "INSERT INTO agent_execution (agent_name, prompt, status, current_step, max_steps, start_time) VALUES (?, ?, ?, ?, ?, ?)",
                record.getAgentName(), record.getPrompt(), record.getStatus(),
                record.getCurrentStep(), record.getMaxSteps(), record.getStartTime());
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public AgentExecutionRecord findById(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT * FROM agent_execution WHERE id = ?", ROW_MAPPER, id);
    }

    public List<AgentExecutionRecord> findByStatus(String status) {
        return jdbcTemplate.query(
                "SELECT * FROM agent_execution WHERE status = ? ORDER BY created_at DESC",
                ROW_MAPPER, status);
    }

    public void updateStatus(Long id, String status, int currentStep, String result) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "UPDATE agent_execution SET status = ?, current_step = ?, result = ?, end_time = ? WHERE id = ?",
                status, currentStep, result, now, id);
    }
}
```

**Step 4: Run tests**

**Step 5: Code review + Commit**

```bash
git commit -m "feat(persistence): add AgentExecutionRecord with JDBC repository for state tracking"
```

---

### Task 5.2: Integrate State Persistence into BaseAgent

**Files:**
- Modify: `src/main/java/com/ron/ronaiagent/agent/BaseAgent.java` (add persistence hooks)
- Create: `src/main/java/com/ron/ronaiagent/agent/AgentExecutionListener.java`
- Test: update existing agent tests

**Step 1: Write failing test**

```java
@Test
void run_shouldPersistExecutionState() {
    // Verify that running an agent creates an AgentExecutionRecord
}
```

**Step 2: Create AgentExecutionListener interface**

```java
package com.ron.ronaiagent.agent;

public interface AgentExecutionListener {
    void onExecutionStart(String agentName, String prompt, int maxSteps);
    void onStepComplete(String agentName, int step, String result);
    void onExecutionComplete(String agentName, AgentState finalState, String result, long durationMs);
}
```

**Step 3: Add listener support to BaseAgent**

```java
// In BaseAgent.java, add:
private AgentExecutionListener executionListener;

// In run(), after setting RUNNING:
if (executionListener != null) {
    executionListener.onExecutionStart(name, userPrompt, maxSteps);
}

// In run(), after each step:
if (executionListener != null) {
    executionListener.onStepComplete(name, currentStep, stepResult);
}

// In run(), after completion/error:
if (executionListener != null) {
    executionListener.onExecutionComplete(name, agentState, finalResult, getExecutionDurationMillis());
}
```

**Step 4: Create JdbcAgentExecutionListener**

```java
package com.ron.ronaiagent.agent;

import com.ron.ronaiagent.persistence.AgentExecutionRecord;
import com.ron.ronaiagent.persistence.AgentExecutionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;

public class JdbcAgentExecutionListener implements AgentExecutionListener {
    private final AgentExecutionRepository repository;
    private final ThreadLocal<Long> currentExecutionId = new ThreadLocal<>();

    public JdbcAgentExecutionListener(AgentExecutionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onExecutionStart(String agentName, String prompt, int maxSteps) {
        AgentExecutionRecord record = AgentExecutionRecord.builder()
                .agentName(agentName)
                .prompt(prompt)
                .status("RUNNING")
                .maxSteps(maxSteps)
                .currentStep(0)
                .startTime(LocalDateTime.now())
                .build();
        Long id = repository.save(record);
        currentExecutionId.set(id);
    }

    @Override
    public void onStepComplete(String agentName, int step, String result) {
        Long id = currentExecutionId.get();
        if (id != null) {
            repository.updateStatus(id, "RUNNING", step, result);
        }
    }

    @Override
    public void onExecutionComplete(String agentName, AgentState finalState, String result, long durationMs) {
        Long id = currentExecutionId.get();
        if (id != null) {
            repository.updateStatus(id, finalState.name(), -1, result);
            currentExecutionId.remove();
        }
    }
}
```

**Step 5: Run all tests**

**Step 6: Code review + Commit**

```bash
git commit -m "feat(agent): add AgentExecutionListener for state persistence during agent lifecycle"
```

---

### Task 5.3: Wire Persistence into AIController

**Files:**
- Modify: `src/main/java/com/ron/ronaiagent/controller/AIController.java`
- Create: `src/main/java/com/ron/ronaiagent/config/PersistenceConfig.java`

**Step 1: Create PersistenceConfig**

```java
package com.ron.ronaiagent.config;

import com.ron.ronaiagent.agent.JdbcAgentExecutionListener;
import com.ron.ronaiagent.persistence.AgentExecutionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

@Configuration
public class PersistenceConfig {

    @Bean
    @Profile("prod")
    public AgentExecutionRepository agentExecutionRepository(DataSource dataSource) {
        return new AgentExecutionRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    @Profile("prod")
    public JdbcAgentExecutionListener agentExecutionListener(AgentExecutionRepository repository) {
        return new JdbcAgentExecutionListener(repository);
    }
}
```

**Step 2: Update AIController to inject and set listener**

In the RonManus creation section (line 419):

```java
RonManus ronManus = new RonManus(availableTools, dashScopeChatModel);
if (agentExecutionListener != null) {
    ronManus.setExecutionListener(agentExecutionListener);
}
```

**Step 3: Run all tests**

**Step 4: Code review + Commit**

```bash
git commit -m "feat(controller): wire agent execution persistence into AIController"
```

---

## Progress Tracker

| Phase | Task | Status | Commit | Review | Notes |
|-------|------|--------|--------|--------|-------|
| 1 | 1.1 ToolResult record | | | | |
| 1 | 1.2 FileOperationTool migration | | | | |
| 1 | 1.3 Remaining tools migration | | | | |
| 2 | 2.1 ToolExecutionRetryPolicy | | | | |
| 2 | 2.2 Retry in ToolCallAgent.act() | | | | |
| 2 | 2.3 Structured error in think() | | | | |
| 3 | 3.1 ContextWindowManager | | | | |
| 3 | 3.2 Integration into BaseAgent | | | | |
| 4 | 4.1 JdbcChatMemoryRepository | | | | |
| 4 | 4.2 Profile-based memory config | | | | |
| 5 | 5.1 AgentExecution entity | | | | |
| 5 | 5.2 Listener integration | | | | |
| 5 | 5.3 Controller wiring | | | | |

---

## Dependencies

```
Phase 1 (ToolResult) ──→ Phase 2 (Retry, uses ToolResult error format)
Phase 2 (Retry) ──────→ Phase 3 (Context, independent but benefits from retry)
Phase 3 (Context) ────→ Phase 4 (Memory, context compaction prepares for memory)
Phase 4 (Memory) ─────→ Phase 5 (Persistence, memory layer depends on schema)
```

Phases 1-3 can be partially parallelized:
- Phase 1 is fully independent
- Phase 2 depends only on Task 1.1 (ToolResult)
- Phase 3 is fully independent

---

## Rollback Strategy

Each task is a single commit. Rollback with:

```bash
git revert <commit-hash>
```

No task modifies database schema in a way that requires migration rollback — all new tables use `CREATE TABLE IF NOT EXISTS`.
