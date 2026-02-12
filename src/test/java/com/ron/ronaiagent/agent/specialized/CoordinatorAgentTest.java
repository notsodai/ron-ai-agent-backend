package com.ron.ronaiagent.agent.specialized;

import com.ron.ronaiagent.agent.AgentState;
import com.ron.ronaiagent.agent.BaseAgent;
import com.ron.ronaiagent.agent.coordinator.AgentManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for CoordinatorAgent
 *
 * Tests cover:
 * - Agent creation and initialization
 * - Parameter validation
 * - State management
 * - AgentManager integration
 * - Configuration properties
 */
@DisplayName("CoordinatorAgent Tests")
class CoordinatorAgentTest {

    @Test
    @DisplayName("Should create agent with valid parameters")
    void testCoordinatorAgentCreation() {
        AgentManager mockManager = new MockAgentManager();

        CoordinatorAgent agent = new CoordinatorAgent(
            "coordinator",
            null,
            mockManager
        );

        assertEquals("coordinator", agent.getName());
        assertEquals(AgentState.IDLE, agent.getAgentState());
        assertEquals(CoordinatorAgent.DEFAULT_MAX_STEPS, agent.getMaxSteps());
    }

    @Test
    @DisplayName("Should have AgentManager reference")
    void testCoordinatorAgentHasAgentManager() {
        AgentManager mockManager = new MockAgentManager();

        CoordinatorAgent agent = new CoordinatorAgent(
            "coordinator",
            null,
            mockManager
        );

        assertNotNull(agent.getAgentManager());
        assertEquals(mockManager, agent.getAgentManager());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when agentId is null")
    void testNullAgentIdThrowsException() {
        AgentManager mockManager = new MockAgentManager();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new CoordinatorAgent(null, null, mockManager)
        );

        assertTrue(exception.getMessage().contains("agentId"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when agentId is empty")
    void testEmptyAgentIdThrowsException() {
        AgentManager mockManager = new MockAgentManager();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new CoordinatorAgent("", null, mockManager)
        );

        assertTrue(exception.getMessage().contains("agentId"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when agentId is blank")
    void testBlankAgentIdThrowsException() {
        AgentManager mockManager = new MockAgentManager();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new CoordinatorAgent("   ", null, mockManager)
        );

        assertTrue(exception.getMessage().contains("agentId"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should throw NullPointerException when agentManager is null")
    void testNullAgentManagerThrowsException() {
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> new CoordinatorAgent("coordinator", null, null)
        );

        assertTrue(exception.getMessage().contains("agentManager"));
    }

    @Test
    @DisplayName("Should accept null chatModel for testing")
    void testNullChatModelAccepted() {
        AgentManager mockManager = new MockAgentManager();

        // This should not throw an exception
        CoordinatorAgent agent = new CoordinatorAgent(
            "coordinator",
            null,
            mockManager
        );

        assertNotNull(agent);
        assertEquals("coordinator", agent.getName());
        // chatClient may be null when chatModel is null, which is acceptable for testing
    }

    @Test
    @DisplayName("Should initialize with default max steps")
    void testDefaultMaxSteps() {
        AgentManager mockManager = new MockAgentManager();

        CoordinatorAgent agent = new CoordinatorAgent(
            "coordinator",
            null,
            mockManager
        );

        assertEquals(CoordinatorAgent.DEFAULT_MAX_STEPS, agent.getMaxSteps());
        assertEquals(10, agent.getMaxSteps()); // Verify the constant value
    }

    @Test
    @DisplayName("Should set correct description")
    void testAgentDescription() {
        AgentManager mockManager = new MockAgentManager();

        CoordinatorAgent agent = new CoordinatorAgent(
            "coordinator",
            null,
            mockManager
        );

        String description = agent.getDescription();
        assertNotNull(description);
        assertTrue(description.toLowerCase().contains("coordinator"));
        assertTrue(description.contains("task distribution") || description.contains("result aggregation"));
    }

    @Test
    @DisplayName("Should have correct system prompt")
    void testSystemPrompt() {
        AgentManager mockManager = new MockAgentManager();

        CoordinatorAgent agent = new CoordinatorAgent(
            "coordinator",
            null,
            mockManager
        );

        String systemPrompt = agent.getSystemPrompt();
        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("Coordinator Agent") || systemPrompt.contains("coordinator"));
        assertTrue(systemPrompt.contains("task distribution") || systemPrompt.contains("delegating"));
        assertTrue(systemPrompt.contains("Master-Worker") || systemPrompt.contains("Chain") || systemPrompt.contains("Parallel"));
    }

    @Test
    @DisplayName("Should have correct next step prompt")
    void testNextStepPrompt() {
        AgentManager mockManager = new MockAgentManager();

        CoordinatorAgent agent = new CoordinatorAgent(
            "coordinator",
            null,
            mockManager
        );

        String nextStepPrompt = agent.getNextStepPrompt();
        assertNotNull(nextStepPrompt);
        assertTrue(nextStepPrompt.toLowerCase().contains("coordinat"));
        assertTrue(nextStepPrompt.contains("progress") || nextStepPrompt.contains("aggregate"));
    }

    @Test
    @DisplayName("Should initialize in IDLE state")
    void testInitialState() {
        AgentManager mockManager = new MockAgentManager();

        CoordinatorAgent agent = new CoordinatorAgent(
            "coordinator",
            null,
            mockManager
        );

        assertEquals(AgentState.IDLE, agent.getAgentState());
        assertEquals(0, agent.getCurrentStep());
    }

    @Test
    @DisplayName("Should allow different agent IDs")
    void testDifferentAgentIds() {
        AgentManager mockManager = new MockAgentManager();

        CoordinatorAgent agent1 = new CoordinatorAgent(
            "task-coordinator",
            null,
            mockManager
        );

        CoordinatorAgent agent2 = new CoordinatorAgent(
            "workflow-orchestrator",
            null,
            mockManager
        );

        assertEquals("task-coordinator", agent1.getName());
        assertEquals("workflow-orchestrator", agent2.getName());
    }

    @Test
    @DisplayName("Should allow different agent managers")
    void testDifferentAgentManagers() {
        AgentManager mockManager1 = new MockAgentManager();
        AgentManager mockManager2 = new MockAgentManager();

        CoordinatorAgent agent1 = new CoordinatorAgent(
            "coordinator-1",
            null,
            mockManager1
        );

        CoordinatorAgent agent2 = new CoordinatorAgent(
            "coordinator-2",
            null,
            mockManager2
        );

        assertEquals(mockManager1, agent1.getAgentManager());
        assertEquals(mockManager2, agent2.getAgentManager());
    }

    /**
     * Simple mock implementation of AgentManager for testing
     */
    private static class MockAgentManager implements AgentManager {
        @Override
        public void registerAgent(String name, BaseAgent agent) {
            // No-op for testing
        }

        @Override
        public void unregisterAgent(String name) {
            // No-op for testing
        }

        @Override
        public Optional<BaseAgent> getAgent(String name) {
            return Optional.empty();
        }

        @Override
        public List<String> getAgentNames() {
            return List.of();
        }

        @Override
        public List<BaseAgent> getAllAgents() {
            return List.of();
        }

        @Override
        public boolean isAgentRegistered(String name) {
            return false;
        }

        @Override
        public int getAgentCount() {
            return 0;
        }
    }
}
