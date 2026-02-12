package com.ron.ronaiagent.integration;

import com.ron.ronaiagent.agent.AgentState;
import com.ron.ronaiagent.agent.BaseAgent;
import com.ron.ronaiagent.agent.coordinator.AgentManager;
import com.ron.ronaiagent.agent.coordinator.AgentManagerImpl;
import com.ron.ronaiagent.agent.coordinator.CollaborationPattern;
import com.ron.ronaiagent.agent.coordinator.TaskCoordinator;
import com.ron.ronaiagent.agent.coordinator.TaskCoordinatorImpl;
import com.ron.ronaiagent.agent.communication.InMemoryMessageBus;
import com.ron.ronaiagent.agent.communication.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock agent for testing purposes
 */
class MockAgent extends BaseAgent {
    public MockAgent(String name) {
        setName(name);
        setDescription("Mock agent for testing");
    }

    @Override
    public String step() {
        return "Mock step execution - " + getName();
    }

    @Override
    public void cleanup() {
        // Mock cleanup implementation
    }
}

class MultiAgentIntegrationTest {

    private AgentManager agentManager;
    private TaskCoordinator taskCoordinator;
    private InMemoryMessageBus messageBus;

    @BeforeEach
    void setUp() {
        // Initialize message bus
        messageBus = new InMemoryMessageBus();

        // Initialize agent manager with mock agents
        agentManager = new AgentManagerImpl();

        // Register mock agents
        agentManager.registerAgent("file-processor", new MockAgent("file-processor"));
        agentManager.registerAgent("search-agent", new MockAgent("search-agent"));
        agentManager.registerAgent("analysis-agent", new MockAgent("analysis-agent"));
        agentManager.registerAgent("coordinator", new MockAgent("coordinator"));

        // Initialize task coordinator
        taskCoordinator = new TaskCoordinatorImpl(agentManager);
    }

    @Test
    void testAgentManagerIntegration() {
        assertEquals(4, agentManager.getAgentCount());
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