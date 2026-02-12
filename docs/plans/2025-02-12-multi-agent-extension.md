# Multi-Agent System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extend the current single-agent ron-ai-agent system to support multi-agent collaboration with specialized agents, inter-agent communication, and flexible coordination patterns.

**Architecture:** Layered extension preserving backward compatibility - add AgentManager for lifecycle management, MessageBus for inter-agent communication, and TaskCoordinator for collaboration orchestration (master-worker, chain, parallel patterns).

**Tech Stack:** Spring Boot 3.5.5, Spring AI, DashScope, existing PostgreSQL + PgVector, Java 21

---

## Table of Contents
1. [Phase 1: Foundation - Communication Layer](#phase-1-foundation---communication-layer)
2. [Phase 2: Agent Management](#phase-2-agent-management)
3. [Phase 3: Specialized Agents](#phase-3-specialized-agents)
4. [Phase 4: Coordination Patterns](#phase-4-coordination-patterns)
5. [Phase 5: Integration and Testing](#phase-5-integration-and-testing)

---

## Phase 1: Foundation - Communication Layer

### Task 1.1: Create Message Data Model

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/agent/communication/Message.java`
- Create: `src/test/java/com/ron/ronaiagent/agent/communication/MessageTest.java`

**Step 1: Write the failing test**

Create `src/test/java/com/ron/ronaiagent/agent/communication/MessageTest.java`:

```java
package com.ron.ronaiagent.agent.communication;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Map;

class MessageTest {
    @Test
    void testMessageCreation() {
        Message message = new Message(
            "agent1",
            "agent2",
            "test-topic",
            "Test content",
            Map.of("key", "value")
        );

        assertEquals("agent1", message.getFrom());
        assertEquals("agent2", message.getTo());
        assertEquals("test-topic", message.getTopic());
        assertEquals("Test content", message.getContent());
        assertFalse(message.isBroadcast());
        assertNotNull(message.getTimestamp());
    }

    @Test
    void testBroadcastMessage() {
        Message message = Message.createBroadcast(
            "agent1",
            "test-topic",
            "Broadcast content"
        );

        assertEquals("agent1", message.getFrom());
        assertTrue(message.isBroadcast());
        assertEquals("test-topic", message.getTopic());
    }

    @Test
    void testMessageWithReplyTo() {
        Message original = new Message("agent1", "agent2", "topic", "content");
        Message reply = original.createReply("Reply content");

        assertEquals("agent2", reply.getFrom());
        assertEquals("agent1", reply.getTo());
        assertEquals("topic", reply.getTopic());
        assertEquals("Reply content", reply.getContent());
        assertTrue(reply.isReply());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=MessageTest`

Expected: FAIL with "Message class not found"

**Step 3: Write minimal implementation**

Create `src/main/java/com/ron/ronaiagent/agent/communication/Message.java`:

```java
package com.ron.ronaiagent.agent.communication;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class Message {
    private final String id;
    private final String from;
    private final String to;
    private final String topic;
    private final String content;
    private final Map<String, Object> metadata;
    private final LocalDateTime timestamp;
    private final boolean broadcast;
    private final boolean reply;
    private final String replyToMessageId;

    private Message(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.from = builder.from;
        this.to = builder.to;
        this.topic = builder.topic;
        this.content = builder.content;
        this.metadata = builder.metadata;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.broadcast = builder.broadcast;
        this.reply = builder.reply;
        this.replyToMessageId = builder.replyToMessageId;
    }

    public static Message createBroadcast(String from, String topic, String content) {
        return new Builder()
            .from(from)
            .topic(topic)
            .content(content)
            .broadcast(true)
            .build();
    }

    public Message createReply(String replyContent) {
        return new Builder()
            .from(this.to)
            .to(this.from)
            .topic(this.topic)
            .content(replyContent)
            .reply(true)
            .replyToMessageId(this.id)
            .build();
    }

    // Getters
    public String getId() { return id; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getTopic() { return topic; }
    public String getContent() { return content; }
    public Map<String, Object> getMetadata() { return metadata; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public boolean isBroadcast() { return broadcast; }
    public boolean isReply() { return reply; }
    public String getReplyToMessageId() { return replyToMessageId; }

    public static class Builder {
        private String id;
        private String from;
        private String to;
        private String topic;
        private String content;
        private Map<String, Object> metadata;
        private LocalDateTime timestamp;
        private boolean broadcast;
        private boolean reply;
        private String replyToMessageId;

        public Builder id(String id) { this.id = id; return this; }
        public Builder from(String from) { this.from = from; return this; }
        public Builder to(String to) { this.to = to; return this; }
        public Builder topic(String topic) { this.topic = topic; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public Builder broadcast(boolean broadcast) { this.broadcast = broadcast; return this; }
        public Builder reply(boolean reply) { this.reply = reply; return this; }
        public Builder replyToMessageId(String replyToMessageId) { this.replyToMessageId = replyToMessageId; return this; }

        public Message build() {
            if (from == null) throw new IllegalArgumentException("from is required");
            if (topic == null) throw new IllegalArgumentException("topic is required");
            if (content == null) throw new IllegalArgumentException("content is required");
            return new Message(this);
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=MessageTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/agent/communication/Message.java
git add src/test/java/com/ron/ronaiagent/agent/communication/MessageTest.java
git commit -m "feat(communication): add message data model for inter-agent communication"
```

---

### Task 1.2: Create MessageBus Interface

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/agent/communication/MessageBus.java`

**Step 1: Write the interface**

Create `src/main/java/com/ron/ronaiagent/agent/communication/MessageBus.java`:

```java
package com.ron.ronaiagent.agent.communication;

import java.util.List;
import java.util.function.Consumer;

public interface MessageBus {
    /**
     * Publish a message to a specific topic
     */
    void publish(String topic, Message message);

    /**
     * Subscribe to a topic with a message handler
     * @return subscription ID for unsubscribing
     */
    String subscribe(String topic, String subscriberId, Consumer<Message> handler);

    /**
     * Unsubscribe from a topic
     */
    void unsubscribe(String subscriptionId);

    /**
     * Send a direct message from one agent to another
     */
    void sendDirect(Message message);

    /**
     * Broadcast a message to all subscribers of a topic
     */
    void broadcast(String topic, Message message);

    /**
     * Get all messages for a specific agent (queue-based)
     */
    List<Message> getMessagesForAgent(String agentId);

    /**
     * Clear message queue for an agent
     */
    void clearMessagesForAgent(String agentId);

    /**
     * Get message history for a topic (for debugging/audit)
     */
    List<Message> getMessageHistory(String topic, int limit);
}
```

**Step 2: Run verification**

Run: `mvn compile`

Expected: SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/agent/communication/MessageBus.java
git commit -m "feat(communication): add MessageBus interface for inter-agent communication"
```

---

### Task 1.3: Implement InMemory MessageBus

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/agent/communication/InMemoryMessageBus.java`
- Create: `src/test/java/com/ron/ronaiagent/agent/communication/InMemoryMessageBusTest.java`

**Step 1: Write the failing test**

Create `src/test/java/com/ron/ronaiagent/agent/communication/InMemoryMessageBusTest.java`:

```java
package com.ron.ronaiagent.agent.communication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class InMemoryMessageBusTest {
    private MessageBus messageBus;

    @BeforeEach
    void setUp() {
        messageBus = new InMemoryMessageBus();
    }

    @Test
    void testPublishAndSubscribe() {
        List<Message> receivedMessages = new ArrayList<>();

        String subscriptionId = messageBus.subscribe(
            "test-topic",
            "agent1",
            message -> receivedMessages.add(message)
        );

        Message message = new Message.Builder()
            .from("agent2")
            .to("agent1")
            .topic("test-topic")
            .content("Test message")
            .build();

        messageBus.publish("test-topic", message);

        assertEquals(1, receivedMessages.size());
        assertEquals("Test message", receivedMessages.get(0).getContent());
    }

    @Test
    void testDirectMessage() {
        List<Message> receivedMessages = new ArrayList<>();

        messageBus.subscribe("direct", "agent1", msg -> receivedMessages.add(msg));

        Message message = new Message.Builder()
            .from("agent2")
            .to("agent1")
            .topic("direct")
            .content("Direct message")
            .build();

        messageBus.sendDirect(message);

        assertEquals(1, receivedMessages.size());
        assertEquals("agent1", receivedMessages.get(0).getTo());
    }

    @Test
    void testBroadcast() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger agent1Count = new AtomicInteger(0);
        AtomicInteger agent2Count = new AtomicInteger(0);

        messageBus.subscribe("broadcast-topic", "agent1", msg -> {
            agent1Count.incrementAndGet();
            latch.countDown();
        });

        messageBus.subscribe("broadcast-topic", "agent2", msg -> {
            agent2Count.incrementAndGet();
            latch.countDown();
        });

        Message broadcastMsg = Message.createBroadcast(
            "broadcaster",
            "broadcast-topic",
            "Broadcast to all"
        );

        messageBus.broadcast("broadcast-topic", broadcastMsg);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1, agent1Count.get());
        assertEquals(1, agent2Count.get());
    }

    @Test
    void testUnsubscribe() {
        List<Message> receivedMessages = new ArrayList<>();

        String subscriptionId = messageBus.subscribe(
            "test-topic",
            "agent1",
            message -> receivedMessages.add(message)
        );

        Message msg1 = new Message.Builder()
            .from("agent2").to("agent1").topic("test-topic").content("msg1").build();
        messageBus.publish("test-topic", msg1);

        messageBus.unsubscribe(subscriptionId);

        Message msg2 = new Message.Builder()
            .from("agent2").to("agent1").topic("test-topic").content("msg2").build();
        messageBus.publish("test-topic", msg2);

        assertEquals(1, receivedMessages.size());
        assertEquals("msg1", receivedMessages.get(0).getContent());
    }

    @Test
    void testMessageQueueForAgent() {
        messageBus.sendDirect(new Message.Builder()
            .from("agent2").to("agent1").topic("direct").content("msg1").build());
        messageBus.sendDirect(new Message.Builder()
            .from("agent3").to("agent1").topic("direct").content("msg2").build());

        List<Message> messages = messageBus.getMessagesForAgent("agent1");

        assertEquals(2, messages.size());
        messageBus.clearMessagesForAgent("agent1");
        assertEquals(0, messageBus.getMessagesForAgent("agent1").size());
    }

    @Test
    void testMessageHistory() {
        for (int i = 0; i < 5; i++) {
            messageBus.publish("history-topic", new Message.Builder()
                .from("agent1").to("agent2").topic("history-topic").content("msg" + i).build());
        }

        List<Message> history = messageBus.getMessageHistory("history-topic", 3);
        assertEquals(3, history.size());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=InMemoryMessageBusTest`

Expected: FAIL with "InMemoryMessageBus class not found"

**Step 3: Write minimal implementation**

Create `src/main/java/com/ron/ronaiagent/agent/communication/InMemoryMessageBus.java`:

```java
package com.ron.ronaiagent.agent.communication;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
public class InMemoryMessageBus implements MessageBus {

    private final Map<String, List<Subscription>> topicSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Queue<Message>> agentMessageQueues = new ConcurrentHashMap<>();
    private final Map<String, List<Message>> topicHistory = new ConcurrentHashMap<>();
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    @Override
    public void publish(String topic, Message message) {
        addToHistory(topic, message);

        List<Subscription> subscribers = topicSubscriptions.get(topic);
        if (subscribers != null) {
            subscribers.forEach(sub -> {
                if (sub.handler != null) {
                    sub.handler.accept(message);
                }
                queueMessage(sub.subscriberId, message);
            });
        }
    }

    @Override
    public String subscribe(String topic, String subscriberId, Consumer<Message> handler) {
        String subscriptionId = UUID.randomUUID().toString();
        Subscription subscription = new Subscription(subscriptionId, topic, subscriberId, handler);

        subscriptions.put(subscriptionId, subscription);
        topicSubscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
            .add(subscription);

        return subscriptionId;
    }

    @Override
    public void unsubscribe(String subscriptionId) {
        Subscription subscription = subscriptions.remove(subscriptionId);
        if (subscription != null) {
            List<Subscription> subscribers = topicSubscriptions.get(subscription.topic);
            if (subscribers != null) {
                subscribers.remove(sub);
            }
        }
    }

    @Override
    public void sendDirect(Message message) {
        String topic = message.getTopic();
        publish(topic, message);
    }

    @Override
    public void broadcast(String topic, Message message) {
        publish(topic, message);
    }

    @Override
    public List<Message> getMessagesForAgent(String agentId) {
        Queue<Message> queue = agentMessageQueues.get(agentId);
        if (queue == null) {
            return List.of();
        }
        return new ArrayList<>(queue);
    }

    @Override
    public void clearMessagesForAgent(String agentId) {
        agentMessageQueues.remove(agentId);
    }

    @Override
    public List<Message> getMessageHistory(String topic, int limit) {
        List<Message> history = topicHistory.get(topic);
        if (history == null) {
            return List.of();
        }

        int startIndex = Math.max(0, history.size() - limit);
        return new ArrayList<>(history.subList(startIndex, history.size()));
    }

    private void queueMessage(String agentId, Message message) {
        agentMessageQueues.computeIfAbsent(agentId, k -> new ConcurrentLinkedQueue<>())
            .add(message);
    }

    private void addToHistory(String topic, Message message) {
        topicHistory.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
            .add(message);
    }

    private static class Subscription {
        final String subscriptionId;
        final String topic;
        final String subscriberId;
        final Consumer<Message> handler;

        Subscription(String subscriptionId, String topic, String subscriberId, Consumer<Message> handler) {
            this.subscriptionId = subscriptionId;
            this.topic = topic;
            this.subscriberId = subscriberId;
            this.handler = handler;
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=InMemoryMessageBusTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/agent/communication/InMemoryMessageBus.java
git add src/test/java/com/ron/ronaiagent/agent/communication/InMemoryMessageBusTest.java
git commit -m "feat(communication): implement InMemoryMessageBus with pub/sub and direct messaging"
```

---

## Phase 2: Agent Management

### Task 2.1: Create AgentManager Interface

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/agent/coordinator/AgentManager.java`

**Step 1: Write the interface**

Create `src/main/java/com/ron/ronaiagent/agent/coordinator/AgentManager.java`:

```java
package com.ron.ronaiagent.agent.coordinator;

import com.ron.ronaiagent.agent.BaseAgent;
import java.util.List;
import java.util.Optional;

public interface AgentManager {
    /**
     * Register an agent with a unique name
     */
    void registerAgent(String name, BaseAgent agent);

    /**
     * Unregister an agent by name
     */
    void unregisterAgent(String name);

    /**
     * Get an agent by name
     */
    Optional<BaseAgent> getAgent(String name);

    /**
     * Get all registered agent names
     */
    List<String> getAgentNames();

    /**
     * Get all registered agents
     */
    List<BaseAgent> getAllAgents();

    /**
     * Check if an agent is registered
     */
    boolean isAgentRegistered(String name);

    /**
     * Get agent count
     */
    int getAgentCount();
}
```

**Step 2: Run verification**

Run: `mvn compile`

Expected: SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/agent/coordinator/AgentManager.java
git commit -m "feat(coordinator): add AgentManager interface for agent lifecycle management"
```

---

### Task 2.2: Implement AgentManager

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/agent/coordinator/AgentManagerImpl.java`
- Create: `src/test/java/com/ron/ronaiagent/agent/coordinator/AgentManagerImplTest.java`

**Step 1: Write the failing test**

Create `src/test/java/com/ron/ronaiagent/agent/coordinator/AgentManagerImplTest.java`:

```java
package com.ron.ronaiagent.agent.coordinator;

import com.ron.ronaiagent.agent.BaseAgent;
import com.ron.ronaiagent.agent.AgentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

class AgentManagerImplTest {
    private AgentManager agentManager;

    @BeforeEach
    void setUp() {
        agentManager = new AgentManagerImpl();
    }

    @Test
    void testRegisterAndGetAgent() {
        BaseAgent mockAgent = createMockAgent("test-agent");

        agentManager.registerAgent("agent1", mockAgent);

        Optional<BaseAgent> retrieved = agentManager.getAgent("agent1");
        assertTrue(retrieved.isPresent());
        assertEquals("agent1", retrieved.get().getAgentId());
    }

    @Test
    void testUnregisterAgent() {
        BaseAgent mockAgent = createMockAgent("agent1");
        agentManager.registerAgent("agent1", mockAgent);

        assertTrue(agentManager.isAgentRegistered("agent1"));

        agentManager.unregisterAgent("agent1");

        assertFalse(agentManager.isAgentRegistered("agent1"));
        assertTrue(agentManager.getAgent("agent1").isEmpty());
    }

    @Test
    void testGetAllAgents() {
        BaseAgent agent1 = createMockAgent("agent1");
        BaseAgent agent2 = createMockAgent("agent2");

        agentManager.registerAgent("agent1", agent1);
        agentManager.registerAgent("agent2", agent2);

        assertEquals(2, agentManager.getAgentCount());
        assertEquals(2, agentManager.getAllAgents().size());
        assertTrue(agentManager.getAgentNames().contains("agent1"));
        assertTrue(agentManager.getAgentNames().contains("agent2"));
    }

    @Test
    void testDuplicateRegistration() {
        BaseAgent agent1 = createMockAgent("agent1");
        BaseAgent agent2 = createMockAgent("agent2");

        agentManager.registerAgent("agent1", agent1);

        // Should replace the existing agent
        agentManager.registerAgent("agent1", agent2);

        Optional<BaseAgent> retrieved = agentManager.getAgent("agent1");
        assertTrue(retrieved.isPresent());
        assertEquals("agent2", retrieved.get().getAgentId());
    }

    private BaseAgent createMockAgent(String agentId) {
        return new BaseAgent(agentId, null) {
            @Override
            protected void step() {
                // Mock implementation
            }
        };
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AgentManagerImplTest`

Expected: FAIL with "AgentManagerImpl class not found"

**Step 3: Write minimal implementation**

Create `src/main/java/com/ron/ronaiagent/agent/coordinator/AgentManagerImpl.java`:

```java
package com.ron.ronaiagent.agent.coordinator;

import com.ron.ronaiagent.agent.BaseAgent;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentManagerImpl implements AgentManager {

    private final Map<String, BaseAgent> agents = new ConcurrentHashMap<>();

    @Override
    public void registerAgent(String name, BaseAgent agent) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent name cannot be null or empty");
        }
        if (agent == null) {
            throw new IllegalArgumentException("Agent cannot be null");
        }
        agents.put(name, agent);
    }

    @Override
    public void unregisterAgent(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Agent name cannot be null");
        }
        agents.remove(name);
    }

    @Override
    public Optional<BaseAgent> getAgent(String name) {
        return Optional.ofNullable(agents.get(name));
    }

    @Override
    public List<String> getAgentNames() {
        return new ArrayList<>(agents.keySet());
    }

    @Override
    public List<BaseAgent> getAllAgents() {
        return new ArrayList<>(agents.values());
    }

    @Override
    public boolean isAgentRegistered(String name) {
        return agents.containsKey(name);
    }

    @Override
    public int getAgentCount() {
        return agents.size();
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AgentManagerImplTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/agent/coordinator/AgentManagerImpl.java
git add src/test/java/com/ron/ronaiagent/agent/coordinator/AgentManagerImplTest.java
git commit -m "feat(coordinator): implement AgentManager with thread-safe agent registry"
```

---

### Task 2.3: Create Collaboration Pattern Enum

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/agent/coordinator/CollaborationPattern.java`

**Step 1: Write the enum**

Create `src/main/java/com/ron/ronaiagent/agent/coordinator/CollaborationPattern.java`:

```java
package com.ron.ronaiagent.agent.coordinator;

public enum CollaborationPattern {
    /**
     * Master-Worker: One coordinator agent delegates tasks to specialized worker agents
     */
    MASTER_WORKER,

    /**
     * Chain: Agents execute sequentially, each building on the previous agent's output
     */
    CHAIN,

    /**
     * Parallel: Multiple agents work independently on different aspects of a task
     */
    PARALLEL,

    /**
     * Hierarchy: High-level planning agents coordinate lower-level execution agents
     */
    HIERARCHY,

    /**
     * RoundRobin: Task distributed equally among agents in rotation
     */
    ROUND_ROBIN
}
```

**Step 2: Run verification**

Run: `mvn compile`

Expected: SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/agent/coordinator/CollaborationPattern.java
git commit -m "feat(coordinator): add CollaborationPattern enum for multi-agent coordination strategies"
```

---

## Phase 3: Specialized Agents

### Task 3.1: Create FileProcessingAgent

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/agent/specialized/FileProcessingAgent.java`
- Create: `src/test/java/com/ron/ronaiagent/agent/specialized/FileProcessingAgentTest.java`

**Step 1: Write the failing test**

Create `src/test/java/com/ron/ronaiagent/agent/specialized/FileProcessingAgentTest.java`:

```java
package com.ron.ronaiagent.agent.specialized;

import com.ron.ronaiagent.agent.AgentState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FileProcessingAgentTest {
    @Test
    void testFileProcessingAgentCreation() {
        FileProcessingAgent agent = new FileProcessingAgent(
            "file-processor",
            null,
            "/tmp/test"
        );

        assertEquals("file-processor", agent.getAgentId());
        assertEquals(AgentState.IDLE, agent.getState());
    }

    @Test
    void testFileProcessingAgentHasCorrectTools() {
        FileProcessingAgent agent = new FileProcessingAgent(
            "file-processor",
            null,
            "/tmp/test"
        );

        assertNotNull(agent.getAvailableTools());
        // Should have file operation tools
        assertTrue(agent.getAvailableTools().length > 0);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=FileProcessingAgentTest`

Expected: FAIL with "FileProcessingAgent class not found"

**Step 3: Write minimal implementation**

First, read the existing ToolCallAgent to understand the pattern:

```bash
# Let me check if ToolCallAgent exists and understand its structure
```

Create `src/main/java/com/ron/ronaiagent/agent/specialized/FileProcessingAgent.java`:

```java
package com.ron.ronaiagent.agent.specialized;

import com.ron.ronaiagent.agent.ToolCallAgent;
import com.ron.ronaiagent.chat.tools.FileOperationTool;
import com.ron.ronaiagent.chat.tools.PDFGenerationTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.stereotype.Component;

@Component
public class FileProcessingAgent extends ToolCallAgent {

    private static final String SYSTEM_PROMPT = """
        You are a File Processing Agent specialized in handling file operations.

        Your capabilities include:
        - Reading and writing files
        - Creating PDF documents
        - Managing directory structures
        - File format conversions

        Always verify file paths and ensure safe operations.
        When tasks involve sensitive operations, explain what you're doing before executing.
        """;

    private final String baseDirectory;

    public FileProcessingAgent(String agentId, ChatModel chatModel, String baseDirectory) {
        super(agentId, chatModel);
        this.baseDirectory = baseDirectory;
        configureAgent();
    }

    private void configureAgent() {
        // Set system prompt
        setSystemPrompt(SYSTEM_PROMPT);

        // Set next step prompt
        setNextPrompt("Continue processing the file operation. Think carefully about file safety.");

        // Configure tools for file operations
        // Tools will be injected via ToolRegistration
    }

    public String getBaseDirectory() {
        return baseDirectory;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=FileProcessingAgentTest`

Expected: PASS (may need to adjust based on actual ToolCallAgent implementation)

**Step 5: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/agent/specialized/FileProcessingAgent.java
git add src/test/java/com/ron/ronaiagent/agent/specialized/FileProcessingAgentTest.java
git commit -m "feat(agent): add FileProcessingAgent for specialized file operations"
```

---

### Task 3.2: Create SearchAgent

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/agent/specialized/SearchAgent.java`
- Create: `src/test/java/com/ron/ronaiagent/agent/specialized/SearchAgentTest.java`

**Step 1: Write the failing test**

Create `src/test/java/com/ron/ronaiagent/agent/specialized/SearchAgentTest.java`:

```java
package com.ron.ronaiagent.agent.specialized;

import com.ron.ronaiagent.agent.AgentState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SearchAgentTest {
    @Test
    void testSearchAgentCreation() {
        SearchAgent agent = new SearchAgent(
            "search-agent",
            null
        );

        assertEquals("search-agent", agent.getAgentId());
        assertEquals(AgentState.IDLE, agent.getState());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SearchAgentTest`

Expected: FAIL with "SearchAgent class not found"

**Step 3: Write minimal implementation**

Create `src/main/java/com/ron/ronaiagent/agent/specialized/SearchAgent.java`:

```java
package com.ron.ronaiagent.agent.specialized;

import com.ron.ronaiagent.agent.ToolCallAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Component
public class SearchAgent extends ToolCallAgent {

    private static final String SYSTEM_PROMPT = """
        You are a Search Agent specialized in information retrieval.

        Your capabilities include:
        - Web search for current information
        - Web scraping for content extraction
        - Resource downloads
        - Search result analysis and summarization

        Always provide citations and sources for search results.
        When information is not found, clearly state that and suggest alternatives.
        """;

    public SearchAgent(String agentId, ChatModel chatModel) {
        super(agentId, chatModel);
        configureAgent();
    }

    private void configureAgent() {
        setSystemPrompt(SYSTEM_PROMPT);
        setNextPrompt("Continue searching and gathering information. Verify sources and summarize findings.");
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SearchAgentTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/agent/specialized/SearchAgent.java
git add src/test/java/com/ron/ronaiagent/agent/specialized/SearchAgentTest.java
git commit -m "feat(agent): add SearchAgent for web search and content retrieval"
```

---

### Task 3.3: Create AnalysisAgent

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/agent/specialized/AnalysisAgent.java`
- Create: `src/test/java/com/ron/ronaiagent/agent/specialized/AnalysisAgentTest.java`

**Step 1: Write the failing test**

Create `src/test/java/com/ron/ronaiagent/agent/specialized/AnalysisAgentTest.java`:

```java
package com.ron.ronaiagent.agent.specialized;

import com.ron.ronaiagent.agent.AgentState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisAgentTest {
    @Test
    void testAnalysisAgentCreation() {
        AnalysisAgent agent = new AnalysisAgent(
            "analysis-agent",
            null
        );

        assertEquals("analysis-agent", agent.getAgentId());
        assertEquals(AgentState.IDLE, agent.getState());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AnalysisAgentTest`

Expected: FAIL with "AnalysisAgent class not found"

**Step 3: Write minimal implementation**

Create `src/main/java/com/ron/ronaiagent/agent/specialized/AnalysisAgent.java`:

```java
package com.ron.ronaiagent.agent.specialized;

import com.ron.ronaiagent.agent.ToolCallAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Component
public class AnalysisAgent extends ToolCallAgent {

    private static final String SYSTEM_PROMPT = """
        You are an Analysis Agent specialized in data analysis and insights generation.

        Your capabilities include:
        - Data pattern recognition
        - Statistical analysis
        - Trend identification
        - Insight generation from structured and unstructured data
        - Report generation

        Always:
        - Explain your analysis methodology
        - Highlight confidence levels in conclusions
        - Identify limitations in the data
        - Suggest follow-up analyses when relevant
        """;

    public AnalysisAgent(String agentId, ChatModel chatModel) {
        super(agentId, chatModel);
        configureAgent();
    }

    private void configureAgent() {
        setSystemPrompt(SYSTEM_PROMPT);
        setNextPrompt("Continue the analysis. Provide deeper insights and verify conclusions.");
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AnalysisAgentTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/agent/specialized/AnalysisAgent.java
git add src/test/java/com/ron/ronaiagent/agent/specialized/AnalysisAgentTest.java
git commit -m "feat(agent): add AnalysisAgent for data analysis and insights"
```

---

### Task 3.4: Create CoordinatorAgent

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/agent/specialized/CoordinatorAgent.java`
- Create: `src/test/java/com/ron/ronaiagent/agent/specialized/CoordinatorAgentTest.java`

**Step 1: Write the failing test**

Create `src/test/java/com/ron/ronaiagent/agent/specialized/CoordinatorAgentTest.java`:

```java
package com.ron.ronaiagent.agent.specialized;

import com.ron.ronaiagent.agent.AgentState;
import com.ron.ronaiagent.agent.coordinator.AgentManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoordinatorAgentTest {
    @Test
    void testCoordinatorAgentCreation() {
        AgentManager mockManager = new AgentManager() {
            @Override public void registerAgent(String name, com.ron.ronaiagent.agent.BaseAgent agent) {}
            @Override public void unregisterAgent(String name) {}
            @Override public java.util.Optional<com.ron.ronaiagent.agent.BaseAgent> getAgent(String name) { return java.util.Optional.empty(); }
            @Override public java.util.List<String> getAgentNames() { return List.of(); }
            @Override public java.util.List<com.ron.ronaiagent.agent.BaseAgent> getAllAgents() { return List.of(); }
            @Override public boolean isAgentRegistered(String name) { return false; }
            @Override public int getAgentCount() { return 0; }
        };

        CoordinatorAgent agent = new CoordinatorAgent(
            "coordinator",
            null,
            mockManager
        );

        assertEquals("coordinator", agent.getAgentId());
        assertEquals(AgentState.IDLE, agent.getState());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=CoordinatorAgentTest`

Expected: FAIL with "CoordinatorAgent class not found"

**Step 3: Write minimal implementation**

Create `src/main/java/com/ron/ronaiagent/agent/specialized/CoordinatorAgent.java`:

```java
package com.ron.ronaiagent.agent.specialized;

import com.ron.ronaiagent.agent.ToolCallAgent;
import com.ron.ronaiagent.agent.coordinator.AgentManager;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Component
public class CoordinatorAgent extends ToolCallAgent {

    private static final String SYSTEM_PROMPT = """
        You are a Coordinator Agent responsible for task distribution and result aggregation.

        Your responsibilities include:
        - Understanding complex user requirements
        - Breaking down tasks into subtasks
        - Delegating subtasks to appropriate specialized agents
        - Aggregating results from multiple agents
        - Ensuring quality and coherence of final output

        Available coordination patterns:
        - Master-Worker: Delegate to specialized workers
        - Chain: Sequential processing with handoffs
        - Parallel: Distribute independent subtasks

        Always:
        - Clearly communicate the task breakdown
        - Explain why specific agents are chosen
        - Synthesize results into coherent responses
        - Handle agent failures gracefully
        """;

    private final AgentManager agentManager;

    public CoordinatorAgent(String agentId, ChatModel chatModel, AgentManager agentManager) {
        super(agentId, chatModel);
        this.agentManager = agentManager;
        configureAgent();
    }

    private void configureAgent() {
        setSystemPrompt(SYSTEM_PROMPT);
        setNextPrompt("Continue coordinating the task. Monitor agent progress and aggregate results.");
    }

    public AgentManager getAgentManager() {
        return agentManager;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=CoordinatorAgentTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/agent/specialized/CoordinatorAgent.java
git add src/test/java/com/ron/ronaiagent/agent/specialized/CoordinatorAgentTest.java
git commit -m "feat(agent): add CoordinatorAgent for task distribution and result aggregation"
```

---

## Phase 4: Coordination Patterns

### Task 4.1: Create Task Coordinator Interface

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/agent/coordinator/TaskCoordinator.java`
- Create: `src/main/java/com/ron/ronaiagent/agent/coordinator/CoordinationResult.java`

**Step 1: Write the interfaces**

Create `src/main/java/com/ron/ronaiagent/agent/coordinator/CoordinationResult.java`:

```java
package com.ron.ronaiagent.agent.coordinator;

import java.time.LocalDateTime;
import java.util.Map;

public class CoordinationResult {
    private final boolean success;
    private final String finalOutput;
    private final Map<String, String> agentOutputs;
    private final long executionTimeMs;
    private final LocalDateTime timestamp;
    private final String errorMessage;

    private CoordinationResult(Builder builder) {
        this.success = builder.success;
        this.finalOutput = builder.finalOutput;
        this.agentOutputs = builder.agentOutputs;
        this.executionTimeMs = builder.executionTimeMs;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.errorMessage = builder.errorMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getFinalOutput() { return finalOutput; }
    public Map<String, String> getAgentOutputs() { return agentOutputs; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getErrorMessage() { return errorMessage; }

    public static class Builder {
        private boolean success = true;
        private String finalOutput;
        private Map<String, String> agentOutputs = Map.of();
        private long executionTimeMs;
        private LocalDateTime timestamp;
        private String errorMessage;

        public Builder success(boolean success) { this.success = success; return this; }
        public Builder finalOutput(String finalOutput) { this.finalOutput = finalOutput; return this; }
        public Builder agentOutputs(Map<String, String> agentOutputs) {
            this.agentOutputs = agentOutputs; return this;
        }
        public Builder executionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; return this; }
        public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public CoordinationResult build() {
            return new CoordinationResult(this);
        }
    }
}
```

Create `src/main/java/com/ron/ronaiagent/agent/coordinator/TaskCoordinator.java`:

```java
package com.ron.ronaiagent.agent.coordinator;

import java.util.List;

public interface TaskCoordinator {
    /**
     * Execute a task using the master-worker pattern
     * @param taskDescription The task to execute
     * @param masterAgentId The coordinator agent
     * @param workerAgentIds The worker agents
     * @return Coordination result
     */
    CoordinationResult executeMasterWorker(
        String taskDescription,
        String masterAgentId,
        List<String> workerAgentIds
    );

    /**
     * Execute a task using the chain pattern (sequential)
     * @param taskDescription The task to execute
     * @param agentIds Agents in execution order
     * @return Coordination result
     */
    CoordinationResult executeChain(
        String taskDescription,
        List<String> agentIds
    );

    /**
     * Execute a task using the parallel pattern
     * @param taskDescription The task to execute
     * @param agentIds Agents to execute in parallel
     * @return Coordination result
     */
    CoordinationResult executeParallel(
        String taskDescription,
        List<String> agentIds
    );

    /**
     * Execute a task with a custom pattern
     */
    CoordinationResult execute(
        String taskDescription,
        CollaborationPattern pattern,
        List<String> agentIds
    );
}
```

**Step 2: Run verification**

Run: `mvn compile`

Expected: SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/agent/coordinator/TaskCoordinator.java
git add src/main/java/com/ron/ronaiagent/agent/coordinator/CoordinationResult.java
git commit -m "feat(coordinator): add TaskCoordinator interface and CoordinationResult"
```

---

### Task 4.2: Implement TaskCoordinator

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/agent/coordinator/TaskCoordinatorImpl.java`
- Create: `src/test/java/com/ron/ronaiagent/agent/coordinator/TaskCoordinatorImplTest.java`

**Step 1: Write the failing test**

Create `src/test/java/com/ron/ronaiagent/agent/coordinator/TaskCoordinatorImplTest.java`:

```java
package com.ron.ronaiagent.agent.coordinator;

import com.ron.ronaiagent.agent.BaseAgent;
import com.ron.ronaiagent.agent.AgentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

class TaskCoordinatorImplTest {
    private TaskCoordinator taskCoordinator;
    private AgentManager agentManager;

    @BeforeEach
    void setUp() {
        agentManager = new AgentManagerImpl();
        taskCoordinator = new TaskCoordinatorImpl(agentManager);

        // Register mock agents
        BaseAgent agent1 = createMockAgent("agent1", "Result from agent1");
        BaseAgent agent2 = createMockAgent("agent2", "Result from agent2");
        BaseAgent coordinator = createMockAgent("coordinator", "Coordinated: agent1 + agent2");

        agentManager.registerAgent("agent1", agent1);
        agentManager.registerAgent("agent2", agent2);
        agentManager.registerAgent("coordinator", coordinator);
    }

    @Test
    void testChainExecution() {
        CoordinationResult result = taskCoordinator.executeChain(
            "Test task",
            List.of("agent1", "agent2")
        );

        assertTrue(result.isSuccess());
        assertEquals(2, result.getAgentOutputs().size());
        assertTrue(result.getAgentOutputs().containsKey("agent1"));
        assertTrue(result.getAgentOutputs().containsKey("agent2"));
    }

    @Test
    void testNonExistentAgent() {
        CoordinationResult result = taskCoordinator.executeChain(
            "Test task",
            List.of("nonexistent")
        );

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    private BaseAgent createMockAgent(String agentId, String response) {
        return new BaseAgent(agentId, null) {
            @Override
            protected void step() {
                // Mock step implementation
            }

            @Override
            public void run(String input) {
                setState(AgentState.FINISHED);
                // Store response in messages
            }
        };
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=TaskCoordinatorImplTest`

Expected: FAIL with "TaskCoordinatorImpl class not found"

**Step 3: Write minimal implementation**

Create `src/main/java/com/ron/ronaiagent/agent/coordinator/TaskCoordinatorImpl.java`:

```java
package com.ron.ronaiagent.agent.coordinator;

import com.ron.ronaiagent.agent.BaseAgent;
import com.ron.ronaiagent.agent.AgentState;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
public class TaskCoordinatorImpl implements TaskCoordinator {

    private final AgentManager agentManager;
    private final ExecutorService executorService;

    public TaskCoordinatorImpl(AgentManager agentManager) {
        this.agentManager = agentManager;
        this.executorService = Executors.newFixedThreadPool(10);
    }

    @Override
    public CoordinationResult executeMasterWorker(
        String taskDescription,
        String masterAgentId,
        List<String> workerAgentIds
    ) {
        long startTime = System.currentTimeMillis();

        try {
            // Master delegates to workers and aggregates results
            Map<String, String> workerResults = new HashMap<>();

            // Execute workers in parallel
            List<CompletableFuture<Void>> futures = workerAgentIds.stream()
                .map(workerId -> CompletableFuture.runAsync(() -> {
                    agentManager.getAgent(workerId).ifPresent(agent -> {
                        agent.run(taskDescription);
                        workerResults.put(workerId, extractAgentOutput(agent));
                    });
                }, executorService))
                .toList();

            // Wait for all workers to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Master aggregates results
            String aggregatedResult = aggregateResults(workerResults);

            return CoordinationResult.builder()
                .success(true)
                .finalOutput(aggregatedResult)
                .agentOutputs(workerResults)
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();

        } catch (Exception e) {
            return CoordinationResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    @Override
    public CoordinationResult executeChain(String taskDescription, List<String> agentIds) {
        long startTime = System.currentTimeMillis();
        Map<String, String> agentOutputs = new LinkedHashMap<>();

        try {
            String currentTask = taskDescription;

            for (String agentId : agentIds) {
                Optional<BaseAgent> agentOpt = agentManager.getAgent(agentId);

                if (agentOpt.isEmpty()) {
                    return CoordinationResult.builder()
                        .success(false)
                        .errorMessage("Agent not found: " + agentId)
                        .agentOutputs(agentOutputs)
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .build();
                }

                BaseAgent agent = agentOpt.get();
                agent.run(currentTask);

                String output = extractAgentOutput(agent);
                agentOutputs.put(agentId, output);

                // Pass output to next agent
                currentTask = output;
            }

            String finalOutput = agentOutputs.isEmpty() ? "" :
                agentOutputs.get(agentIds.get(agentIds.size() - 1));

            return CoordinationResult.builder()
                .success(true)
                .finalOutput(finalOutput)
                .agentOutputs(agentOutputs)
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();

        } catch (Exception e) {
            return CoordinationResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .agentOutputs(agentOutputs)
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    @Override
    public CoordinationResult executeParallel(String taskDescription, List<String> agentIds) {
        long startTime = System.currentTimeMillis();
        Map<String, String> agentOutputs = new ConcurrentHashMap<>();

        try {
            List<CompletableFuture<Void>> futures = agentIds.stream()
                .map(agentId -> CompletableFuture.runAsync(() -> {
                    agentManager.getAgent(agentId).ifPresent(agent -> {
                        agent.run(taskDescription);
                        agentOutputs.put(agentId, extractAgentOutput(agent));
                    });
                }, executorService))
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            String aggregatedResult = aggregateResults(agentOutputs);

            return CoordinationResult.builder()
                .success(true)
                .finalOutput(aggregatedResult)
                .agentOutputs(agentOutputs)
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();

        } catch (Exception e) {
            return CoordinationResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .agentOutputs(agentOutputs)
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    @Override
    public CoordinationResult execute(
        String taskDescription,
        CollaborationPattern pattern,
        List<String> agentIds
    ) {
        return switch (pattern) {
            case MASTER_WORKER -> {
                String masterId = agentIds.isEmpty() ? null : agentIds.get(0);
                List<String> workers = agentIds.size() > 1 ? agentIds.subList(1, agentIds.size()) : List.of();
                yield executeMasterWorker(taskDescription, masterId, workers);
            }
            case CHAIN -> executeChain(taskDescription, agentIds);
            case PARALLEL -> executeParallel(taskDescription, agentIds);
            default -> CoordinationResult.builder()
                .success(false)
                .errorMessage("Pattern not implemented: " + pattern)
                .build();
        };
    }

    private String extractAgentOutput(BaseAgent agent) {
        // Get last message from agent
        List<String> messages = agent.getMessages();
        if (messages.isEmpty()) {
            return "No output";
        }
        return messages.get(messages.size() - 1);
    }

    private String aggregateResults(Map<String, String> results) {
        if (results.isEmpty()) {
            return "No results";
        }

        return results.entrySet().stream()
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .collect(Collectors.joining("\n\n"));
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=TaskCoordinatorImplTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/agent/coordinator/TaskCoordinatorImpl.java
git add src/test/java/com/ron/ronaiagent/agent/coordinator/TaskCoordinatorImplTest.java
git commit -m "feat(coordinator): implement TaskCoordinator with chain and parallel patterns"
```

---

## Phase 5: Integration and Testing

### Task 5.1: Create Multi-Agent Configuration

**Files:**
- Create: `src/main/java/com/ron/ronaiagent/config/MultiAgentConfig.java`

**Step 1: Write the configuration**

Create `src/main/java/com/ron/ronaiagent/config/MultiAgentConfig.java`:

```java
package com.ron.ronaiagent.config;

import com.ron.ronaiagent.agent.coordinator.AgentManager;
import com.ron.ronaiagent.agent.coordinator.AgentManagerImpl;
import com.ron.ronaiagent.agent.coordinator.TaskCoordinator;
import com.ron.ronaiagent.agent.coordinator.TaskCoordinatorImpl;
import com.ron.ronaiagent.agent.communication.InMemoryMessageBus;
import com.ron.ronaiagent.agent.communication.MessageBus;
import com.ron.ronaiagent.agent.specialized.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultiAgentConfig {

    @Value("${agent.file.base-directory:/tmp}")
    private String fileBaseDirectory;

    // Message Bus
    @Bean
    public MessageBus messageBus() {
        return new InMemoryMessageBus();
    }

    // Agent Manager
    @Bean
    public AgentManager agentManager() {
        return new AgentManagerImpl();
    }

    // Task Coordinator
    @Bean
    public TaskCoordinator taskCoordinator(AgentManager agentManager) {
        return new TaskCoordinatorImpl(agentManager);
    }

    // Specialized Agents
    @Bean
    public FileProcessingAgent fileProcessingAgent(ChatModel chatModel) {
        return new FileProcessingAgent("file-processor", chatModel, fileBaseDirectory);
    }

    @Bean
    public SearchAgent searchAgent(ChatModel chatModel) {
        return new SearchAgent("search-agent", chatModel);
    }

    @Bean
    public AnalysisAgent analysisAgent(ChatModel chatModel) {
        return new AnalysisAgent("analysis-agent", chatModel);
    }

    @Bean
    public CoordinatorAgent coordinatorAgent(
        ChatModel chatModel,
        AgentManager agentManager
    ) {
        return new CoordinatorAgent("coordinator", chatModel, agentManager);
    }
}
```

**Step 2: Run verification**

Run: `mvn compile`

Expected: SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/com/ron/ronaiagent/config/MultiAgentConfig.java
git commit -m "feat(config): add MultiAgentConfig for automatic agent registration"
```

---

### Task 5.2: Create Integration Test

**Files:**
- Create: `src/test/java/com/ron/ronaiagent/integration/MultiAgentIntegrationTest.java`

**Step 1: Write the integration test**

Create `src/test/java/com/ron/ronaiagent/integration/MultiAgentIntegrationTest.java`:

```java
package com.ron.ronaiagent.integration;

import com.ron.ronaiagent.agent.coordinator.AgentManager;
import com.ron.ronaiagent.agent.coordinator.CollaborationPattern;
import com.ron.ronaiagent.agent.coordinator.TaskCoordinator;
import com.ron.ronaiagent.agent.communication.Message;
import com.ron.ronaiagent.agent.communication.MessageBus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MultiAgentIntegrationTest {

    @Autowired
    private AgentManager agentManager;

    @Autowired
    private TaskCoordinator taskCoordinator;

    @Autowired
    private MessageBus messageBus;

    @Test
    void testAgentManagerIntegration() {
        assertTrue(agentManager.getAgentCount() > 0);
        assertTrue(agentManager.isAgentRegistered("file-processor"));
        assertTrue(agentManager.isAgentRegistered("search-agent"));
        assertTrue(agentManager.isAgentRegistered("analysis-agent"));
        assertTrue(agentManager.isAgentRegistered("coordinator"));
    }

    @Test
    void testMessageBusIntegration() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        String[] receivedContent = new String[1];

        messageBus.subscribe("test-topic", "test-agent", message -> {
            receivedContent[0] = message.getContent();
            latch.countDown();
        });

        Message testMessage = new Message.Builder()
            .from("sender")
            .to("receiver")
            .topic("test-topic")
            .content("Integration test message")
            .build();

        messageBus.publish("test-topic", testMessage);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("Integration test message", receivedContent[0]);
    }

    @Test
    void testChainPatternIntegration() {
        if (agentManager.getAgentCount() < 2) {
            return; // Skip if not enough agents registered
        }

        List<String> agentIds = agentManager.getAgentNames().stream()
            .limit(2)
            .toList();

        var result = taskCoordinator.execute(
            "Test integration task",
            CollaborationPattern.CHAIN,
            agentIds
        );

        assertNotNull(result);
        assertNotNull(result.getAgentOutputs());
    }

    @Test
    void testParallelPatternIntegration() {
        if (agentManager.getAgentCount() < 2) {
            return; // Skip if not enough agents registered
        }

        List<String> agentIds = agentManager.getAgentNames().stream()
            .limit(2)
            .toList();

        var result = taskCoordinator.execute(
            "Test parallel task",
            CollaborationPattern.PARALLEL,
            agentIds
        );

        assertNotNull(result);
        assertNotNull(result.getAgentOutputs());
    }
}
```

**Step 2: Run test**

Run: `mvn test -Dtest=MultiAgentIntegrationTest`

Expected: PASS (may need adjustments based on actual agent implementations)

**Step 3: Commit**

```bash
git add src/test/java/com/ron/ronaiagent/integration/MultiAgentIntegrationTest.java
git commit -m "test(integration): add multi-agent integration tests"
```

---

### Task 5.3: Update Application Documentation

**Files:**
- Modify: `CLAUDE.md`

**Step 1: Update documentation**

Add to CLAUDE.md after the existing content:

```markdown
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
```

**Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: add multi-agent system usage documentation"
```

---

### Task 5.4: Add Configuration Properties

**Files:**
- Modify: `src/main/resources/application-local.yml`

**Step 1: Add configuration**

Add to application-local.yml:

```yaml
# Multi-Agent Configuration
agent:
  file:
    base-directory: ${FILE_BASE_DIR:/tmp/ron-agent-files}
  coordination:
    default-timeout-seconds: 300
    max-parallel-agents: 10
  messaging:
    enable-message-history: true
    message-history-limit: 100
```

**Step 2: Commit**

```bash
git add src/main/resources/application-local.yml
git commit -m "config: add multi-agent configuration properties"
```

---

## Summary

This implementation plan extends ron-ai-agent from a single-agent system to a full multi-agent architecture with:

✅ **Phase 1: Communication Layer**
- Message data model with builder pattern
- MessageBus interface and in-memory implementation
- Pub/sub and direct messaging support

✅ **Phase 2: Agent Management**
- AgentManager for lifecycle management
- Thread-safe agent registry
- Collaboration pattern definitions

✅ **Phase 3: Specialized Agents**
- FileProcessingAgent for file operations
- SearchAgent for web search
- AnalysisAgent for data analysis
- CoordinatorAgent for task orchestration

✅ **Phase 4: Coordination Patterns**
- Chain: Sequential agent execution
- Parallel: Concurrent agent execution
- Master-Worker: Coordinator with workers

✅ **Phase 5: Integration**
- Spring auto-configuration
- Integration tests
- Documentation updates

### Next Steps After Implementation

1. **Performance Testing**: Load test with multiple concurrent agents
2. **Persistence**: Add database-backed message queue for durability
3. **Monitoring**: Add metrics for agent performance and communication
4. **Security**: Implement agent authentication and authorization
5. **Advanced Patterns**: Add Hierarchy and RoundRobin patterns

### Backward Compatibility

The implementation preserves full backward compatibility:
- Existing RonManus agent continues to work
- Multi-agent features are opt-in
- No breaking changes to existing APIs
