package com.ron.ronaiagent.agent.specialized;

import com.ron.ronaiagent.agent.AgentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for FileProcessingAgent
 *
 * Tests cover:
 * - Agent creation and initialization
 * - Parameter validation
 * - State management
 * - Configuration properties
 */
@DisplayName("FileProcessingAgent Tests")
class FileProcessingAgentTest {

    @Test
    @DisplayName("Should create agent with valid parameters")
    void testFileProcessingAgentCreation() {
        FileProcessingAgent agent = new FileProcessingAgent(
            "file-processor",
            null,
            "/tmp/test"
        );

        assertEquals("file-processor", agent.getName());
        assertEquals(AgentState.IDLE, agent.getAgentState());
        assertEquals("/tmp/test", agent.getBaseDirectory());
        assertEquals(FileProcessingAgent.DEFAULT_MAX_STEPS, agent.getMaxSteps());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when agentId is null")
    void testNullAgentIdThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new FileProcessingAgent(null, null, "/tmp/test")
        );

        assertTrue(exception.getMessage().contains("agentId"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when agentId is empty")
    void testEmptyAgentIdThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new FileProcessingAgent("", null, "/tmp/test")
        );

        assertTrue(exception.getMessage().contains("agentId"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when agentId is blank")
    void testBlankAgentIdThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new FileProcessingAgent("   ", null, "/tmp/test")
        );

        assertTrue(exception.getMessage().contains("agentId"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when baseDirectory is null")
    void testNullBaseDirectoryThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new FileProcessingAgent("file-processor", null, null)
        );

        assertTrue(exception.getMessage().contains("baseDirectory"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when baseDirectory is empty")
    void testEmptyBaseDirectoryThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new FileProcessingAgent("file-processor", null, "")
        );

        assertTrue(exception.getMessage().contains("baseDirectory"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when baseDirectory is blank")
    void testBlankBaseDirectoryThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new FileProcessingAgent("file-processor", null, "   ")
        );

        assertTrue(exception.getMessage().contains("baseDirectory"));
        assertTrue(exception.getMessage().contains("not be null or blank"));
    }

    @Test
    @DisplayName("Should accept null chatModel for testing")
    void testNullChatModelAccepted() {
        // This should not throw an exception
        FileProcessingAgent agent = new FileProcessingAgent(
            "file-processor",
            null,
            "/tmp/test"
        );

        assertNotNull(agent);
        assertEquals("file-processor", agent.getName());
        // chatClient may be null when chatModel is null, which is acceptable for testing
    }

    @Test
    @DisplayName("Should initialize with default max steps")
    void testDefaultMaxSteps() {
        FileProcessingAgent agent = new FileProcessingAgent(
            "file-processor",
            null,
            "/tmp/test"
        );

        assertEquals(FileProcessingAgent.DEFAULT_MAX_STEPS, agent.getMaxSteps());
        assertEquals(10, agent.getMaxSteps()); // Verify the constant value
    }

    @Test
    @DisplayName("Should set correct description")
    void testAgentDescription() {
        FileProcessingAgent agent = new FileProcessingAgent(
            "file-processor",
            null,
            "/tmp/test"
        );

        String description = agent.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("file operations"));
        assertTrue(description.contains("reading") || description.contains("writing"));
    }

    @Test
    @DisplayName("Should have correct system prompt")
    void testSystemPrompt() {
        FileProcessingAgent agent = new FileProcessingAgent(
            "file-processor",
            null,
            "/tmp/test"
        );

        String systemPrompt = agent.getSystemPrompt();
        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("File Processing Agent"));
        assertTrue(systemPrompt.contains("file operations"));
    }

    @Test
    @DisplayName("Should have correct next step prompt")
    void testNextStepPrompt() {
        FileProcessingAgent agent = new FileProcessingAgent(
            "file-processor",
            null,
            "/tmp/test"
        );

        String nextStepPrompt = agent.getNextStepPrompt();
        assertNotNull(nextStepPrompt);
        assertTrue(nextStepPrompt.contains("file operation request"));
        assertTrue(nextStepPrompt.contains("terminate tool"));
    }

    @Test
    @DisplayName("Should initialize in IDLE state")
    void testInitialState() {
        FileProcessingAgent agent = new FileProcessingAgent(
            "file-processor",
            null,
            "/tmp/test"
        );

        assertEquals(AgentState.IDLE, agent.getAgentState());
        assertEquals(0, agent.getCurrentStep());
    }

    @Test
    @DisplayName("Should allow different base directory paths")
    void testDifferentBaseDirectories() {
        FileProcessingAgent agent1 = new FileProcessingAgent(
            "agent1",
            null,
            "/tmp/dir1"
        );

        FileProcessingAgent agent2 = new FileProcessingAgent(
            "agent2",
            null,
            "/tmp/dir2"
        );

        assertEquals("/tmp/dir1", agent1.getBaseDirectory());
        assertEquals("/tmp/dir2", agent2.getBaseDirectory());
    }

    @Test
    @DisplayName("Should handle Windows-style paths")
    void testWindowsPaths() {
        FileProcessingAgent agent = new FileProcessingAgent(
            "file-processor",
            null,
            "C:\\Users\\test\\files"
        );

        assertEquals("C:\\Users\\test\\files", agent.getBaseDirectory());
    }

    @Test
    @DisplayName("Should handle Unix-style paths")
    void testUnixPaths() {
        FileProcessingAgent agent = new FileProcessingAgent(
            "file-processor",
            null,
            "/home/user/files"
        );

        assertEquals("/home/user/files", agent.getBaseDirectory());
    }
}
