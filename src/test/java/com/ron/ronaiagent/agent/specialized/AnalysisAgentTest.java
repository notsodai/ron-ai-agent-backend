package com.ron.ronaiagent.agent.specialized;

import com.ron.ronaiagent.agent.AgentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for AnalysisAgent
 *
 * Tests cover:
 * - Agent creation and initialization
 * - Parameter validation
 * - State management
 * - Configuration properties
 */
@DisplayName("AnalysisAgent Tests")
class AnalysisAgentTest {

    @Test
    @DisplayName("Should create agent with valid parameters")
    void testAnalysisAgentCreation() {
        AnalysisAgent agent = new AnalysisAgent(
            "analysis-agent",
            null
        );

        assertEquals("analysis-agent", agent.getName());
        assertEquals(AgentState.IDLE, agent.getAgentState());
        assertEquals(AnalysisAgent.DEFAULT_MAX_STEPS, agent.getMaxSteps());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when agentId is null")
    void testNullAgentIdThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new AnalysisAgent(null, null)
        );

        assertTrue(exception.getMessage().contains("agentId"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when agentId is empty")
    void testEmptyAgentIdThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new AnalysisAgent("", null)
        );

        assertTrue(exception.getMessage().contains("agentId"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when agentId is blank")
    void testBlankAgentIdThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new AnalysisAgent("   ", null)
        );

        assertTrue(exception.getMessage().contains("agentId"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should accept null chatModel for testing")
    void testNullChatModelAccepted() {
        // This should not throw an exception
        AnalysisAgent agent = new AnalysisAgent(
            "analysis-agent",
            null
        );

        assertNotNull(agent);
        assertEquals("analysis-agent", agent.getName());
        // chatClient may be null when chatModel is null, which is acceptable for testing
    }

    @Test
    @DisplayName("Should initialize with default max steps")
    void testDefaultMaxSteps() {
        AnalysisAgent agent = new AnalysisAgent(
            "analysis-agent",
            null
        );

        assertEquals(AnalysisAgent.DEFAULT_MAX_STEPS, agent.getMaxSteps());
        assertEquals(10, agent.getMaxSteps()); // Verify the constant value
    }

    @Test
    @DisplayName("Should set correct description")
    void testAgentDescription() {
        AnalysisAgent agent = new AnalysisAgent(
            "analysis-agent",
            null
        );

        String description = agent.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("analysis") || description.contains("Analysis"));
        assertTrue(description.contains("insights") || description.contains("data"));
    }

    @Test
    @DisplayName("Should have correct system prompt")
    void testSystemPrompt() {
        AnalysisAgent agent = new AnalysisAgent(
            "analysis-agent",
            null
        );

        String systemPrompt = agent.getSystemPrompt();
        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("Analysis Agent") || systemPrompt.contains("analysis"));
        assertTrue(systemPrompt.contains("data analysis") || systemPrompt.contains("insights"));
        assertTrue(systemPrompt.contains("confidence") || systemPrompt.contains("statistical"));
    }

    @Test
    @DisplayName("Should have correct next step prompt")
    void testNextStepPrompt() {
        AnalysisAgent agent = new AnalysisAgent(
            "analysis-agent",
            null
        );

        String nextStepPrompt = agent.getNextStepPrompt();
        assertNotNull(nextStepPrompt);
        assertTrue(nextStepPrompt.contains("analysis") || nextStepPrompt.contains("insights"));
        assertTrue(nextStepPrompt.contains("continue") || nextStepPrompt.contains("verify"));
    }

    @Test
    @DisplayName("Should initialize in IDLE state")
    void testInitialState() {
        AnalysisAgent agent = new AnalysisAgent(
            "analysis-agent",
            null
        );

        assertEquals(AgentState.IDLE, agent.getAgentState());
        assertEquals(0, agent.getCurrentStep());
    }

    @Test
    @DisplayName("Should allow different agent IDs")
    void testDifferentAgentIds() {
        AnalysisAgent agent1 = new AnalysisAgent(
            "data-analyst",
            null
        );

        AnalysisAgent agent2 = new AnalysisAgent(
            "insight-generator",
            null
        );

        assertEquals("data-analyst", agent1.getName());
        assertEquals("insight-generator", agent2.getName());
    }
}
