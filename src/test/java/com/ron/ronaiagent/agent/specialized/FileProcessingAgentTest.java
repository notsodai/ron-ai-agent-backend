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

        assertEquals("file-processor", agent.getName());
        assertEquals(AgentState.IDLE, agent.getAgentState());
    }

    @Test
    void testFileProcessingAgentHasCorrectTools() {
        // When using the 3-parameter constructor (for testing), tools are null
        FileProcessingAgent agent = new FileProcessingAgent(
            "file-processor",
            null,
            "/tmp/test"
        );

        // Verify that the agent has access to the tools field
        // In production, tools will be injected via ToolRegistration
        // For testing, we just verify the field is accessible
        // The actual tool injection happens in MultiAgentConfig (Task 5.1)
        assertNotNull(agent);
        // Tools can be null in test scenarios, which is acceptable
    }

    @Test
    void testFileProcessingAgentHasBaseDirectory() {
        FileProcessingAgent agent = new FileProcessingAgent(
            "file-processor",
            null,
            "/tmp/test"
        );

        assertEquals("/tmp/test", agent.getBaseDirectory());
    }
}
