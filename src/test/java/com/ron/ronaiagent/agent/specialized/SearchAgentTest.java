package com.ron.ronaiagent.agent.specialized;

import com.ron.ronaiagent.agent.AgentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for SearchAgent
 *
 * Tests cover:
 * - Agent creation and initialization
 * - Parameter validation
 * - State management
 * - Configuration properties
 */
@DisplayName("SearchAgent Tests")
class SearchAgentTest {

    @Test
    @DisplayName("Should create agent with valid parameters")
    void testSearchAgentCreation() {
        SearchAgent agent = new SearchAgent(
            "search-agent",
            null
        );

        assertEquals("search-agent", agent.getName());
        assertEquals(AgentState.IDLE, agent.getAgentState());
        assertEquals(SearchAgent.DEFAULT_MAX_STEPS, agent.getMaxSteps());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when agentId is null")
    void testNullAgentIdThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SearchAgent(null, null)
        );

        assertTrue(exception.getMessage().contains("agentId"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when agentId is empty")
    void testEmptyAgentIdThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SearchAgent("", null)
        );

        assertTrue(exception.getMessage().contains("agentId"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when agentId is blank")
    void testBlankAgentIdThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SearchAgent("   ", null)
        );

        assertTrue(exception.getMessage().contains("agentId"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should accept null chatModel for testing")
    void testNullChatModelAccepted() {
        // This should not throw an exception
        SearchAgent agent = new SearchAgent(
            "search-agent",
            null
        );

        assertNotNull(agent);
        assertEquals("search-agent", agent.getName());
        // chatClient may be null when chatModel is null, which is acceptable for testing
    }

    @Test
    @DisplayName("Should initialize with default max steps")
    void testDefaultMaxSteps() {
        SearchAgent agent = new SearchAgent(
            "search-agent",
            null
        );

        assertEquals(SearchAgent.DEFAULT_MAX_STEPS, agent.getMaxSteps());
        assertEquals(10, agent.getMaxSteps()); // Verify the constant value
    }

    @Test
    @DisplayName("Should set correct description")
    void testAgentDescription() {
        SearchAgent agent = new SearchAgent(
            "search-agent",
            null
        );

        String description = agent.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("search") || description.contains("Search"));
        assertTrue(description.contains("content retrieval") || description.contains("information"));
    }

    @Test
    @DisplayName("Should have correct system prompt")
    void testSystemPrompt() {
        SearchAgent agent = new SearchAgent(
            "search-agent",
            null
        );

        String systemPrompt = agent.getSystemPrompt();
        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("Search Agent") || systemPrompt.contains("search"));
        assertTrue(systemPrompt.contains("citations") || systemPrompt.contains("sources"));
    }

    @Test
    @DisplayName("Should have correct next step prompt")
    void testNextStepPrompt() {
        SearchAgent agent = new SearchAgent(
            "search-agent",
            null
        );

        String nextStepPrompt = agent.getNextStepPrompt();
        assertNotNull(nextStepPrompt);
        assertTrue(nextStepPrompt.contains("search") || nextStepPrompt.contains("gather"));
        assertTrue(nextStepPrompt.contains("verify") || nextStepPrompt.contains("sources"));
    }

    @Test
    @DisplayName("Should initialize in IDLE state")
    void testInitialState() {
        SearchAgent agent = new SearchAgent(
            "search-agent",
            null
        );

        assertEquals(AgentState.IDLE, agent.getAgentState());
        assertEquals(0, agent.getCurrentStep());
    }

    @Test
    @DisplayName("Should allow different agent IDs")
    void testDifferentAgentIds() {
        SearchAgent agent1 = new SearchAgent(
            "web-searcher",
            null
        );

        SearchAgent agent2 = new SearchAgent(
            "content-finder",
            null
        );

        assertEquals("web-searcher", agent1.getName());
        assertEquals("content-finder", agent2.getName());
    }
}
